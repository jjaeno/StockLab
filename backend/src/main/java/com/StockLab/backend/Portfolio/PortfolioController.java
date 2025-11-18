package com.StockLab.backend.Portfolio;

import com.StockLab.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

 /**
     * 포트폴리오 조회
     *
     * GET /api/v1/portfolio
     * Header: X-User-UID: firebase-uid-123
     */

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "포트폴리오 조회 API")
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    
    @GetMapping
    @Operation(summary = "포트폴리오 조회", description = "보유 종목/현금/평가 금액 조회")
    public ApiResponse<PortfolioDto.PortfolioResponse> getPortfolio(
            @RequestHeader("X-User-UID") String uid) {
        return ApiResponse.success(portfolioService.getPortfolio(uid));
    }
}
