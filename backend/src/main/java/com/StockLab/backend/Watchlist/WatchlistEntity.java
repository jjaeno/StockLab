package com.StockLab.backend.Watchlist;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist", uniqueConstraints = @UniqueConstraint(columnNames = {"uid", "symbol", "exchange"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 64, nullable = false)
    private String uid;
    
    @Column(length = 32, nullable = false)
    private String symbol;
    
    @Column(length = 32)
    private String exchange;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
