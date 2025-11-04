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
 * 인증 서비스
 * Firebase ID Token 검증 및 사용자 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth;
    
    /**
     * Firebase ID Token 검증
     * @param idToken Firebase ID Token
     * @return 인증된 사용자 정보
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
                    .build(); //cash는 기본값 10,000,000으로 들어감
            userRepository.save(user);
            isNewUser = true;
        }
        
        return AuthDto.AuthResponse.builder()
                .uid(user.getUid())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .cash(user.getCash())
                .newUser(isNewUser)
                .build();
    }
    
    // 사용자 조회
    public UserEntity getUserByUid(String uid) {
        return userRepository.findById(uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    
    // 사용자 현금 업데이트
    @Transactional
    public void updateUserCash(String uid, BigDecimal newCash) {
        UserEntity user = getUserByUid(uid);
        user.setCash(newCash);
        userRepository.save(user);
    }
}