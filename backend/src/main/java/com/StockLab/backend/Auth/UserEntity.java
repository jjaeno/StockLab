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
    
    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal cash = new BigDecimal("10000000.00");
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
