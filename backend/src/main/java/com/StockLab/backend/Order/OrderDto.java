package com.StockLab.backend.Order;

import com.StockLab.backend.Auth.UserEntity;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderDto {
    
    /**
     * 주문 요청
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRequest {
        
        @NotBlank(message = "종목 코드/심볼은 필수입니다")
        private String symbol;
        
        @NotNull(message = "매수/매도 구분은 필수입니다")
        private OrderEntity.OrderSide side;
        
        @NotNull(message = "수량은 필수입니다")
        @DecimalMin(value = "0.000001", message = "수량은 0보다 커야 합니다")
        private BigDecimal quantity;
        
        // exchange는 선택사항 (해외주식일 경우 명시)
        private String exchange;
    }
    
    /**
     * 주문 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long orderId;
        private String symbol;
        private OrderEntity.OrderSide side;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal totalAmount;
        private UserEntity.Currency currency;  // 거래 통화
        private LocalDateTime createdAt;
    }
    
    /**
     * 입출금 요청 (통화 구분)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashRequest {
        
        @NotNull(message = "금액은 필수입니다")
        @DecimalMin(value = "0.01", message = "금액은 0.01 이상이어야 합니다")
        private BigDecimal amount;
        
        @NotNull(message = "통화는 필수입니다")
        private UserEntity.Currency currency;  // KRW 또는 USD
    }
    
    /**
     * 입출금 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashResponse {
        private BigDecimal amount;
        private UserEntity.Currency currency;
        private CashLedgerEntity.TransactionType transactionType;
        private BigDecimal balanceAfter;  // 거래 후 잔액
        private LocalDateTime timestamp;
    }
}