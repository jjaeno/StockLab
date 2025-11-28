package com.example.android.data.repository

import com.example.android.data.api.ApiResult
import com.example.android.data.api.ApiService
import com.example.android.data.api.toApiResult
import com.example.android.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StockLab Repository
 * - API 호출을 캡슐화
 * - Flow를 통한 반응형 데이터 스트림 제공
 * - 에러 처리 및 로깅
 */

@Singleton
class StockLabRepository @Inject constructor(
    private val api: ApiService
) {

    // ==========================================
    // 인증 API
    // ==========================================

    /**
     * Firebase ID Token 검증
     */
    fun verifyToken(idToken: String): Flow<ApiResult<AuthResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.verifyToken(VerifyTokenRequest(idToken)).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ==========================================
    // 시세 조회 API
    // ==========================================

    /**
     * 단일 종목 시세 조회
     */
    fun getQuote(symbol: String, exchange: String? = null): Flow<ApiResult<UnifiedQuoteResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.getQuote(symbol, exchange).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * 다중 종목 시세 조회
     * @param symbols 쉼표로 구분된 종목 리스트
     */
    fun getMultipleQuotes(symbols: String): Flow<ApiResult<List<UnifiedQuoteResponse>>> = flow {
        emit(ApiResult.Loading)
        val result = api.getMultipleQuotes(symbols).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * 캔들 차트 데이터 조회
     */
    fun getCandles(
        symbol: String,
        range: CandleRange,
        exchange: String? = null
    ): Flow<ApiResult<CandleResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.getCandles(symbol, range.apiValue, exchange).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ==========================================
    // 주문 API
    // ==========================================

    /**
     * 주문 생성 (매수/매도)
     */
    fun createOrder(
        uid: String,
        request: OrderRequest
    ): Flow<ApiResult<OrderResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.createOrder(uid, request).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * 사용자 주문 내역 조회
     */
    fun getUserOrders(uid: String): Flow<ApiResult<List<OrderEntity>>> = flow {
        emit(ApiResult.Loading)
        val result = api.getUserOrders(uid).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ==========================================
    // 입출금 API
    // ==========================================

    /**
     * 입금
     */
    fun deposit(
        uid: String,
        amount: Double,
        currency: Currency
    ): Flow<ApiResult<CashResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.deposit(uid, CashRequest(amount, currency)).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * 출금
     */
    fun withdraw(
        uid: String,
        amount: Double,
        currency: Currency
    ): Flow<ApiResult<CashResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.withdraw(uid, CashRequest(amount, currency)).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ==========================================
    // 포트폴리오 API
    // ==========================================

    /**
     * 포트폴리오 조회
     */
    fun getPortfolio(uid: String): Flow<ApiResult<PortfolioResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.getPortfolio(uid).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ==========================================
    // 관심종목 API
    // ==========================================

    /**
     * 관심 종목 목록 조회
     */
    fun getWatchlist(uid: String): Flow<ApiResult<WatchlistResponse>> = flow {
        emit(ApiResult.Loading)
        val result = api.getWatchlist(uid).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * 관심 종목 추가
     */
    fun addToWatchlist(
        uid: String,
        symbol: String,
        exchange: String? = null
    ): Flow<ApiResult<WatchlistItem>> = flow {
        emit(ApiResult.Loading)
        val result = api.addToWatchlist(uid, AddWatchlistRequest(symbol, exchange)).toApiResult()
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * 관심 종목 삭제
     */
    fun removeFromWatchlist(
        uid: String,
        symbol: String,
        exchange: String? = null
    ): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.removeFromWatchlist(uid, symbol, exchange)
            if (response.isSuccessful) {
                emit(ApiResult.Success(Unit))
            } else {
                emit(ApiResult.Error("관심 종목 삭제 실패", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.localizedMessage ?: "네트워크 오류"))
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Repository 제공을 위한 Hilt 모듈 업데이트
 */
/*
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideStockLabRepository(
        api: StockLabApiService
    ): StockLabRepository = StockLabRepository(api)
}
*/