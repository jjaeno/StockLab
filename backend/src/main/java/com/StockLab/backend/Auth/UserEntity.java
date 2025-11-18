package com.StockLab.backend.Auth;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {
    
    @Id
    @Column(length = 64)
    private String uid;
    
    @Column(length = 255)
    private String email;
    
    @Column(name = "display_name", length = 255)
    private String displayName;
    
    @Column(name = "cash_krw", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal cashKrw = new BigDecimal("10000000.00");

    @Column(name = "cash_usd", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal cashUsd = new BigDecimal("10000.00"); 

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    //통화별 잔액 조회
    public BigDecimal getCash(Currency currency) {
        return currency == Currency.KRW ? cashKrw : cashUsd;
    }
    
    
    //통화별 잔액 설정
    public void setCash(Currency currency, BigDecimal amount) {
        if (currency == Currency.KRW) {
            this.cashKrw = amount;
        } else {
            this.cashUsd = amount;
        }
    }
    
    //통화 Enum
    public enum Currency {
        KRW, USD
    }

}
