package com.StockLab.backend.config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 한국투자증권 Open API 토큰 관리 클래스
 * 
 * 기능:
 * 1. OAuth2 access_token 자동 발급
 * 2. 토큰을 로컬 파일(token_cache.json)에 캐싱
 * 3. 23시간마다 자동 갱신
 * 4. 만료 시 자동으로 새 토큰 발급
 */
@Component
@Slf4j
public class KisTokenManager {
    
    @Value("${api.kis.appkey}")
    private String appKey;
    
    @Value("${api.kis.appsecret}")
    private String appSecret;
    
    @Value("${api.kis.base-url}")
    private String baseUrl;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private static final String TOKEN_CACHE_FILE = "token_cache.json";
    private static final long TOKEN_REFRESH_HOURS = 23; // 23시간마다 갱신
    
    private String accessToken;
    private LocalDateTime tokenExpiry;
    
    public KisTokenManager(WebClient.Builder webClientBuilder, 
                          @Value("${api.kis.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }
    
    /**
     * 애플리케이션 시작 시 초기화
     * 1. 캐시된 토큰 로드 시도
     * 2. 토큰이 없거나 만료되었으면 새로 발급
     * 3. 자동 갱신 스케줄러 시작
     */
    @PostConstruct
    public void initialize() {
        log.info("=== KIS Token Manager 초기화 시작 ===");
        
        // 1. 캐시된 토큰 로드 시도
        if (loadTokenFromCache()) {
            log.info("캐시된 토큰 로드 성공 (만료: {})", tokenExpiry);
        } else {
            log.info("캐시된 토큰이 없거나 만료됨. 새 토큰 발급 시작...");
            refreshToken();
        }
        
        // 2. 자동 갱신 스케줄러 시작 (23시간마다)
        scheduler.scheduleAtFixedRate(
            this::refreshToken,
            TOKEN_REFRESH_HOURS,
            TOKEN_REFRESH_HOURS,
            TimeUnit.HOURS
        );
        
        log.info("=== KIS Token Manager 초기화 완료 ===");
    }
    
    /**
     * 현재 유효한 Access Token 반환
     * 만료되었으면 자동으로 재발급
     */
    public String getAccessToken() {
        if (isTokenExpired()) {
            log.warn("토큰이 만료되었습니다. 재발급 시작...");
            refreshToken();
        }
        return accessToken;
    }
    
    /**
     * OAuth2 토큰 발급
     * POST /oauth2/tokenP
     */
    private void refreshToken() {
        log.info(">>> 한국투자증권 OAuth2 토큰 발급 요청");
        
        try {
            // 요청 바디 생성
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("grant_type", "client_credentials");
            requestBody.put("appkey", appKey);
            requestBody.put("appsecret", appSecret);
            
            // API 호출
            String response = webClient.post()
                    .uri("/oauth2/tokenP")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // 응답 파싱
            JsonNode json = objectMapper.readTree(response);
            
            // access_token 추출
            this.accessToken = json.get("access_token").asText();
            
            // 만료 시간 설정 (현재 시각 + 24시간)
            this.tokenExpiry = LocalDateTime.now().plusHours(24);
            
            log.info("토큰 발급 성공!");
            log.info("   - Access Token: {}...", accessToken.substring(0, 30));
            log.info("   - 만료 시각: {}", tokenExpiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // 캐시 파일에 저장
            saveTokenToCache();
            
        } catch (Exception e) {
            log.error("토큰 발급 실패: {}", e.getMessage(), e);
            throw new RuntimeException("KIS 토큰 발급 실패", e);
        }
    }
    
    /**
     * 토큰 만료 여부 확인
     */
    private boolean isTokenExpired() {
        if (accessToken == null || tokenExpiry == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(tokenExpiry);
    }
    
    /**
     * 토큰을 로컬 파일에 저장
     * token_cache.json 파일 생성
     */
    private void saveTokenToCache() {
        try {
            TokenCache cache = new TokenCache();
            cache.setAccessToken(accessToken);
            cache.setTokenExpiry(tokenExpiry.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(TOKEN_CACHE_FILE), cache);
            
            log.debug("토큰 캐시 저장 완료: {}", TOKEN_CACHE_FILE);
            
        } catch (IOException e) {
            log.warn("토큰 캐시 저장 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 로컬 파일에서 토큰 로드
     * @return 유효한 토큰이 있으면 true, 없으면 false
     */
    private boolean loadTokenFromCache() {
        try {
            File cacheFile = new File(TOKEN_CACHE_FILE);
            if (!cacheFile.exists()) {
                return false;
            }
            
            TokenCache cache = objectMapper.readValue(cacheFile, TokenCache.class);
            this.accessToken = cache.getAccessToken();
            this.tokenExpiry = LocalDateTime.parse(
                    cache.getTokenExpiry(), 
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
            
            // 만료되었는지 확인
            if (isTokenExpired()) {
                log.info("캐시된 토큰이 만료됨");
                return false;
            }
            
            log.debug("캐시된 토큰 로드 완료");
            return true;
            
        } catch (Exception e) {
            log.warn("토큰 캐시 로드 실패: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 토큰 캐시 DTO
     */
    @Data
    private static class TokenCache {
        private String accessToken;
        private String tokenExpiry;
    }
}