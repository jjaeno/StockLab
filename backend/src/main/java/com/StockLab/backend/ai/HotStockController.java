package com.StockLab.backend.ai;

import com.StockLab.backend.ai.AiDto.HotStocksResponse;
import com.StockLab.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.StockLab.backend.ai.AiDto.HotStockItem;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "AI Analysis", description = "AI 주목 종목 분석")
public class HotStockController {

    private final HotStockService hotStockService;

    @GetMapping("/hot-stocks")
    @Operation(summary = "AI 주목 종목", description = "뉴스 이슈 기반 주목 종목 TOP 3")
    public ApiResponse<List<HotStockItem>> getHotStocks(
            @RequestParam(defaultValue = "3") int limit,
            @RequestParam(required = false) List<String> symbols) {
        List<HotStockItem> items = hotStockService.getHotStocks(limit, symbols);
        return ApiResponse.success(items);
    }
}