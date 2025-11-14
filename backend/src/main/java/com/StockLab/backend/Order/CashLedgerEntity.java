package com.StockLab.backend.Order;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/*
 * 현금 입출금 기록 엔티티
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
    
    @Column(length = 32, nullable = false)
    private String reason;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}