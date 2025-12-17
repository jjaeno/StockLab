package com.StockLab.backend.Quote;

import com.StockLab.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 통합 시세 조회 Controller
 * 
 * - 국내주식 (6자리 숫자)
 * - 미국주식 (영문 심볼)
 */
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Quote", description = "통합 시세 조회 API (국내+해외)")
public class UnifiedQuoteController {
    
    private final KisQuoteService domesticQuoteService;
    private final KisOverseasQuoteService overseasQuoteService;
    
    /**
     * 단일 종목 조회 (자동 판단)
     * 
     * GET /api/v1/quotes/005930    → 삼성전자 (국내)
     * GET /api/v1/quotes/AAPL      → 애플 (미국)
     * 
     * 거래소를 명시하고 싶으면 exchange 파라미터 사용:
     * GET /api/v1/quotes/AAPL?exchange=NASDAQ
     */
    @GetMapping("/{symbol}")
    @Operation(
        summary = "단일 종목 시세 조회",
        description = "종목 코드를 자동으로 판단하여 시세를 조회합니다.\n\n"
    )
    public ApiResponse<UnifiedQuoteResponse> getQuote(
            @PathVariable @Parameter(description = "종목 코드 또는 심볼", example = "005930") 
            String symbol,
            @RequestParam(required = false) @Parameter(description = "거래소 (선택사항)", example = "NASDAQ") 
            String exchange) {
        
        log.info("시세 조회: {} {}", symbol, exchange != null ? "(" + exchange + ")" : "");
        
        QuoteDto.QuoteResponse quote;
        StockType stockType;
        
        // 거래소가 명시되었으면 해외주식으로 처리
        if (exchange != null) {
            log.debug("   → 해외주식 (명시된 거래소: {})", exchange);
            quote = overseasQuoteService.getOverseasQuote(symbol, exchange);
            stockType = StockType.OVERSEAS;
        } else {
            // 자동 판단
            stockType = detectStockType(symbol);
            
            switch (stockType) {
                case DOMESTIC:
                    log.debug("   → 국내주식");
                    quote = domesticQuoteService.getQuote(symbol);
                    break;
                    
                case OVERSEAS:
                    log.debug("   → 해외주식 (자동 판단)");
                    // 영문 심볼 → 미국 주식으로 간주
                    quote = overseasQuoteService.getUsStock(symbol);
                    break;
                    
                default:
                    throw new IllegalArgumentException("알 수 없는 종목 형식: " + symbol);
            }
        }
        
        return ApiResponse.success(UnifiedQuoteResponse.builder()
                .quote(quote)
                .stockType(stockType)
                .market(getMarketName(stockType))
                .currency(getCurrency(stockType))
                .build());
    }
    
    /**
     * 다중 종목 조회
     * 
     * GET /api/v1/quotes?symbols=005930,AAPL,TSLA,000660
     * 
     * 쉼표로 구분하여 여러 종목을 한 번에 조회
     * 국내주식과 해외주식을 섞어서 사용 가능
     */
    @GetMapping
    @Operation(summary = "다중 종목 시세 조회")
    public ApiResponse<List<UnifiedQuoteResponse>> getQuotes(
            @RequestParam String symbols) {
        
        log.info("다중 조회 시작: {}", symbols);
        
        String[] symbolArray = symbols.split(",");
        List<String> domesticSymbols = new ArrayList<>();
        List<String> overseasSymbols = new ArrayList<>();
        
        // 국내/해외 분류
        for (String symbol : symbolArray) {
            symbol = symbol.trim();
            if (symbol.isEmpty()) continue;
            
            StockType stockType = detectStockType(symbol);
            if (stockType == StockType.DOMESTIC) {
                domesticSymbols.add(symbol);
            } else if (stockType == StockType.OVERSEAS) {
                overseasSymbols.add(symbol);
            }
        }
        
        log.info("   국내: {}개, 해외: {}개", domesticSymbols.size(), overseasSymbols.size());
        
        List<UnifiedQuoteResponse> responses = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        
        // ============ 국내 종목 처리 (Rate Limit 내장) ============
        for (String symbol : domesticSymbols) {
            try {
                QuoteDto.QuoteResponse quote = domesticQuoteService.getQuote(symbol);
                responses.add(UnifiedQuoteResponse.builder()
                        .quote(quote)
                        .stockType(StockType.DOMESTIC)
                        .market(getMarketName(StockType.DOMESTIC))
                        .currency(getCurrency(StockType.DOMESTIC))
                        .build());
                successCount++;
            } catch (Exception e) {
                log.warn("국내 종목 실패: {} - {}", symbol, e.getMessage());
                failCount++;
            }
        }
        
        // ============ 해외 종목 처리 (배치 + 대기) ============
        int batchSize = 2; // VTS: 한 번에 3개씩만 처리
        for (int i = 0; i < overseasSymbols.size(); i += batchSize) {
            List<String> batch = overseasSymbols.subList(
                i, 
                Math.min(i + batchSize, overseasSymbols.size())
            );
            
            log.info("   해외 배치 {}/{} 처리 중...", (i / batchSize) + 1, 
                    (overseasSymbols.size() + batchSize - 1) / batchSize);
            
            for (String symbol : batch) {
                try {
                    QuoteDto.QuoteResponse quote = overseasQuoteService.getUsStock(symbol);
                    responses.add(UnifiedQuoteResponse.builder()
                            .quote(quote)
                            .stockType(StockType.OVERSEAS)
                            .market(getMarketName(StockType.OVERSEAS))
                            .currency(getCurrency(StockType.OVERSEAS))
                            .build());
                    successCount++;
                } catch (Exception e) {
                    log.warn("해외 종목 실패: {} - {}", symbol, e.getMessage());
                    failCount++;
                }
            }
            
            // 배치 간 대기 (마지막 제외)
            if (i + batchSize < overseasSymbols.size()) {
                try {
                    log.debug("⏱배치 간 2초 대기...");
                    Thread.sleep(2000); // VTS: 배치 간 2초 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("배치 대기 중단됨");
                    break;
                }
            }
        }
        
        log.info("✅ 다중 조회 완료: 성공 {}/{}, 실패 {}", 
                successCount, symbolArray.length, failCount);
        
        return ApiResponse.success(responses);
    }
    
    /**
     * 캔들 차트 데이터 조회 (국내 + 해외)
     * 
     * GET /api/v1/quotes/AAPL/candles?range=1M
     * GET /api/v1/quotes/005930/candles?range=3M
     * GET /api/v1/quotes/AAPL/candles?range=1W&exchange=NASDAQ
     */
    @GetMapping("/{symbol}/candles")
    @Operation(
        summary = "캔들 차트 데이터 조회",
        description = "일자별 시가, 고가, 저가, 종가 데이터를 조회합니다.\n" +
                      "국내주식 + 해외주식 지원"
    )
    public ApiResponse<QuoteDto.CandleResponse> getCandles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1M") 
            @Parameter(description = "조회 기간 (1D/1W/1M/3M/1Y)") String range,
            @RequestParam(required = false)
            @Parameter(description = "거래소 (해외주식일 경우 명시 가능)", example = "NASDAQ") String exchange) {

        log.info("캔들 조회 요청: {} (range={}, exchange={})", symbol, range, exchange);

        QuoteDto.CandleResponse candles;
        StockType stockType;

        // 거래소 명시 → 해외 캔들
        if (exchange != null) {
            candles = overseasQuoteService.getOverseasCandles(symbol, exchange, range);
            stockType = StockType.OVERSEAS;
        } else {
            stockType = detectStockType(symbol);
            switch (stockType) {
                case DOMESTIC -> candles = domesticQuoteService.getCandles(symbol, range);
                case OVERSEAS -> candles = overseasQuoteService.getUsCandlesForSymbol(symbol, range);
                default -> throw new IllegalArgumentException("지원하지 않는 종목 형식: " + symbol);
            }
        }

        return ApiResponse.success(candles);
    }
    
    /**
     * 종목 타입 자동 감지
     */
    private StockType detectStockType(String symbol) {
        // 6자리 숫자 → 국내주식
        if (symbol.matches("\\d{6}")) {
            return StockType.DOMESTIC;
        }
        
        // 영문 심볼 → 해외주식 (미국)
        if (symbol.matches("[A-Z]+")) {
            return StockType.OVERSEAS;
        }
        
        throw new IllegalArgumentException("알 수 없는 종목 형식: " + symbol);
    }
    
    private String getMarketName(StockType type) {
        return switch (type) {
            case DOMESTIC -> "한국 (KOSPI/KOSDAQ)";
            case OVERSEAS -> "해외 (미국/기타)";
        };
    }
    
    private String getCurrency(StockType type) {
        return switch (type) {
            case DOMESTIC -> "KRW";
            case OVERSEAS -> "USD";
        };
    }
    
    /**
     * 종목 타입 Enum
     */
    public enum StockType {
        DOMESTIC,   // 국내주식
        OVERSEAS,   // 해외주식 (미국 등)
    }
    
    /**
     * 통합 시세 응답 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnifiedQuoteResponse {
        private QuoteDto.QuoteResponse quote;
        private StockType stockType;
        private String market;
        private String currency;
    }
}