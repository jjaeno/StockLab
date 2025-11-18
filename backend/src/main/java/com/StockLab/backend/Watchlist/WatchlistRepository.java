package com.StockLab.backend.Watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistEntity, Long> {
    List<WatchlistEntity> findByUidOrderByCreatedAtDesc(String uid);
    Optional<WatchlistEntity> findByUidAndSymbolAndExchange(String uid, String symbol, String exchange);
    boolean existsByUidAndSymbolAndExchange(String uid, String symbol, String exchange);
    void deleteByUidAndSymbolAndExchange(String uid, String symbol, String exchange);
}
