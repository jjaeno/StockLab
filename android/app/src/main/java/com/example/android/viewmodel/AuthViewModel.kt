package com.example.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.example.android.data.api.ApiResult
import com.example.android.data.model.AuthResponse
import com.example.android.data.repository.StockLabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * 인증 ViewModel
 * - Firebase 인증 관리
 * - 백엔드 토큰 검증
 * - 사용자 정보 전역 상태 관리
 */

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: StockLabRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    // 현재 Firebase 사용자
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    // 백엔드 인증 정보
    private val _authResponse = MutableStateFlow<AuthResponse?>(null)
    val authResponse: StateFlow<AuthResponse?> = _authResponse.asStateFlow()

    // 로그인 상태
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // 사용자 UID (편의성)
    val uid: String?
        get() = _authResponse.value?.uid ?: _currentUser.value?.uid

    init {
        // Firebase 인증 상태 리스너 등록
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser

            // 로그인되어 있으면 백엔드 토큰 검증
            auth.currentUser?.let { user ->
                verifyWithBackend(user)
            } ?: run {
                _authState.value = AuthState.LoggedOut
                _authResponse.value = null
            }
        }
    }

    /**
     * 이메일/비밀번호 로그인
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { user ->
                    _currentUser.value = user
                    verifyWithBackend(user)
                } ?: run {
                    _authState.value = AuthState.Error("로그인 실패")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "로그인 중 오류 발생")
            }
        }
    }

    /**
     * 이메일/비밀번호 회원가입
     */
    fun signUpWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let { user ->
                    // 사용자 프로필 업데이트
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                    user.updateProfile(profileUpdates).await()

                    _currentUser.value = user
                    verifyWithBackend(user)
                } ?: run {
                    _authState.value = AuthState.Error("회원가입 실패")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "회원가입 중 오류 발생")
            }
        }
    }

    /**
     * 로그아웃
     */
    fun signOut() {
        firebaseAuth.signOut()
        _currentUser.value = null
        _authResponse.value = null
        _authState.value = AuthState.LoggedOut
    }

    /**
     * 백엔드 토큰 검증
     * Firebase ID Token을 백엔드로 전송하여 검증 및 사용자 정보 동기화
     */
    private fun verifyWithBackend(user: FirebaseUser) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                // Firebase ID Token 가져오기
                val idToken = user.getIdToken(false).await().token

                if (idToken != null) {
                    // 백엔드 API 호출
                    repository.verifyToken(idToken).collect { result ->
                        when (result) {
                            is ApiResult.Success -> {
                                _authResponse.value = result.data
                                _authState.value = AuthState.Authenticated(result.data)
                            }
                            is ApiResult.Error -> {
                                _authState.value = AuthState.Error(result.message)
                            }
                            is ApiResult.Loading -> {
                                _authState.value = AuthState.Loading
                            }
                        }
                    }
                } else {
                    _authState.value = AuthState.Error("토큰 발급 실패")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "토큰 검증 실패")
            }
        }
    }

    /**
     * 사용자 잔액 업데이트 (거래 후)
     */
    fun updateBalance(cashKrw: Double? = null, cashUsd: Double? = null) {
        _authResponse.value?.let { current ->
            _authResponse.value = current.copy(
                cashKrw = cashKrw ?: current.cashKrw,
                cashUsd = cashUsd ?: current.cashUsd
            )
        }
    }
}

/**
 * 인증 상태 Sealed Class
 */
sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class Authenticated(val authResponse: AuthResponse) : AuthState()
    data class Error(val message: String) : AuthState()
}