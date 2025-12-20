package com.example.android.data.model

import android.os.Parcelable
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize


/**
 * StockLab 데이터 모델
 * 백엔드 API 응답 구조와 동일하게 매핑
 */

// ==========================================
// 공통 응답 래퍼
// ==========================================

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T?,
    @SerializedName("message") val message: String?,
    @SerializedName("timestamp") val timestamp: String
)

// ==========================================
// 인증 관련 모델
// ==========================================

data class VerifyTokenRequest(
    @SerializedName("idToken") val idToken: String
)

data class AuthResponse(
    @SerializedName("uid") val uid: String,
    @SerializedName("email") val email: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("cashKrw") val cashKrw: Double,
    @SerializedName("cashUsd") val cashUsd: Double,
    @SerializedName("newUser") val newUser: Boolean
)

// ==========================================
// 시세 관련 모델
// ==========================================

@Parcelize
data class QuoteResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("currentPrice") val currentPrice: Double,
    @SerializedName("change") val change: Double,
    @SerializedName("percentChange") val percentChange: Double,
    @SerializedName("high") val high: Double,
    @SerializedName("low") val low: Double,
    @SerializedName("open") val open: Double,
    @SerializedName("previousClose") val previousClose: Double,
    @SerializedName("timestamp") val timestamp: Long
) : Parcelable {
    // 가격이 상승했는지 여부
    val isPositive: Boolean get() = change >= 0
}

data class UnifiedQuoteResponse(
    @SerializedName("quote") val quote: QuoteResponse,
    @SerializedName("stockType") val stockType: StockType,
    @SerializedName("market") val market: String,
    @SerializedName("currency") val currency: Currency
)

data class CandleResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("range") val range: String,
    @SerializedName("timestamps") val timestamps: List<Long>,
    @SerializedName("open") val open: List<Double>,
    @SerializedName("high") val high: List<Double>,
    @SerializedName("low") val low: List<Double>,
    @SerializedName("close") val close: List<Double>,
    @SerializedName("volume") val volume: List<Long>
)

enum class CandleRange(val apiValue: String, val displayName: String) {
    ONE_DAY("1D", "일"),
    ONE_WEEK("1W", "주"),
    ONE_MONTH("1M", "월"),
    THREE_MONTHS("3M", "3개월"),
    ONE_YEAR("1Y", "년")
}

enum class StockType {
    @SerializedName("DOMESTIC") DOMESTIC,
    @SerializedName("OVERSEAS") OVERSEAS
}
// Models.kt에 추가

// 다중 조회 요청
data class BatchQuoteRequest(
    @SerializedName("symbols") val symbols: List<String>
)

// 다중 조회 응답
data class BatchQuoteResponse(
    @SerializedName("results") val results: List<QuoteResult>,
    @SerializedName("totalRequested") val totalRequested: Int,
    @SerializedName("successCount") val successCount: Int,
    @SerializedName("failedCount") val failedCount: Int,
    @SerializedName("cachedCount") val cachedCount: Int,
    @SerializedName("timestamp") val timestamp: Long
)

// 개별 종목 결과
data class QuoteResult(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("status") val status: ResultStatus,
    @SerializedName("data") val data: QuoteResponse?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("lastKnownPrice") val lastKnownPrice: Double?,
    @SerializedName("source") val source: String,
    @SerializedName("cached") val cached: Boolean,
    @SerializedName("fetchedAt") val fetchedAt: Long
)

enum class ResultStatus {
    @SerializedName("SUCCESS") SUCCESS,
    @SerializedName("FAILED") FAILED,
    @SerializedName("CACHED") CACHED
}

// ==========================================
// 주문 관련 모델
// ==========================================

data class OrderRequest(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("side") val side: OrderSide,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("exchange") val exchange: String? = null
)

data class OrderResponse(
    @SerializedName("orderId") val orderId: Long,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("side") val side: OrderSide,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("price") val price: Double,
    @SerializedName("totalAmount") val totalAmount: Double,
    @SerializedName("currency") val currency: Currency,
    @SerializedName("createdAt") val createdAt: String
)

data class OrderEntity(
    @SerializedName("id") val id: Long,
    @SerializedName("uid") val uid: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("side") val side: OrderSide,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("price") val price: Double,
    @SerializedName("createdAt") val createdAt: String
)

enum class OrderSide(val displayName: String, val koreanName: String) {
    @SerializedName("BUY") BUY("Buy", "매수"),
    @SerializedName("SELL") SELL("Sell", "매도")
}

// ==========================================
// 입출금 관련 모델
// ==========================================

data class CashRequest(
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: Currency
)

data class CashResponse(
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: Currency,
    @SerializedName("transactionType") val transactionType: TransactionType,
    @SerializedName("balanceAfter") val balanceAfter: Double,
    @SerializedName("timestamp") val timestamp: String
)

data class CashLedgerEntity(
    @SerializedName("id") val id: Long,
    @SerializedName("uid") val uid: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: Currency,
    @SerializedName("transactionType") val transactionType: TransactionType,
    @SerializedName("symbol") val symbol: String?,
    @SerializedName("createdAt") val createdAt: String
)

enum class Currency(val symbol: String, val displayName: String) {
    @SerializedName("KRW") KRW("₩", "원"),
    @SerializedName("USD") USD("$", "달러")
}

enum class TransactionType(val displayName: String, val koreanName: String) {
    @SerializedName("DEPOSIT") DEPOSIT("Deposit", "입금"),
    @SerializedName("WITHDRAW") WITHDRAW("Withdraw", "출금"),
    @SerializedName("BUY") BUY("Buy", "매수"),
    @SerializedName("SELL") SELL("Sell", "매도")
}

// ==========================================
// 포트폴리오 관련 모델
// ==========================================

data class PositionView(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("name") val name: String? = null, // 종목명 추가
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("avgPrice") val avgPrice: Double,
    @SerializedName("currentPrice") val currentPrice: Double,
    @SerializedName("marketValue") val marketValue: Double,
    @SerializedName("profitLoss") val profitLoss: Double,
    @SerializedName("currency") val currency: Currency
) {
    val profitLossPercent: Double get() =
        if (avgPrice != 0.0) (profitLoss / (avgPrice * quantity)) * 100 else 0.0

    val isProfit: Boolean get() = profitLoss >= 0

    // 종목명 가져오기 (백엔드에서 name이 없으면 로컬에서 찾기)
    fun getDisplayName(): String {
        return name ?: com.example.android.util.StockData.getStockName(symbol)
    }
}


data class PortfolioResponse(
    @SerializedName("uid") val uid: String,
    @SerializedName("cashKrw") val cashKrw: Double,
    @SerializedName("cashUsd") val cashUsd: Double,
    @SerializedName("positions") val positions: List<PositionView>,
    @SerializedName("totalMarketValueKrw") val totalMarketValueKrw: Double,
    @SerializedName("totalMarketValueUsd") val totalMarketValueUsd: Double
) {
    // 총 자산 (현금 + 평가금액)
    val totalAssetsKrw: Double get() = cashKrw + totalMarketValueKrw
    val totalAssetsUsd: Double get() = cashUsd + totalMarketValueUsd
}

// ==========================================
// 관심종목 관련 모델
// ==========================================

// 관심종목 관련 모델 (수정)
data class WatchlistItem(
    @SerializedName("id") val id: Long,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("name") val name: String? = null, // 종목명 추가
    @SerializedName("exchange") val exchange: String?,
    @SerializedName("createdAt") val createdAt: String
) {
    // 종목명 가져오기
    fun getDisplayName(): String {
        return name ?: com.example.android.util.StockData.getStockName(symbol)
    }
}


data class WatchlistResponse(
    @SerializedName("uid") val uid: String,
    @SerializedName("items") val items: List<WatchlistItem>
)

data class AddWatchlistRequest(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("exchange") val exchange: String? = null
)

// ==========================================
// UI 상태 모델
// ==========================================

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val code: Int? = null) : UiState<Nothing>()
}

// 주식 검색 결과 (로컬 모델)
@Parcelize
data class StockSearchResult(
    val symbol: String,
    val name: String,
    val market: String,
    val exchange: String? = null,
    val currency: Currency = Currency.KRW
) : Parcelable


// 종목 상세 화면에 전달할 데이터
@Parcelize
data class StockDetail(
    val symbol: String,
    val name: String,
    val exchange: String?,
    val stockType: StockType,
    val currency: Currency
) : Parcelable

data class NewsArticle(
    val title: String,
    val summary: String,
    val url: String,
    val publishedAt: String,
    val source: String
)

// AI 예측 요청/응답
data class GptForecastRequest(
    val symbol: String,
    val displayName: String?,
    val limit: Int = 5,
    val query: String? = null,
    val model: String? = null
)

data class UsedArticle(
    val title: String,
    val url: String,
    val publishedAt: String
)

enum class ForecastDirection {
    UP, DOWN, NEUTRAL, UNCERTAIN
}
fun ForecastDirection.toKorean(): String = when (this) {
    ForecastDirection.UP -> "상승"
    ForecastDirection.DOWN -> "하락"
    ForecastDirection.NEUTRAL -> "중립"
    ForecastDirection.UNCERTAIN -> "불확실"
}


data class GptForecastResponse(
    val summary: String,
    val direction: ForecastDirection,
    val confidence: Double,
    val risks: String?,
    val usedArticles: List<UsedArticle>,
    val model: String
)
