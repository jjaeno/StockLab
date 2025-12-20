package com.StockLab.backend.Quote;

import com.StockLab.backend.Quote.QuoteDto.*;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 다중 종목 시세 조회 전용 서비스
 * 
 * 핵심 기능:
 * - 응답 누락 0% 보장 (실패도 status=FAILED로 포함)
 * - 동시성 제한 (flatMap concurrency=5)
 * - timeout(3초) + retry(1회, 429만)
 * - 부분 실패 격리 (onErrorResume)
 * - 캐시 적용 (60초)
 * - last-known-good 폴백
 * - 요청 순서 유지
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiQuoteService {
    
    private final KisQuoteService domesticQuoteService;
    private final KisOverseasQuoteService overseasQuoteService;
    private final LastKnownGoodCache lastKnownGoodCache;
    
    private static final int CONCURRENCY_LIMIT = 3;
    private static final Duration TIMEOUT_PER_QUOTE = Duration.ofSeconds(5);
    private static final int MAX_RETRY = 1;
    
    /**
     * 다중 종목 시세 조회 (메인 메서드)
     * 
     * @param symbols 종목 리스트 (국내/해외 혼합 가능)
     * @return 항상 N개의 QuoteResult (성공/실패 모두 포함)
     */
    public BatchQuoteResponse getBatchQuotes(List<String> symbols) {
        long startTime = System.currentTimeMillis();
        
        log.info("=== 다중 시세 조회 시작: {} 건 ===", symbols.size());
        
        // 1. 요청 순서 보존을 위한 인덱스 맵 생성
        Map<String, Integer> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < symbols.size(); i++) {
            orderMap.put(symbols.get(i), i);
        }
        
        // 2. Flux 스트림으로 병렬 처리
        Map<String, QuoteResult> resultMap = Flux.fromIterable(symbols)
                .flatMap(symbol -> fetchQuoteWithFallback(symbol), CONCURRENCY_LIMIT)
                .collectMap(QuoteResult::getSymbol)
                .publishOn(Schedulers.boundedElastic())
                .blockOptional()
                .orElseGet(HashMap::new);
        
        // 3. 요청 순서대로 결과 재구성 (누락 방지)
        List<QuoteResult> orderedResults = symbols.stream()
                .map(symbol -> resultMap.getOrDefault(symbol, 
                    createMissingResult(symbol, "UNKNOWN_ERROR")))
                .collect(Collectors.toList());
        
        // 4. 통계 계산
        int successCount = (int) orderedResults.stream()
                .filter(r -> r.getStatus() == ResultStatus.SUCCESS).count();
        int cachedCount = (int) orderedResults.stream()
                .filter(QuoteResult::isCached).count();
        int failedCount = orderedResults.size() - successCount;
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        log.info("=== 다중 시세 조회 완료: {}ms ===", elapsed);
        log.info("   총 요청: {}, 성공: {}, 실패: {}, 캐시: {}", 
                symbols.size(), successCount, failedCount, cachedCount);
        
        // 5. 검증: 응답 개수 확인
        if (orderedResults.size() != symbols.size()) {
            log.error("❌ 응답 누락 발생! 요청: {}, 응답: {}", 
                    symbols.size(), orderedResults.size());
        }
        
        return BatchQuoteResponse.builder()
                .results(orderedResults)
                .totalRequested(symbols.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .cachedCount(cachedCount)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 단일 종목 조회 + 폴백 처리
     * 
     * 흐름:
     * 1. timeout(3초) 적용
     * 2. retry(1회, 429만)
     * 3. 실패 시 onErrorResume으로 QuoteResult.failed 반환 (누락 안됨!)
     * 4. last-known-good 조회
     */
    private Mono<QuoteResult> fetchQuoteWithFallback(String symbol) {
        return fetchQuoteInternal(symbol)
                .timeout(TIMEOUT_PER_QUOTE)
                .retryWhen(Retry.fixedDelay(MAX_RETRY, Duration.ofMillis(500))
                        .filter(this::isRetryable))
                .doOnSuccess(result -> {
                    // 성공 시 last-known-good 저장
                    if (result.getStatus() == ResultStatus.SUCCESS && 
                        result.getData() != null) {
                        lastKnownGoodCache.save(symbol, 
                            result.getData().getCurrentPrice());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("종목 {} 조회 실패: {} - last-known-good 조회 중...", 
                            symbol, e.getClass().getSimpleName());
                    return Mono.just(createFailedResultWithFallback(symbol, e));
                });
    }
    
    /**
     * 내부 시세 조회 (국내/해외 자동 판단)
     */
    private Mono<QuoteResult> fetchQuoteInternal(String symbol) {
        boolean isDomestic = symbol.matches("\\d{6}");
        
        if (isDomestic) {
            return domesticQuoteService.getQuoteAsync(symbol)
                    .map(quote -> QuoteResult.builder()
                            .symbol(symbol)
                            .status(ResultStatus.SUCCESS)
                            .data(quote)
                            .source("KIS")
                            .cached(false)
                            .fetchedAt(System.currentTimeMillis())
                            .build());
        } else {
            return overseasQuoteService.getOverseasQuoteAsync(symbol, "NASDAQ")
                    .map(quote -> QuoteResult.builder()
                            .symbol(symbol)
                            .status(ResultStatus.SUCCESS)
                            .data(quote)
                            .source("KIS")
                            .cached(false)
                            .fetchedAt(System.currentTimeMillis())
                            .build());
        }
    }
    
    /**
     * 실패 결과 생성 + last-known-good 폴백
     */
    private QuoteResult createFailedResultWithFallback(String symbol, Throwable e) {
        String reason = determineFailureReason(e);
        BigDecimal lastKnownPrice = lastKnownGoodCache.get(symbol);
        
        QuoteResult.QuoteResultBuilder builder = QuoteResult.builder()
                .symbol(symbol)
                .status(ResultStatus.FAILED)
                .reason(reason)
                .source("NONE")
                .cached(false)
                .fetchedAt(System.currentTimeMillis());
        
        if (lastKnownPrice != null) {
            builder.lastKnownPrice(lastKnownPrice)
                   .source("LAST_KNOWN");
            log.debug("   → last-known-good 사용: {} = {}", symbol, lastKnownPrice);
        }
        
        return builder.build();
    }
    
    /**
     * 완전 누락된 경우 (절대 발생하면 안되지만 방어 코드)
     */
    private QuoteResult createMissingResult(String symbol, String reason) {
        log.error("❌ 종목 {} 결과 누락 발생! (방어 코드 실행)", symbol);
        return QuoteResult.builder()
                .symbol(symbol)
                .status(ResultStatus.FAILED)
                .reason(reason)
                .source("NONE")
                .cached(false)
                .fetchedAt(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 재시도 가능한 에러 판단 (429 Rate Limit만)
     */
    private boolean isRetryable(Throwable e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("429");
    }
    
    /**
     * 실패 사유 판단
     */
    private String determineFailureReason(Throwable e) {
        String className = e.getClass().getSimpleName();
        String message = e.getMessage();
        
        if (className.contains("Timeout")) {
            return "TIMEOUT";
        } else if (message != null && message.contains("429")) {
            return "RATE_LIMIT";
        } else if (className.contains("Parse")) {
            return "PARSE_ERROR";
        } else {
            return "API_ERROR";
        }
    }
}