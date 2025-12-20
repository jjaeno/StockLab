package com.StockLab.backend.Quote;

import com.StockLab.backend.common.ApiResponse;
import com.StockLab.backend.Quote.QuoteDto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 다중 종목 시세 조회 전용 Controller
 * 
 * 프론트엔드는 이 엔드포인트만 호출
 */
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Multi Quote", description = "다중 종목 시세 조회 API (누락 0% 보장)")
public class MultiQuoteController {
    
    private final MultiQuoteService multiQuoteService;
    
    /**
     * 다중 종목 시세 일괄 조회
     * 
     * POST /api/v1/quotes/batch
     * Body:
     * {
     *   "symbols": ["005930", "000660", "AAPL", "TSLA", ...]
     * }
     * 
     * Response:
     * {
     *   "success": true,
     *   "data": {
     *     "results": [
     *       {
     *         "symbol": "005930",
     *         "status": "SUCCESS",
     *         "data": { "currentPrice": 70000, ... },
     *         "source": "KIS",
     *         "cached": false
     *       },
     *       {
     *         "symbol": "AAPL",
     *         "status": "FAILED",
     *         "reason": "TIMEOUT",
     *         "lastKnownPrice": 180.50,
     *         "source": "LAST_KNOWN"
     *       },
     *       ... // 항상 N개
     *     ],
     *     "totalRequested": 30,
     *     "successCount": 25,
     *     "failedCount": 5,
     *     "cachedCount": 10
     *   }
     * }
     */
    @PostMapping("/batch")
    @Operation(
        summary = "다중 종목 시세 일괄 조회",
        description = "국내/해외 종목을 한 번에 조회합니다.\n\n" +
                      "**중요**: 응답 개수는 항상 요청 개수와 동일합니다.\n" +
                      "실패한 종목도 status=FAILED로 포함됩니다."
    )
    public ApiResponse<BatchQuoteResponse> getBatchQuotes(
            @Valid @RequestBody BatchQuoteRequest request) {
        
        log.info("다중 시세 조회 요청: {} 건", request.getSymbols().size());
        
        BatchQuoteResponse response = multiQuoteService.getBatchQuotes(request.getSymbols());
        
        return ApiResponse.success(response, 
                String.format("%d건 조회 완료 (성공: %d, 실패: %d)", 
                        response.getTotalRequested(),
                        response.getSuccessCount(),
                        response.getFailedCount()));
    }
}