package com.StockLab.backend.News;

import com.StockLab.backend.News.NaverNewsDto.NewsArticle;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 네이버 뉴스 컨트롤러
 *
 * 호출 방식:
 * - 종목 클릭 시 symbol + displayName을 넘기는 것이 정확도 최고
 * - query로 직접 검색도 지원 (디버깅/확장에 유리)
 */
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NaverNewsController {

    private final NaverNewsService naverNewsService;

    /**
     * 예:
     * GET /api/v1/news/naver?symbol=AAPL&displayName=Apple%20Inc.&limit=5
     * GET /api/v1/news/naver?query=애플%20주가&limit=5
     */
    @GetMapping("/naver")
    public ResponseEntity<List<NewsArticle>> getNaverNews(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "5") int limit
    ) {
        // 최소 입력 방어 (symbol/displayName/query 중 하나는 있어야 함)
        boolean hasAny = (query != null && !query.isBlank())
                || (symbol != null && !symbol.isBlank())
                || (displayName != null && !displayName.isBlank());

        if (!hasAny) {
            // 빈 입력은 빈 결과로 반환 (프론트도 안전)
            return ResponseEntity.ok(List.of());
        }

        List<NewsArticle> result = naverNewsService.searchNewsBySymbol(symbol, displayName, query, limit);
        return ResponseEntity.ok(result);
    }
}
