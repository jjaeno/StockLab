package com.example.android.ui.detail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.ApiResult
import com.example.android.data.model.*
import com.example.android.data.repository.StockLabRepository
import com.example.android.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

import android.text.Html
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import com.example.android.data.model.NewsArticle
import com.example.android.data.model.StockDetail

import com.example.android.ui.components.StockChart

/**
 * 종목 상세 ViewModel
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: StockLabRepository
) : ViewModel() {

    private val _quote = MutableStateFlow<UnifiedQuoteResponse?>(null)
    val quote: StateFlow<UnifiedQuoteResponse?> = _quote.asStateFlow()

    private val _candles = MutableStateFlow<CandleResponse?>(null)
    val candles: StateFlow<CandleResponse?> = _candles.asStateFlow()

    private val _selectedRange = MutableStateFlow(CandleRange.ONE_MONTH)
    val selectedRange: StateFlow<CandleRange> = _selectedRange.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadStockData(symbol: String, exchange: String?) {
        loadQuote(symbol, exchange)
        loadCandles(symbol, exchange, _selectedRange.value)
    }

    private fun loadQuote(symbol: String, exchange: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getQuote(symbol, exchange).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _quote.value = result.data
                        _isLoading.value = false
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value = result.message
                        _isLoading.value = false
                    }
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                }
            }
        }
    }
    /**뉴스 상태*/
    private val _news = MutableStateFlow<ApiResult<List<NewsArticle>>>(ApiResult.Loading)
    val news: StateFlow<ApiResult<List<NewsArticle>>> = _news.asStateFlow()

    /** 뉴스 로드 */
    fun loadNews(symbol: String, displayName: String?) {
        viewModelScope.launch {
            repository.getNaverNews(symbol, displayName).collect { result ->
                _news.value = result
            }
        }
    }

    fun loadCandles(symbol: String, exchange: String?, range: CandleRange) {
        viewModelScope.launch {
            repository.getCandles(symbol, range, exchange).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _candles.value = result.data
                        _selectedRange.value = range
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value = result.message
                    }
                    is ApiResult.Loading -> {}
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * 종목 상세 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    stockDetail: StockDetail,
    navController: androidx.navigation.NavHostController,
    viewModel: DetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToOrder: (String, OrderSide, Double, String?) -> Unit
) {
    val context = LocalContext.current
    val quote by viewModel.quote.collectAsState()
    val candles by viewModel.candles.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // ✅ 뉴스 상태 (ViewModel에 news: StateFlow<ApiResult<List<NewsArticle>>> 가 있어야 함)
    val newsResult by viewModel.news.collectAsState()

    LaunchedEffect(stockDetail.symbol) {
        viewModel.loadStockData(stockDetail.symbol, stockDetail.exchange)

        // ✅ 종목 상세 진입 시 뉴스도 함께 로드
        // - displayName(회사명) 전달 가능하면 필터 정확도/검색 품질이 올라감
        viewModel.loadNews(stockDetail.symbol, stockDetail.name)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.showToast(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stockDetail.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = stockDetail.symbol,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (isLoading && quote == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                quote?.let { quoteData ->
                    CurrentPriceSection(
                        quote = quoteData.quote,
                        currency = quoteData.currency
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                CandleRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { range ->
                        viewModel.loadCandles(stockDetail.symbol, stockDetail.exchange, range)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                candles?.let { candleData ->
                    quote?.let { quoteData ->
                        StockChart(
                            candleData = candleData,
                            currency = quoteData.currency,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                quote?.let { quoteData ->
                    TradeButtons(
                        symbol = stockDetail.symbol,
                        currentPrice = quoteData.quote.currentPrice,
                        exchange = stockDetail.exchange,
                        onNavigateToOrder = onNavigateToOrder
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ✅ 매수/매도 버튼 아래에 뉴스 섹션 추가
                NewsSection(
                    result = newsResult,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

/**
 * 현재가 섹션
 */
@Composable
private fun CurrentPriceSection(
    quote: QuoteResponse,
    currency: Currency
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = quote.currentPrice.toFormattedCurrency(currency),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quote.change.toFormattedChange(currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = quote.change.getPriceChangeColor()
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = quote.percentChange.toFormattedPercent(),
                    style = MaterialTheme.typography.titleLarge,
                    color = quote.change.getPriceChangeColor()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceInfoItem("고가", quote.high.toFormattedCurrency(currency))
                PriceInfoItem("저가", quote.low.toFormattedCurrency(currency))
                PriceInfoItem("시가", quote.open.toFormattedCurrency(currency))
            }
        }
    }
}

@Composable
private fun PriceInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * 캔들 기간 선택기
 */
@Composable
private fun CandleRangeSelector(
    selectedRange: CandleRange,
    onRangeSelected: (CandleRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CandleRange.values().forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.displayName) }
            )
        }
    }
}

/**
 * Compose 라인 차트 (애니메이션 포함)
 */
@Composable
private fun LineChartCompose(
    candleData: CandleResponse,
    modifier: Modifier = Modifier
) {
    val closeValues = candleData.close
    if (closeValues.isEmpty()) return

    val minValue = closeValues.minOrNull() ?: 0.0
    val maxValue = closeValues.maxOrNull() ?: 0.0
    val valueRange = maxValue - minValue

    // 애니메이션
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(candleData) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = EaseInOut)
        )
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val progress = animatedProgress.value

                if (closeValues.size < 2) return@Canvas

                val step = width / (closeValues.size - 1)

                // 그리드 라인
                val gridColor = Color.Gray.copy(alpha = 0.2f)
                for (i in 0..4) {
                    val y = height * i / 4
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // 라인 차트 경로
                val path = Path()
                val visibleCount = (closeValues.size * progress).toInt().coerceAtLeast(1)

                closeValues.take(visibleCount).forEachIndexed { index, value ->
                    val x = index * step
                    val normalizedValue = if (valueRange > 0) {
                        ((value - minValue) / valueRange).toFloat()
                    } else {
                        0.5f
                    }
                    val y = height - (normalizedValue * height)

                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // 라인 그리기
                drawPath(
                    path = path,
                    color = Color(0xFF3B82F6),
                    style = Stroke(width = 3f)
                )

                // 시작점/끝점 원
                if (visibleCount > 0 && valueRange > 0) {
                    val firstValue = closeValues[0]
                    val firstY = height - (((firstValue - minValue) / valueRange).toFloat() * height)
                    drawCircle(
                        color = Color(0xFF3B82F6),
                        radius = 6f,
                        center = Offset(0f, firstY)
                    )
                }

                if (visibleCount == closeValues.size && valueRange > 0) {
                    val lastValue = closeValues.last()
                    val lastX = (closeValues.size - 1) * step
                    val lastY = height - (((lastValue - minValue) / valueRange).toFloat() * height)
                    drawCircle(
                        color = Color(0xFF3B82F6),
                        radius = 6f,
                        center = Offset(lastX, lastY)
                    )
                }
            }

            // 가격 범위 표시
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "%.0f".format(maxValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "%.0f".format(minValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 매수/매도 버튼
 */
@Composable
private fun TradeButtons(
    symbol: String,
    currentPrice: Double,
    exchange: String?,
    onNavigateToOrder: (String, OrderSide, Double, String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onNavigateToOrder(symbol, OrderSide.SELL, currentPrice, exchange) },
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Constants.Colors.BlueDown),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "매도",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Button(
            onClick = { onNavigateToOrder(symbol, OrderSide.BUY, currentPrice, exchange) },
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Constants.Colors.RedUp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "매수",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/**
 *  뉴스 섹션 (제목 + 요약)
 *
 * ViewModel.news 가 ApiResult<List<NewsArticle>> 형태라고 가정.
 * - Success: 기사 리스트 카드 출력
 * - Loading: 로딩 인디케이터
 * - Error: 간단 에러 표시
 */
@Composable
private fun NewsSection(
    result: ApiResult<List<NewsArticle>>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(modifier),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "관련 뉴스",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (result) {
                is ApiResult.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ApiResult.Error -> {
                    Text(
                        text = "뉴스를 불러오지 못했습니다.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is ApiResult.Success -> {
                    val articles = result.data
                    if (articles.isEmpty()) {
                        Text(
                            text = "관련 뉴스가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        articles.forEachIndexed { idx, article ->
                            NewsArticleCard(
                                article = article,
                                onClick = {
                                    // URL이 비어있지 않을 때만 오픈
                                    if (article.url.isNotBlank()) {
                                        uriHandler.openUri(article.url)
                                    }
                                }
                            )
                            if (idx != articles.lastIndex) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsArticleCard(
    article: NewsArticle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = article.title.cleanHtml(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = article.summary.cleanHtml(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = article.source.ifBlank { "NAVER" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = article.publishedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * title/summary에 HTML 엔티티나 태그가 섞일 때 대비용
 */
private fun String.cleanHtml(): String {
    return try {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (e: Exception) {
        this
    }
}