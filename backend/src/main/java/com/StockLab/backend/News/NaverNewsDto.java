package com.StockLab.backend.News;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * 네이버 뉴스 검색 API 응답 매핑 DTO
 * - Naver API 원본 구조를 그대로 받기 위한 DTO (필드 누락/추가에도 안전)
 */
public class NaverNewsDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NaverNewsApiResponse {
        private String lastBuildDate;
        private int total;
        private int start;
        private int display;
        private List<NaverNewsItem> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NaverNewsItem {
        private String title;        // HTML 태그 포함 가능
        private String originallink; // 원문 링크
        private String link;         // 네이버 링크(또는 원문)
        private String description;  // HTML 태그 포함 가능
        private String pubDate;      // RFC 822 스타일 문자열
    }

    /**
     * StockLab 화면에 표시할 "정규화된" 뉴스 DTO
     * - 프론트에서 쓰기 편한 형태로 통일
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewsArticle {
        private String title;        // 정제된 제목
        private String summary;      // 정제된 요약(description)
        private String url;          // 가능하면 originallink 우선
        private String publishedAt;  // 원문 문자열 그대로(파싱 실패 대비)
        private String source;       // "NAVER"
        private String query;        // 디버깅용(어떤 검색어로 찾았는지)
    }
}
