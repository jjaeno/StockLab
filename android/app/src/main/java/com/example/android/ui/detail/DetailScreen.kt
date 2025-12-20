package com.example.android.ui.detail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.android.ui.components.StockChart

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

    private val _news = MutableStateFlow<ApiResult<List<NewsArticle>>>(ApiResult.Loading)
    val news: StateFlow<ApiResult<List<NewsArticle>>> = _news.asStateFlow()

    private val _forecast = MutableStateFlow<ApiResult<GptForecastResponse>>(ApiResult.Loading)
    val forecast: StateFlow<ApiResult<GptForecastResponse>> = _forecast.asStateFlow()

    fun loadNews(symbol: String, displayName: String?) {
        viewModelScope.launch {
            repository.getNaverNews(symbol, displayName).collect { result ->
                _news.value = result
            }
        }
    }

    fun loadForecast(symbol: String, displayName: String?) {
        viewModelScope.launch {
            repository.getGptForecast(symbol, displayName).collect { result ->
                _forecast.value = result
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

    val newsResult by viewModel.news.collectAsState()
    val forecastResult by viewModel.forecast.collectAsState()

    LaunchedEffect(stockDetail.symbol) {
        viewModel.loadStockData(stockDetail.symbol, stockDetail.exchange)
        viewModel.loadNews(stockDetail.symbol, stockDetail.name)
        viewModel.loadForecast(stockDetail.symbol, stockDetail.name)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                quote?.let {
                    CurrentPriceSection(
                        quote = it.quote,
                        currency = it.currency
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                CandleRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = {
                        viewModel.loadCandles(
                            stockDetail.symbol,
                            stockDetail.exchange,
                            it
                        )
                    }
                )

                candles?.let { candleData ->
                    quote?.let { quoteData ->
                        StockChart(
                            candleData = candleData,
                            currency = quoteData.currency,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                quote?.let {
                    TradeButtons(
                        symbol = stockDetail.symbol,
                        currentPrice = it.quote.currentPrice,
                        exchange = stockDetail.exchange,
                        onNavigateToOrder = onNavigateToOrder
                    )
                }

                ForecastSection(
                    result = forecastResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                NewsSection(
                    result = newsResult,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentPriceSection(
    quote: QuoteResponse,
    currency: Currency
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = quote.currentPrice.toFormattedCurrency(currency),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row {
            Text(
                text = quote.change.toFormattedChange(currency),
                color = quote.change.getPriceChangeColor(),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = quote.percentChange.toFormattedPercent(),
                color = quote.change.getPriceChangeColor(),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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

@Composable
private fun PriceInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun CandleRangeSelector(
    selectedRange: CandleRange,
    onRangeSelected: (CandleRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CandleRange.values().forEach {
            FilterChip(
                selected = selectedRange == it,
                onClick = { onRangeSelected(it) },
                label = { Text(it.displayName, fontSize = 12.sp) }
            )
        }
    }
}

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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onNavigateToOrder(symbol, OrderSide.SELL, currentPrice, exchange) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Constants.Colors.BlueDown
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("매도", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onNavigateToOrder(symbol, OrderSide.BUY, currentPrice, exchange) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Constants.Colors.RedUp
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("매수", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ForecastSection(
    result: ApiResult<GptForecastResponse>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "이슈 요약",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(6.dp))

        when (result) {
            is ApiResult.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is ApiResult.Error -> {
                Text(
                    text = "요약을 불러오지 못했습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
            is ApiResult.Success -> {
                Text(
                    text = result.data.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${result.data.direction.toKorean()} 전망 · 신뢰도 ${(result.data.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun NewsSection(
    result: ApiResult<List<NewsArticle>>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(modifier)
    ) {
        Text(
            text = "관련 뉴스",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (result) {
            is ApiResult.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is ApiResult.Error -> {
                Text(
                    text = "뉴스를 불러오지 못했습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
            is ApiResult.Success -> {
                result.data.forEachIndexed { idx, article ->
                    NewsArticleItem(article) {
                        uriHandler.openUri(article.url)
                    }
                    if (idx != result.data.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsArticleItem(
    article: NewsArticle,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = article.title.cleanHtml(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = article.summary.cleanHtml(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${article.source} · ${article.publishedAt}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

private fun String.cleanHtml(): String {
    return try {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (e: Exception) {
        this
    }
}
