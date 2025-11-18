package com.StockLab.backend.Portfolio;

import com.StockLab.backend.Auth.UserEntity;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포트폴리오 조회용 DTO 모음
 */
public class PortfolioDto {
    
    /**
     * 개별 보유 종목 뷰
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionView {
        private String symbol;
        private BigDecimal quantity; // 보유 수량
        private BigDecimal avgPrice; // 평균 매입가
        private BigDecimal currentPrice; // 현재가
        private BigDecimal marketValue; // 평가 금액
        private BigDecimal profitLoss; // 평가 손익
        private UserEntity.Currency currency; // 통화(KRW/USD)
    }
    
    /**
     * 포트폴리오 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioResponse {
        private String uid; // 사용자 UID
        private BigDecimal cashKrw;
        private BigDecimal cashUsd;
        private List<PositionView> positions; // 보유 종목 리스트
        private BigDecimal totalMarketValueKrw; // 총 평가 금액 (KRW)
        private BigDecimal totalMarketValueUsd; // 총 평가 금액 (USD)
    }
}
