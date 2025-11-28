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

/**
 * Toast 메시지 표시 (짧게)
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Toast 메시지 표시 (길게)
 */
fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// ==========================================
// 숫자 포맷팅 확장 함수
// ==========================================

/**
 * 금액 포맷팅 (통화별)
 * 예: 1234567.89 -> "₩1,234,568" (KRW) 또는 "$1,234.57" (USD)
 */
fun Double.toFormattedCurrency(currency: Currency): String {
    val format = when (currency) {
        Currency.KRW -> {
            // KRW는 소수점 없이 천단위 구분
            NumberFormat.getNumberInstance(Locale.KOREA).apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }
        }
        Currency.USD -> {
            // USD는 소수점 2자리
            NumberFormat.getNumberInstance(Locale.US).apply {
                maximumFractionDigits = 2
                minimumFractionDigits = 2
            }
        }
    }

    return "${currency.symbol}${format.format(this)}"
}

/**
 * 간단한 숫자 포맷팅 (천단위 구분)
 * 예: 1234567.89 -> "1,234,567.89"
 */
fun Double.toFormattedNumber(decimalPlaces: Int = 2): String {
    val format = DecimalFormat("#,##0.${"0".repeat(decimalPlaces)}")
    return format.format(this)
}

/**
 * 정수 포맷팅 (천단위 구분)
 * 예: 1234567 -> "1,234,567"
 */
fun Long.toFormattedNumber(): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(this)
}

/**
 * 퍼센트 포맷팅
 * 예: 0.1234 -> "+12.34%" 또는 -0.05 -> "-5.00%"
 */
fun Double.toFormattedPercent(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign%.2f%%".format(this)
}

/**
 * 변동액 포맷팅 (부호 포함)
 * 예: 1234.56 -> "+1,234.56" 또는 -789.12 -> "-789.12"
 */
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

/**
 * 가격 변동에 따른 색상 반환
 * 양수: 빨간색 (상승)
 * 음수: 파란색 (하락)
 * 0: 회색 (보합)
 */
fun Double.getPriceChangeColor(): Color {
    return when {
        this > 0 -> Color(0xFFFF3B30)  // 빨간색
        this < 0 -> Color(0xFF007AFF)  // 파란색
        else -> Color.Gray              // 회색
    }
}

/**
 * 수익/손실에 따른 배경색 반환 (연한 색)
 */
fun Double.getProfitLossBackgroundColor(): Color {
    return when {
        this > 0 -> Color(0x1AFF3B30)  // 연한 빨간색
        this < 0 -> Color(0x1A007AFF)  // 연한 파란색
        else -> Color(0x1A8E8E93)      // 연한 회색
    }
}

// ==========================================
// 날짜 포맷팅
// ==========================================

/**
 * ISO 8601 날짜 문자열을 포맷팅
 * "2025-01-15T10:30:00" -> "2025.01.15 10:30"
 */
fun String.toFormattedDateTime(): String {
    return try {
        val localDateTime = LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
    } catch (e: Exception) {
        this
    }
}

/**
 * Unix timestamp를 포맷팅
 * 1736899200 -> "2025.01.15"
 */
fun Long.toFormattedDate(): String {
    val date = Date(this * 1000)
    val format = java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    return format.format(date)
}

// ==========================================
// 종목 타입 판별
// ==========================================

/**
 * 종목 코드가 국내주식인지 확인
 * 6자리 숫자면 국내주식으로 간주
 */
fun String.isDomesticStock(): Boolean {
    return matches(Regex("\\d{6}"))
}

/**
 * 종목 코드가 해외주식인지 확인
 * 영문 알파벳으로 구성되면 해외주식으로 간주
 */
fun String.isOverseasStock(): Boolean {
    return matches(Regex("[A-Z]+"))
}

// ==========================================
// 검증 유틸리티
// ==========================================

/**
 * 입력값이 유효한 금액인지 확인
 */
fun String.isValidAmount(): Boolean {
    return try {
        val amount = toDoubleOrNull()
        amount != null && amount > 0
    } catch (e: Exception) {
        false
    }
}

/**
 * 입력값이 유효한 수량인지 확인
 */
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
    // 색상 상수
    object Colors {
        val RedUp = Color(0xFFFF3B30)     // 상승 빨간색
        val BlueDown = Color(0xFF007AFF)   // 하락 파란색
        val GrayNeutral = Color.Gray       // 보합 회색

        val ProfitGreen = Color(0xFF34C759)   // 수익 초록색
        val LossRed = Color(0xFFFF3B30)       // 손실 빨간색
    }

    // 기본값
    const val DEFAULT_QUANTITY = 1.0
    const val MIN_ORDER_AMOUNT_KRW = 1000.0
    const val MIN_ORDER_AMOUNT_USD = 1.0
}

// ==========================================
// 주식 데이터 클래스 (로컬용)
// ==========================================

/**
 * 주요 국내 주식 목록 (샘플)
 */
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
        Stock("068270", "셀트리온")
    )

    val overseasStocks = listOf(
        Stock("AAPL", "Apple", "NASDAQ"),
        Stock("MSFT", "Microsoft", "NASDAQ"),
        Stock("GOOGL", "Alphabet", "NASDAQ"),
        Stock("AMZN", "Amazon", "NASDAQ"),
        Stock("TSLA", "Tesla", "NASDAQ"),
        Stock("NVDA", "NVIDIA", "NASDAQ"),
        Stock("META", "Meta", "NASDAQ"),
        Stock("NFLX", "Netflix", "NASDAQ")
    )

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
}