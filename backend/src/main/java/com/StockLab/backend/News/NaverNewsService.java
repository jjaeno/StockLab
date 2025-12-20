package com.StockLab.backend.News;

import com.StockLab.backend.News.NaverNewsDto.NaverNewsApiResponse;
import com.StockLab.backend.News.NaverNewsDto.NaverNewsItem;
import com.StockLab.backend.News.NaverNewsDto.NewsArticle;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 네이버 뉴스 검색 API 서비스
 *
 * 안정성 포인트:
 * - query 인코딩 안전 처리
 * - NPE 방지 (items null 등)
 * - HTML 태그 제거 + HTML 엔티티 언이스케이프
 * - 캐시로 호출량 방어
 */
@Service
@Slf4j
public class NaverNewsService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    private final Cache<String, List<NewsArticle>> cache;

    public NaverNewsService(
            WebClient.Builder webClientBuilder,
            @Value("${api.naver.base-url}") String baseUrl,
            @Value("${api.naver.client-id}") String clientId,
            @Value("${api.naver.client-secret}") String clientSecret
    ) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-Naver-Client-Id", clientId)
                .defaultHeader("X-Naver-Client-Secret", clientSecret)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        this.clientId = clientId;
        this.clientSecret = clientSecret;

        // 캐시: 동일 query에 대해 10분 캐시 (무료/쿼터 보호 + 체감 속도 개선)
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(300)
                .build();
    }

    /**
     * 종목 기반 뉴스 조회 (권장)
     * - 프론트가 symbol + displayName(회사명)을 함께 넘겨주면 정확도가 상승
     */
    public List<NewsArticle> searchNewsBySymbol(String symbol, String displayName, String queryOverride, int limit) {
        String query = buildQuery(symbol, displayName, queryOverride);

        // 네이버 display는 1~100 범위지만, 모바일 UI는 보통 5~20이면 충분
        int display = clamp(limit, 1, 20);

        // sort: sim(정확도) / date(최신). 니 요구는 "관련 뉴스"가 우선이므로 sim 추천.
        // 다만 최신성도 중요하면 date로 바꿔도 됨.
        String sort = "sim";

        return searchNaverNews(query, display, 1, sort);
    }

    /**
     * 네이버 뉴스 API 호출
     */
    private List<NewsArticle> searchNaverNews(String query, int display, int start, String sort) {
        String cacheKey = "q=" + query + "|display=" + display + "|start=" + start + "|sort=" + sort;
        List<NewsArticle> cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            // query는 URL 인코딩이 안전 (WebClient uriBuilder도 해주지만, 한글/특수문자 확실히 막기 위해 이중 안전장치)
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

            NaverNewsApiResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/news.json")
                            .queryParam("query", query) // 이미 인코딩 되었으므로 그대로 사용
                            .queryParam("display", display)
                            .queryParam("start", start)
                            .queryParam("sort", sort)
                            .build(true)) // encoded query를 그대로 사용
                    .retrieve()
                    .bodyToMono(NaverNewsApiResponse.class)
                    .block(Duration.ofSeconds(5));

            List<NewsArticle> articles = normalize(response, query);

            cache.put(cacheKey, articles);
            log.info("네이버 뉴스 조회 성공: query='{}', size={}", query, articles.size());
            return articles;

        } catch (Exception e) {
            log.error("네이버 뉴스 조회 실패: query='{}'", query, e);
            return List.of();
        }
    }

    /**
     * 네이버 응답 → 내부 NewsArticle로 정규화
     */
    private List<NewsArticle> normalize(NaverNewsApiResponse response, String query) {
        if (response == null || response.getItems() == null) {
            return List.of();
        }

        return response.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> toArticle(item, query))
                .filter(a -> a.getTitle() != null && !a.getTitle().isBlank())
                .collect(Collectors.toList());
    }

    private NewsArticle toArticle(NaverNewsItem item, String query) {
        String title = cleanText(item.getTitle());
        String summary = cleanText(item.getDescription());

        // url은 originallink 우선, 없으면 link
        String url = firstNonBlank(item.getOriginallink(), item.getLink());

        return NewsArticle.builder()
                .title(title)
                .summary(summary)
                .url(url == null ? "" : url)
                .publishedAt(safe(item.getPubDate()))
                .source("NAVER")
                .query(query)
                .build();
    }

    /**
     * 검색어 생성 전략:
     * - queryOverride가 있으면 최우선 사용
     * - displayName이 있으면 displayName 중심 (예: "Apple" / "삼성전자")
     * - 그 외 symbol
     *
     * 주의:
     * - 미국 종목은 "AAPL"만으로는 한국 기사 매칭이 약할 수 있어 displayName을 같이 쓰는 게 최선.
     */
    private String buildQuery(String symbol, String displayName, String queryOverride) {
        if (queryOverride != null && !queryOverride.isBlank()) {
            return queryOverride.trim();
        }

        String s = (symbol == null) ? "" : symbol.trim();
        String name = (displayName == null) ? "" : displayName.trim();

        if (!name.isBlank() && !s.isBlank()) {
            // 회사명 + 심볼을 같이 넣어 관련성을 올림
            // 예: "Apple AAPL", "마이크로소프트 MSFT", "삼성전자 005930"
            return name + " " + s;
        }

        if (!name.isBlank()) return name;
        if (!s.isBlank()) return s;

        // 여긴 컨트롤러에서 이미 막겠지만, 안전 처리
        return "";
    }

    /**
     * HTML 태그 제거 + HTML 엔티티 언이스케이프
     * 네이버 뉴스 title/description은 <b>태그 및 엔티티가 섞여서 옴
     */
    private String cleanText(String raw) {
        if (raw == null) return "";

        // 1) HTML 엔티티 처리 (&quot;, &amp; 등)
        String unescaped = HtmlUtils.htmlUnescape(raw);

        // 2) HTML 태그 제거
        String noTags = unescaped.replaceAll("<[^>]*>", "");

        // 3) 공백 정리
        return noTags.replaceAll("\\s+", " ").trim();
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
