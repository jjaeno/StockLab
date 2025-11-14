package com.StockLab.backend.Order;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
/*
 * 보유 주식 엔티티
 */
@Entity
@Table(name = "positions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"uid", "symbol"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 64, nullable = false)
    private String uid;
    
    @Column(length = 32, nullable = false)
    private String symbol;
    
    @Column(precision = 18, scale = 6, nullable = false)
    private BigDecimal quantity;
    
    @Column(name = "avg_price", precision = 18, scale = 6, nullable = false)
    private BigDecimal avgPrice;
}