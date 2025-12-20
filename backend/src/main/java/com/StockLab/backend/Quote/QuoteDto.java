package com.StockLab.backend.Quote;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * 시세 관련 DTO 클래스들
 */
public class QuoteDto {
    
    /**
     * 단일 종목 시세 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteResponse {
        private String symbol;
        private BigDecimal currentPrice;
        private BigDecimal change;
        private BigDecimal percentChange;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal open;
        private BigDecimal previousClose;
        private Long timestamp;
    }
    
    /**
     * 캔들 차트 데이터 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandleResponse {
        private String symbol;
        private String range;
        private List<Long> timestamps;
        private List<Double> open;
        private List<Double> high;
        private List<Double> low;
        private List<Double> close;
        private List<Long> volume;
    }

    // 배치 시세 요청/응답 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteResult {
        private String symbol;
        private ResultStatus status;  // SUCCESS, FAILED, CACHED
        private QuoteResponse data;
        private String reason;  // TIMEOUT, RATE_LIMIT, PARSE_ERROR, API_ERROR
        private BigDecimal lastKnownPrice;  // 실패 시 마지막 성공값
        private String source;  // KIS, CACHE, LAST_KNOWN
        private boolean cached;
        private Long fetchedAt;
    }

    public enum ResultStatus {
        SUCCESS,
        FAILED,
        CACHED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchQuoteRequest {
        @NotEmpty
        private List<String> symbols;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchQuoteResponse {
        private List<QuoteResult> results;
        private int totalRequested;
        private int successCount;
        private int failedCount;
        private int cachedCount;
        private Long timestamp;
    }
}