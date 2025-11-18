package com.StockLab.backend.Watchlist;

import com.StockLab.backend.common.BusinessException;
import com.StockLab.backend.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {
    
    private final WatchlistRepository watchlistRepository;
    
    /**
     * 관심 종목 추가
     */
    @Transactional
    public WatchlistDto.Item add(String uid, WatchlistDto.AddRequest request) {
        String symbol = request.getSymbol();
        String exchange = request.getExchange();
        
        boolean exists = watchlistRepository.existsByUidAndSymbolAndExchange(uid, symbol, exchange);
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 관심 종목에 추가되었습니다.");
        }
        
        WatchlistEntity saved = watchlistRepository.save(WatchlistEntity.builder()
                .uid(uid)
                .symbol(symbol)
                .exchange(exchange)
                .build());
        
        log.info("관심 종목 추가: {} {} (uid={})", symbol, exchange, uid);
        return toItem(saved);
    }
    
    /**
     * 관심 종목 목록 조회
     */
    @Transactional(readOnly = true)
    public WatchlistDto.ListResponse list(String uid) {
        List<WatchlistDto.Item> items = watchlistRepository.findByUidOrderByCreatedAtDesc(uid)
                .stream()
                .map(this::toItem)
                .collect(Collectors.toList());
        return WatchlistDto.ListResponse.builder()
                .uid(uid)
                .items(items)
                .build();
    }
    
    /**
     * 관심 종목 삭제
     */
    @Transactional
    public void remove(String uid, String symbol, String exchange) {
        WatchlistEntity entity = watchlistRepository
                .findByUidAndSymbolAndExchange(uid, symbol, exchange)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "관심 종목을 찾을 수 없습니다."));
        watchlistRepository.delete(entity);
        log.info("관심 종목 삭제: {} {} (uid={})", symbol, exchange, uid);
    }
    
    private WatchlistDto.Item toItem(WatchlistEntity entity) {
        return WatchlistDto.Item.builder()
                .id(entity.getId())
                .symbol(entity.getSymbol())
                .exchange(entity.getExchange())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
