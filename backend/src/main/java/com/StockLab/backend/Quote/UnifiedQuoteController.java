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
 * - 홍콩주식 (.HK)
 * - 기타 해외주식 (거래소 명시)
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
     * GET /api/v1/quotes/700.HK    → 텐센트 (홍콩)
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
                    
                case HONGKONG:
                    log.debug("   → 홍콩 주식");
                    quote = overseasQuoteService.getHkStock(symbol);
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
     * GET /api/v1/quotes?symbols=005930,AAPL,TSLA,000660,700.HK
     * 
     * 쉼표로 구분하여 여러 종목을 한 번에 조회
     * 국내주식과 해외주식을 섞어서 사용 가능
     */
    @GetMapping
    @Operation(
        summary = "다중 종목 시세 조회",
        description = "쉼표로 구분하여 여러 종목을 한 번에 조회합니다.\n"
    )
    public ApiResponse<List<UnifiedQuoteResponse>> getQuotes(
            @RequestParam @Parameter(description = "쉼표로 구분된 종목 리스트", example = "005930,AAPL,TSLA") 
            String symbols) {
        
        log.info("다중 조회: {}", symbols);
        
        List<UnifiedQuoteResponse> responses = new ArrayList<>();
        
        for (String symbol : symbols.split(",")) {
            symbol = symbol.trim();
            
            try {
                StockType stockType = detectStockType(symbol);
                QuoteDto.QuoteResponse quote;
                
                switch (stockType) {
                    case DOMESTIC:
                        quote = domesticQuoteService.getQuote(symbol);
                        break;
                    case OVERSEAS:
                        quote = overseasQuoteService.getUsStock(symbol);
                        break;
                    case HONGKONG:
                        quote = overseasQuoteService.getHkStock(symbol);
                        break;
                    default:
                        continue;
                }
                
                responses.add(UnifiedQuoteResponse.builder()
                        .quote(quote)
                        .stockType(stockType)
                        .market(getMarketName(stockType))
                        .currency(getCurrency(stockType))
                        .build());
                        
            } catch (Exception e) {
                log.warn("종목 조회 실패: {} - {}", symbol, e.getMessage());
            }
        }
        
        return ApiResponse.success(responses);
    }
    
    /**
     * 캔들 차트 데이터 조회 (국내 + 해외)
     * 
     * GET /api/v1/quotes/AAPL/candles?range=1M
     * GET /api/v1/quotes/005930/candles?range=3M
     * GET /api/v1/quotes/700.HK/candles?range=1Y
     * GET /api/v1/quotes/AAPL/candles?range=1W&exchange=NASDAQ
     */
    @GetMapping("/{symbol}/candles")
    @Operation(
        summary = "캔들 차트 데이터 조회",
        description = "일자별 시가, 고가, 저가, 종가 데이터를 조회합니다.\n" +
                      "국내주식 + 해외주식(미국, 홍콩 등) 지원"
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
                case HONGKONG -> candles = overseasQuoteService.getHkCandles(symbol, range);
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
        
        // .HK로 끝나면 → 홍콩주식
        if (symbol.endsWith(".HK")) {
            return StockType.HONGKONG;
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
            case HONGKONG -> "홍콩 (HKEX)";
        };
    }
    
    private String getCurrency(StockType type) {
        return switch (type) {
            case DOMESTIC -> "KRW";
            case OVERSEAS -> "USD";
            case HONGKONG -> "HKD";
        };
    }
    
    /**
     * 종목 타입 Enum
     */
    public enum StockType {
        DOMESTIC,   // 국내주식
        OVERSEAS,   // 해외주식 (미국 등)
        HONGKONG    // 홍콩주식
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