package com.StockLab.backend.Quote;

import com.StockLab.backend.common.BusinessException;
import com.StockLab.backend.common.ErrorCode;
import com.StockLab.backend.config.KisTokenManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class KisQuoteService {

    private final AtomicLong lastDomesticCallTime = new AtomicLong(0);
    private static final long MIN_CALL_INTERVAL_MS = 200; // 국내: 200ms

    private final WebClient webClient;
    private final KisTokenManager tokenManager;
    private final String appKey;
    private final String appSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimiter domesticRateLimiter;

    // 60초 TTL 캐시
    private final Cache<String, QuoteDto.QuoteResponse> quoteCache;

    // TR_ID 상수 정의
    private static final String TR_ID_PRICE = "FHKST01010100";        // 국내주식 현재가 시세
    private static final String TR_ID_DAILY_CHART = "FHKST03010100";  // 국내주식 기간별 시세

    // 기타 TR_ID는 필요 시 추가
    private synchronized void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastDomesticCallTime.get();

        if (elapsed < MIN_CALL_INTERVAL_MS) {
            try {
                long sleepTime = MIN_CALL_INTERVAL_MS - elapsed;
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastDomesticCallTime.set(System.currentTimeMillis());
    }

    // 생성자
    public KisQuoteService(WebClient.Builder webClientBuilder,
                           @Value("${api.kis.base-url}") String baseUrl,
                           @Value("${api.kis.appkey}") String appKey,
                           @Value("${api.kis.appsecret}") String appSecret,
                           KisTokenManager tokenManager) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.tokenManager = tokenManager;
        this.domesticRateLimiter = RateLimiter.of("kisDomestic",
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofMillis(MIN_CALL_INTERVAL_MS))
                        .limitForPeriod(1)
                        .timeoutDuration(Duration.ofSeconds(1))
                        .build());
        this.quoteCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

    /**
     * 단일 종목 현재가 조회 (동기: 기존 사용)
     */
    public QuoteDto.QuoteResponse getQuote(String stockCode) {
        return getQuoteAsync(stockCode)
                .timeout(Duration.ofSeconds(5))
                .publishOn(Schedulers.boundedElastic())
                .blockOptional()
                .orElseThrow(() -> new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "시세 조회 실패: " + stockCode));
    }

    /**
     * 비동기 현재가 조회 (RateLimiter + onErrorResume)
     * 기존 동기 로직(getQuoteBlocking)을 재사용하며, blocking 호출은 boundedElastic에서 실행.
     */
    public Mono<QuoteDto.QuoteResponse> getQuoteAsync(String stockCode) {
        return Mono.defer(() -> Mono.fromCallable(() -> getQuoteBlocking(stockCode)))
                .publishOn(Schedulers.boundedElastic())
                .transformDeferred(RateLimiterOperator.of(domesticRateLimiter))
                .onErrorResume(e -> {
                    log.warn("국내 시세 조회 실패 (skip): {} - {}", stockCode, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 동기 현재가 조회 본체 (기존 로직 유지)
     */
    private QuoteDto.QuoteResponse getQuoteBlocking(String stockCode) {
        // 캐시 확인
        QuoteDto.QuoteResponse cached = quoteCache.getIfPresent(stockCode);
        if (cached != null) {
            log.debug("캐시 히트: {}", stockCode);
            return cached;
        }
        enforceRateLimit(); // Rate Limit 준수

        log.debug("KIS API 호출 - 현재가 조회: {}", stockCode);

        try {
            // Access Token 가져오기 (만료 시 자동 갱신)
            String accessToken = tokenManager.getAccessToken();

            // API 호출
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // 주식시장 구분 (J: 주식)
                            .queryParam("FID_INPUT_ISCD", stockCode)    // 종목코드
                            .build())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret) // appsecret도 헤더에 포함
                    .header("tr_id", TR_ID_PRICE)
                    .header("custtype", "P") //고객 구분(P: 개인)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5)); //5초 타임아웃

            // 응답 파싱
            QuoteDto.QuoteResponse quote = parseQuoteResponse(stockCode, response);

            // 유효한 가격인 경우에만 캐시에 저장
            if (quote.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                quoteCache.put(stockCode, quote);
                log.info("국내주식 캐시 저장: {} - {}원", stockCode, quote.getCurrentPrice());
            } else {
                log.warn("가격 0인 응답: {}", stockCode);
            }

            return quote;

        } catch (Exception e) {
            log.error("시세 조회 실패: {} - {}", stockCode, e.getMessage());
            log.debug("KIS 응답: {}", e);
            throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                    "시세 조회 실패: " + stockCode);
        }
    }

    /**
     * 다중 종목 현재가 조회
     * KIS API는 다중 조회를 지원하지 않으므로 순차 호출
     */
    public List<QuoteDto.QuoteResponse> getQuotes(List<String> stockCodes) {
        log.info("다중 종목 조회: {} 건", stockCodes.size());

        return stockCodes.stream()
                .map(this::getQuote)
                .collect(Collectors.toList());
    }

    /**
     * 일자별 캔들 데이터 조회
     */
    public QuoteDto.CandleResponse getCandles(String stockCode, String range) {
        log.debug("캔들 데이터 조회: {} - {}", stockCode, range);

        try {
            String accessToken = tokenManager.getAccessToken();
            // 기간 구분 코드 매핑
            String periodCode;
            switch (range.toUpperCase()) {
                case "1D" -> periodCode = "D";   // 1일치 → 일봉
                case "1W" -> periodCode = "D";   // 1주 → 일봉으로 최근 7일
                case "1M" -> periodCode = "D";   // 1개월 → 일봉
                case "3M" -> periodCode = "D";   // 3개월 → 일봉으로 3개월치
                case "1Y" -> periodCode = "W";   // 1년 → 주봉
                default -> periodCode = "D";
            }

            // 조회 기간 계산
            String endDate = getCurrentDate();
            String startDate = getStartDate(range);

            // API 호출
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .queryParam("FID_INPUT_DATE_1", startDate)   // 조회 시작일 (YYYYMMDD)
                            .queryParam("FID_INPUT_DATE_2", endDate)     // 조회 종료일 (YYYYMMDD)
                            .queryParam("FID_PERIOD_DIV_CODE", periodCode)      // D: 일봉
                            .queryParam("FID_ORG_ADJ_PRC", "0")          // 0: 수정주가 미반영
                            .build())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", TR_ID_DAILY_CHART)
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 응답 파싱
            return parseCandleResponse(stockCode, range, response);

        } catch (Exception e) {
            log.error("캔들 데이터 조회 실패: {} - {}", stockCode, e.getMessage());
            throw new BusinessException(ErrorCode.API_ERROR, "캔들 데이터 조회 실패");
        }
    }

    /**
     * 현재가 응답 파싱
     */
    private QuoteDto.QuoteResponse parseQuoteResponse(String stockCode, String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            JsonNode output = json.get("output");

            if (output == null) {
                throw new RuntimeException("응답 데이터가 없습니다");
            }

            BigDecimal currentPrice = new BigDecimal(output.get("stck_prpr").asText());
            BigDecimal change = new BigDecimal(output.get("prdy_vrss").asText());
            BigDecimal percentChange = new BigDecimal(output.get("prdy_ctrt").asText());

            // 전일 대비 부호 확인 (5: 하락)
            String sign = output.get("prdy_vrss_sign").asText();
            if ("5".equals(sign) || "4".equals(sign)) {
                change = change.negate();
                percentChange = percentChange.negate();
            }

            BigDecimal previousClose = currentPrice.subtract(change);

            return QuoteDto.QuoteResponse.builder()
                    .symbol(stockCode)
                    .currentPrice(currentPrice)
                    .change(change)
                    .percentChange(percentChange)
                    .high(new BigDecimal(output.get("stck_hgpr").asText()))
                    .low(new BigDecimal(output.get("stck_lwpr").asText()))
                    .open(new BigDecimal(output.get("stck_oprc").asText()))
                    .previousClose(previousClose)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .build();

        } catch (Exception e) {
            log.error("응답 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("시세 데이터 파싱 실패", e);
        }
    }

    /**
     * 캔들 데이터 응답 파싱
     */
    private QuoteDto.CandleResponse parseCandleResponse(String stockCode, String range,
                                                        String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            JsonNode output2 = json.get("output2");

            if (output2 == null || output2.size() == 0) {
                return QuoteDto.CandleResponse.builder()
                        .symbol(stockCode)
                        .range(range)
                        .timestamps(Collections.emptyList())
                        .open(Collections.emptyList())
                        .high(Collections.emptyList())
                        .low(Collections.emptyList())
                        .close(Collections.emptyList())
                        .volume(Collections.emptyList())
                        .build();
            }

            List<Long> timestamps = new ArrayList<>();
            List<Double> open = new ArrayList<>();
            List<Double> high = new ArrayList<>();
            List<Double> low = new ArrayList<>();
            List<Double> close = new ArrayList<>();
            List<Long> volume = new ArrayList<>();

            for (JsonNode candle : output2) {
                // 날짜 → Timestamp 변환 (YYYYMMDD → Unix timestamp)
                String date = candle.get("stck_bsop_date").asText();
                timestamps.add(parseDateToTimestamp(date));

                open.add(Double.parseDouble(candle.get("stck_oprc").asText()));
                high.add(Double.parseDouble(candle.get("stck_hgpr").asText()));
                low.add(Double.parseDouble(candle.get("stck_lwpr").asText()));
                close.add(Double.parseDouble(candle.get("stck_clpr").asText()));
                volume.add(Long.parseLong(candle.get("acml_vol").asText()));
            }

            return QuoteDto.CandleResponse.builder()
                    .symbol(stockCode)
                    .range(range)
                    .timestamps(timestamps)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build();

        } catch (Exception e) {
            log.error("캔들 데이터 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("캔들 데이터 파싱 실패", e);
        }
    }

    /**
     * 날짜 문자열을 Unix timestamp로 변환
     */
    private Long parseDateToTimestamp(String dateStr) {
        try {
            int year = Integer.parseInt(dateStr.substring(0, 4));
            int month = Integer.parseInt(dateStr.substring(4, 6));
            int day = Integer.parseInt(dateStr.substring(6, 8));

            return java.time.LocalDate.of(year, month, day)
                    .atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 현재 날짜 반환 (YYYYMMDD 형식)
     */
    private String getCurrentDate() {
        return java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * 조회 시작 날짜 계산
     */
    private String getStartDate(String range) {
        java.time.LocalDate startDate = switch (range) {
            case "1D" -> java.time.LocalDate.now().minusDays(1);
            case "1W" -> java.time.LocalDate.now().minusWeeks(1);
            case "1M" -> java.time.LocalDate.now().minusMonths(1);
            case "3M" -> java.time.LocalDate.now().minusMonths(3);
            case "1Y" -> java.time.LocalDate.now().minusYears(1);
            default -> java.time.LocalDate.now().minusMonths(1);
        };

        return startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
