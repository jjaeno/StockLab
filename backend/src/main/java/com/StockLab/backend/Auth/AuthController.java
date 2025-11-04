package com.StockLab.backend.Auth;


import com.google.firebase.auth.FirebaseAuthException;
import com.StockLab.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 Controller
 * Firebase 토큰 검증 API
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Firebase 인증 API")
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * Firebase ID Token 검증
     * 
     * POST /api/v1/auth/verify
     * Body: { "idToken": "..." }
     * 
     * Response:
     * {
     *   "success": true,
     *   "data": {
     *     "uid": "firebase-uid-123",
     *     "email": "user@example.com",
     *     "displayName": "홍길동",
     *     "cash": 10000000.00,
     *     "newUser": false
     *   },
     *   "timestamp": "2025-01-15T10:30:00"
     * }
     */
    @PostMapping("/verify")
    @Operation(summary = "Firebase 토큰 검증", 
               description = "Firebase ID Token을 검증하고 사용자 정보를 반환합니다")
    public ApiResponse<AuthDto.AuthResponse> verifyToken(
            @RequestBody AuthDto.VerifyRequest request) throws FirebaseAuthException {
        
        log.info("Token verification request received");
        AuthDto.AuthResponse response = authService.verifyToken(request.getIdToken());
        
        return ApiResponse.success(response, "인증에 성공했습니다");
    }
}