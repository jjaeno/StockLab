package com.StockLab.backend.Watchlist;

import com.StockLab.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist", description = "관심 종목 관리 API")
public class WatchlistController {
    
    private final WatchlistService watchlistService;
    
    /**
     * 관심 종목 목록 조회
     *
     * GET /api/v1/watchlist
     * Header: X-User-UID: firebase-uid-123
     */
    @GetMapping
    @Operation(summary = "관심 종목 목록", description = "사용자의 관심 종목을 조회")
    public ApiResponse<WatchlistDto.ListResponse> list(@RequestHeader("X-User-UID") String uid) {
        return ApiResponse.success(watchlistService.list(uid));
    }
    
    /**
     * 관심 종목 추가
     *
     * POST /api/v1/watchlist
     * Header: X-User-UID: firebase-uid-123
     * Body:
     * {
     *   "symbol": "AAPL",
     *   "exchange": "NASDAQ"   // 해외일 때 선택
     * }
     */
    @PostMapping
    @Operation(summary = "관심 종목 추가", description = "종목/거래소를 관심 종목에 등록")
    public ApiResponse<WatchlistDto.Item> add(
            @RequestHeader("X-User-UID") String uid,
            @Valid @RequestBody WatchlistDto.AddRequest request) {
        return ApiResponse.success(watchlistService.add(uid, request));
    }
    
    /**
     * 관심 종목 삭제
     *
     * DELETE /api/v1/watchlist/symbol
     * Header: X-User-UID: firebase-uid-123
     */
    @DeleteMapping("/{symbol}")
    @Operation(summary = "관심 종목 삭제", description = "심볼과 거래소로 관심 종목 삭제")
    public ApiResponse<Void> remove(
            @RequestHeader("X-User-UID") String uid,
            @PathVariable String symbol,
            @RequestParam(required = false) String exchange) {
        watchlistService.remove(uid, symbol, exchange);
        return ApiResponse.success(null, "삭제되었습니다.");
    }
}
