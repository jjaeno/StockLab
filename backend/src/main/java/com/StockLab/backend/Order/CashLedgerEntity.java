package com.StockLab.backend.Order;

import com.StockLab.backend.Auth.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 현금 입출금 기록 엔티티 (통화 구분 지원)
 */
@Entity
@Table(name = "cash_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashLedgerEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 64, nullable = false)
    private String uid;
    
    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 3, nullable = false)
    private UserEntity.Currency currency;  // KRW 또는 USD
    
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private TransactionType transactionType;  // DEPOSIT, WITHDRAW, BUY, SELL
    
    @Column(length = 32)
    private String symbol;  // 거래 종목 (매수/매도 시에만)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    /**
     * 거래 유형 Enum
     */
    public enum TransactionType {
        DEPOSIT,   // 입금
        WITHDRAW,  // 출금
        BUY,       // 매수
        SELL       // 매도
    }
}