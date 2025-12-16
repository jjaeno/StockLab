package com.example.android.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.example.android.data.model.Currency
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 확장 함수 및 유틸리티 모음
 */

// ==========================================
// Context 확장 함수
// ==========================================

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// ==========================================
// 숫자 포맷팅 확장 함수
// ==========================================

fun Double.toFormattedCurrency(currency: Currency): String {
    val format = when (currency) {
        Currency.KRW -> {
            NumberFormat.getNumberInstance(Locale.KOREA).apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }
        }
        Currency.USD -> {
            NumberFormat.getNumberInstance(Locale.US).apply {
                maximumFractionDigits = 2
                minimumFractionDigits = 2
            }
        }
    }
    return "${currency.symbol}${format.format(this)}"
}

fun Double.toFormattedNumber(decimalPlaces: Int = 2): String {
    val format = DecimalFormat("#,##0.${"0".repeat(decimalPlaces)}")
    return format.format(this)
}

fun Long.toFormattedNumber(): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(this)
}

fun Double.toFormattedPercent(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign%.2f%%".format(this)
}

fun Double.toFormattedChange(currency: Currency): String {
    val sign = if (this >= 0) "+" else ""
    val formatted = when (currency) {
        Currency.KRW -> toFormattedNumber(0)
        Currency.USD -> toFormattedNumber(2)
    }
    return "$sign$formatted"
}

// ==========================================
// 색상 유틸리티
// ==========================================

fun Double.getPriceChangeColor(): Color {
    return when {
        this > 0 -> Color(0xFFFF3B30)
        this < 0 -> Color(0xFF007AFF)
        else -> Color.Gray
    }
}

fun Double.getProfitLossBackgroundColor(): Color {
    return when {
        this > 0 -> Color(0x1AFF3B30)
        this < 0 -> Color(0x1A007AFF)
        else -> Color(0x1A8E8E93)
    }
}

// ==========================================
// 날짜 포맷팅
// ==========================================

fun String.toFormattedDateTime(): String {
    return try {
        val localDateTime = LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
    } catch (e: Exception) {
        this
    }
}

fun Long.toFormattedDate(): String {
    val date = Date(this * 1000)
    val format = java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    return format.format(date)
}

// ==========================================
// 종목 타입 판별
// ==========================================

fun String.isDomesticStock(): Boolean {
    return matches(Regex("\\d{6}"))
}

fun String.isOverseasStock(): Boolean {
    return matches(Regex("[A-Z]+"))
}

// ==========================================
// 검증 유틸리티
// ==========================================

fun String.isValidAmount(): Boolean {
    return try {
        val amount = toDoubleOrNull()
        amount != null && amount > 0
    } catch (e: Exception) {
        false
    }
}

fun String.isValidQuantity(): Boolean {
    return try {
        val quantity = toDoubleOrNull()
        quantity != null && quantity > 0
    } catch (e: Exception) {
        false
    }
}

// ==========================================
// 상수 정의
// ==========================================

object Constants {
    object Colors {
        val RedUp = Color(0xFFFF3B30)
        val BlueDown = Color(0xFF007AFF)
        val GrayNeutral = Color.Gray
        val ProfitGreen = Color(0xFF34C759)
        val LossRed = Color(0xFFFF3B30)
    }

    const val DEFAULT_QUANTITY = 1.0
    const val MIN_ORDER_AMOUNT_KRW = 1000.0
    const val MIN_ORDER_AMOUNT_USD = 1.0
}

// ==========================================
// 주식 데이터 클래스 (로컬용)
// ==========================================

object StockData {
    data class Stock(
        val symbol: String,
        val name: String,
        val market: String = "KOSPI"
    )

    val domesticStocks = listOf(
        Stock("005930", "삼성전자"),
        Stock("000660", "SK하이닉스"),
        Stock("035420", "NAVER"),
        Stock("035720", "카카오"),
        Stock("051910", "LG화학"),
        Stock("006400", "삼성SDI"),
        Stock("207940", "삼성바이오로직스"),
        Stock("005380", "현대차"),
        Stock("000270", "기아"),
        Stock("068270", "셀트리온"),
//        Stock("012330", "현대모비스"),
//        Stock("105560", "KB금융"),
//        Stock("055550", "신한지주"),
//        Stock("017670", "SK텔레콤"),
//        Stock("066570", "LG전자"),
//        Stock("015760", "한국전력"),
//        Stock("009150", "삼성전기"),
//        Stock("003550", "LG"),
//        Stock("096770", "SK이노베이션"),
//        Stock("034020", "두산에너빌리티")
    )

    val overseasStocks = listOf(
        Stock("AAPL", "Apple", "NASDAQ"),
        Stock("MSFT", "Microsoft", "NASDAQ"),
        Stock("GOOGL", "Alphabet", "NASDAQ"),
        Stock("AMZN", "Amazon", "NASDAQ"),
        Stock("TSLA", "Tesla", "NASDAQ"),
        Stock("NVDA", "NVIDIA", "NASDAQ"),
        Stock("META", "Meta", "NASDAQ"),
        Stock("NFLX", "Netflix", "NASDAQ"),
        Stock("AMD", "AMD", "NASDAQ"),
        Stock("INTC", "Intel", "NASDAQ"),
//        Stock("QCOM", "Qualcomm", "NASDAQ"),
//        Stock("AVGO", "Broadcom", "NASDAQ"),
//        Stock("CSCO", "Cisco", "NASDAQ"),
//        Stock("ORCL", "Oracle", "NYSE"),
//        Stock("IBM", "IBM", "NYSE"),
//        Stock("JPM", "JPMorgan", "NYSE"),
//        Stock("BAC", "Bank of America", "NYSE"),
//        Stock("WMT", "Walmart", "NYSE"),
//        Stock("DIS", "Disney", "NYSE"),
//        Stock("V", "Visa", "NYSE")
    )

    // 심볼 → 이름 매핑 맵
    private val symbolToNameMap: Map<String, String> by lazy {
        (domesticStocks + overseasStocks).associate { it.symbol to it.name }
    }

    /**
     * 심볼로 종목 이름 가져오기
     */
    fun getStockName(symbol: String): String {
        return symbolToNameMap[symbol] ?: symbol
    }

    /**
     * 종목 검색 (이름 또는 심볼)
     */
    fun searchStocks(query: String): List<Stock> {
        val allStocks = domesticStocks + overseasStocks
        return if (query.isEmpty()) {
            allStocks
        } else {
            allStocks.filter {
                it.symbol.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true)
            }
        }
    }

    /**
     * 전체 종목 리스트 (국내 + 해외)
     */
    fun getAllStocks(): List<Stock> {
        return domesticStocks + overseasStocks
    }
}