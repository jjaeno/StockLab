package com.StockLab.backend.Quote;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

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
}