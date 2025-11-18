package com.StockLab.backend.Auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.StockLab.backend.common.BusinessException;
import com.StockLab.backend.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * 인증 서비스 (KRW/USD 이중 화폐 지원)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth;
    
    /**
     * Firebase ID Token 검증
     */
    @Transactional
    public AuthDto.AuthResponse verifyToken(String idToken) throws FirebaseAuthException {
        log.debug("Verifying Firebase token");
        
        // Firebase 토큰 검증
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String name = decodedToken.getName();
        
        // 사용자 조회
        UserEntity user = userRepository.findById(uid).orElse(null);
        boolean isNewUser = false;
        
        // 신규 사용자 등록(회원가입)
        if (user == null) {
            log.info("New user registration: {}", uid);
            user = UserEntity.builder()
                    .uid(uid)
                    .email(email)
                    .displayName(name)
                    .cashKrw(new BigDecimal("10000000.00"))  // 초기 KRW: 1000만원
                    .cashUsd(new BigDecimal("10000.00"))     // 초기 USD: 1만달러
                    .build();
            userRepository.save(user);
            isNewUser = true;
        }
        
        return AuthDto.AuthResponse.builder()
                .uid(user.getUid())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .cashKrw(user.getCashKrw())
                .cashUsd(user.getCashUsd())
                .newUser(isNewUser)
                .build();
    }
    
    // 사용자 조회
    public UserEntity getUserByUid(String uid) {
        return userRepository.findById(uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    
    // 사용자 현금 업데이트 (통화별)
    @Transactional
    public void updateUserCash(String uid, UserEntity.Currency currency, BigDecimal newCash) {
        UserEntity user = getUserByUid(uid);
        user.setCash(currency, newCash);
        userRepository.save(user);
        
        log.debug("사용자 {} {} 잔액 업데이트: {}", uid, currency, newCash);
    }
}