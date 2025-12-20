package com.example.android.data.api

import com.example.android.data.model.*
import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.GET
import retrofit2.http.Query
/**
 * StockLab Retrofit API 인터페이스
 * 백엔드 API 엔드포인트와 1:1 매핑
 */

interface ApiService {

    // ==========================================
    // 인증 API (UID 불필요)
    // ==========================================

    /**
     * Firebase ID Token 검증
     * POST /auth/verify
     */
    @POST("auth/verify")
    suspend fun verifyToken(
        @Body request: VerifyTokenRequest
    ): Response<ApiResponse<AuthResponse>>

    // ==========================================
    // 시세 조회 API (UID 불필요)
    // ==========================================

    /**
     * 단일 종목 시세 조회 (통합)
     * GET /quotes/{symbol}
     * @param symbol 종목 코드 또는 심볼 (예: "005930", "AAPL")
     * @param exchange 거래소 (선택사항, 예: "NASDAQ")
     */
    @GET("quotes/{symbol}")
    suspend fun getQuote(
        @Path("symbol") symbol: String,
        @Query("exchange") exchange: String? = null
    ): Response<ApiResponse<UnifiedQuoteResponse>>

    /**
     * 다중 종목 시세 조회
     * GET /quotes?symbols=005930,AAPL,TSLA
     * @param symbols 쉼표로 구분된 종목 리스트
     */
    @GET("quotes")
    suspend fun getMultipleQuotes(
        @Query("symbols") symbols: String
    ): Response<ApiResponse<List<UnifiedQuoteResponse>>>

    /**
     * 캔들 차트 데이터 조회
     * GET /quotes/{symbol}/candles
     * @param symbol 종목 코드
     * @param range 조회 기간 (1D, 1W, 1M, 3M, 1Y)
     * @param exchange 거래소 (선택사항)
     */
    @GET("quotes/{symbol}/candles")
    suspend fun getCandles(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("exchange") exchange: String? = null
    ): Response<ApiResponse<CandleResponse>>

    /**
     * 다중 종목 시세 일괄 조회
     * POST /quotes/batch
     */
    @POST("quotes/batch")
    suspend fun getBatchQuotes(
        @Body request: BatchQuoteRequest
    ): Response<ApiResponse<BatchQuoteResponse>>
    // ==========================================
    // 주문 API (UID 필요 - Header)
    // ==========================================

    /**
     * 주문 생성 (매수/매도)
     * POST /orders
     * Header: X-User-UID
     */
    @POST("orders")
    suspend fun createOrder(
        @Header("X-User-UID") uid: String,
        @Body request: OrderRequest
    ): Response<ApiResponse<OrderResponse>>

    /**
     * 사용자 주문 내역 조회
     * GET /orders/{uid}
     */
    @GET("orders/{uid}")
    suspend fun getUserOrders(
        @Path("uid") uid: String
    ): Response<ApiResponse<List<OrderEntity>>>

    // ==========================================
    // 입출금 API (UID 필요 - Header)
    // ==========================================

    /**
     * 입금 (통화별)
     * POST /account/deposit
     * Header: X-User-UID
     */
    @POST("account/deposit")
    suspend fun deposit(
        @Header("X-User-UID") uid: String,
        @Body request: CashRequest
    ): Response<ApiResponse<CashResponse>>

    /**
     * 출금 (통화별)
     * POST /account/withdraw
     * Header: X-User-UID
     */
    @POST("account/withdraw")
    suspend fun withdraw(
        @Header("X-User-UID") uid: String,
        @Body request: CashRequest
    ): Response<ApiResponse<CashResponse>>

    // ==========================================
    // 포트폴리오 API (UID 필요 - Header)
    // ==========================================

    /**
     * 포트폴리오 조회
     * GET /portfolio
     * Header: X-User-UID
     */
    @GET("portfolio")
    suspend fun getPortfolio(
        @Header("X-User-UID") uid: String
    ): Response<ApiResponse<PortfolioResponse>>

    // ==========================================
    // 관심종목 API (UID 필요 - Header)
    // ==========================================

    /**
     * 관심 종목 목록 조회
     * GET /watchlist
     * Header: X-User-UID
     */
    @GET("watchlist")
    suspend fun getWatchlist(
        @Header("X-User-UID") uid: String
    ): Response<ApiResponse<WatchlistResponse>>

    /**
     * 관심 종목 추가
     * POST /watchlist
     * Header: X-User-UID
     */
    @POST("watchlist")
    suspend fun addToWatchlist(
        @Header("X-User-UID") uid: String,
        @Body request: AddWatchlistRequest
    ): Response<ApiResponse<WatchlistItem>>

    /**
     * 관심 종목 삭제
     * DELETE /watchlist/{symbol}
     * Header: X-User-UID
     * @param symbol 종목 코드
     * @param exchange 거래소 (선택사항)
     */
    @DELETE("watchlist/{symbol}")
    suspend fun removeFromWatchlist(
        @Header("X-User-UID") uid: String,
        @Path("symbol") symbol: String,
        @Query("exchange") exchange: String? = null
    ): Response<ApiResponse<Void>>
    /** 네이버 뉴스 검색 */
    @GET("news/naver")
    suspend fun getNaverNews(
        @Query("symbol") symbol: String?,
        @Query("displayName") displayName: String?,
        @Query("limit") limit: Int = 5
    ): Response<List<NewsArticle>>
}

/**
 * API 호출 결과 래퍼
 * 성공/실패를 명확하게 처리하기 위한 sealed class
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

/**
 * Retrofit Response를 ApiResult로 변환하는 확장 함수
 */
fun <T> Response<ApiResponse<T>>.toApiResult(): ApiResult<T> {
    return try {
        if (isSuccessful) {
            val body = body()
            if (body != null && body.success && body.data != null) {
                ApiResult.Success(body.data)
            } else {
                ApiResult.Error(
                    message = body?.message ?: "알 수 없는 오류가 발생했습니다",
                    code = code()
                )
            }
        } else {
            ApiResult.Error(
                message = message() ?: "서버 오류: ${code()}",
                code = code()
            )
        }
    } catch (e: Exception) {
        ApiResult.Error(
            message = e.localizedMessage ?: "네트워크 오류가 발생했습니다"
        )
    }
}