package com.StockLab.backend.Quote;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 마지막 성공 시세 저장소 (무기한 보관)
 * 실패 시 폴백으로 사용
 */
@Component
@Slf4j
public class LastKnownGoodCache {
    
    private final ConcurrentHashMap<String, BigDecimal> lastGoodPrices = new ConcurrentHashMap<>();
    
    public void save(String symbol, BigDecimal price) {
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            lastGoodPrices.put(symbol, price);
            log.debug("Last-known-good 저장: {} → {}", symbol, price);
        }
    }
    
    public BigDecimal get(String symbol) {
        return lastGoodPrices.get(symbol);
    }
    
    public boolean has(String symbol) {
        return lastGoodPrices.containsKey(symbol);
    }
}