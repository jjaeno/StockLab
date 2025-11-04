package com.StockLab.backend.Auth;

import lombok.*;
import java.math.BigDecimal;

/**
 * 인증 관련 DTO 클래스들
 */
public class AuthDto {
    
    // ID 토큰 검증 요청
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyRequest {
        private String idToken;
    }
    
    // 인증 응답 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String uid;
        private String email;
        private String displayName;
        private BigDecimal cash;
        private boolean newUser;
    }
}
