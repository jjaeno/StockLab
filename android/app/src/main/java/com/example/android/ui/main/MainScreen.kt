package com.example.android.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.android.data.model.*
import com.example.android.util.*
import com.example.android.viewmodel.AuthViewModel
import com.example.android.viewmodel.MainViewModel
import android.util.Log

/**
 * 메인 화면 (리팩토링 완료)
 *
 * 핵심 개선:
 * - QuoteResult.status 처리 (SUCCESS/FAILED/CACHED)
 * - 실패 시 lastKnownPrice 표시
 * - API 재호출 방지
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
    onStockClick: (StockDetail) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val authResponse by authViewModel.authResponse.collectAsState()

    // StateFlow 구독만
    val watchlist by mainViewModel.watchlist.collectAsState()
    val watchlistQuotes by mainViewModel.watchlistQuotes.collectAsState()
    val searchQuery by mainViewModel.searchQuery.collectAsState()
    val searchResults by mainViewModel.searchResults.collectAsState()
    val allStocks by mainViewModel.allStocks.collectAsState()
    val allStockQuotes by mainViewModel.allStockQuotes.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val errorMessage by mainViewModel.errorMessage.collectAsState()

    // 초기화 (한 번만)
    val hasInitialized = remember { mutableStateOf(false) }
    LaunchedEffect(authResponse?.uid) {
        authResponse?.uid?.let { uid ->
            if (!hasInitialized.value) {
                Log.i("MainScreen", "MainScreen 초기화: UID=$uid")
                mainViewModel.setUid(uid)
                hasInitialized.value = true
            } else {
                Log.d("MainScreen", "⏭ MainScreen 이미 초기화됨, 스킵")
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.showToast(it)
            mainViewModel.clearError()
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(isLoading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StockLab", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { mainViewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { mainViewModel.refresh() },
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 잔고 카드
                item {
                    authResponse?.let { auth ->
                        BalanceCard(
                            displayName = auth.displayName,
                            cashKrw = auth.cashKrw,
                            cashUsd = auth.cashUsd
                        )
                    }
                }
                // 핫스톡 섹션
                item {
                    val hotStocksList by mainViewModel.hotStocks.collectAsState()
                    if (hotStocksList.isNotEmpty()) {
                        HotStocksSection(
                            items = hotStocksList,
                            onItemClick = { item ->
                                val stockType = if (item.symbol.isDomesticStock())
                                    StockType.DOMESTIC else StockType.OVERSEAS
                                val currency = if (stockType == StockType.DOMESTIC)
                                    Currency.KRW else Currency.USD

                                onStockClick(
                                    StockDetail(
                                        symbol = item.symbol,
                                        name = item.displayName,
                                        exchange = null,
                                        stockType = stockType,
                                        currency = currency
                                    )
                                )
                            }
                        )
                    }
                }


                // 검색바
                item {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { mainViewModel.updateSearchQuery(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 검색 중일 때
                if (searchQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "검색 결과",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(
                        items = searchResults,
                        key = { "search_${it.market}_${it.symbol}" }
                    ) { stock ->
                        SearchResultItem(
                            stock = stock,
                            onClick = {
                                val stockType = if (stock.symbol.isDomesticStock())
                                    StockType.DOMESTIC else StockType.OVERSEAS
                                val currency = if (stockType == StockType.DOMESTIC)
                                    Currency.KRW else Currency.USD

                                onStockClick(
                                    StockDetail(
                                        symbol = stock.symbol,
                                        name = stock.name,
                                        exchange = null,
                                        stockType = stockType,
                                        currency = currency
                                    )
                                )
                            },
                            onAddClick = {
                                mainViewModel.addToWatchlist(stock.symbol, null)
                                context.showToast("${stock.name} 관심종목 추가")
                            }
                        )
                    }
                } else {
                    // 관심종목 섹션
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "관심종목",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = "${watchlist.size}개",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (watchlist.isEmpty()) {
                        item {
                            EmptyWatchlistCard()
                        }
                    } else {
                        // ⭐ 관심종목 표시 (QuoteResult 사용)
                        items(
                            items = watchlist,
                            key = { "${it.exchange ?: "NONE"}_${it.symbol}" }
                        ) { item ->
                            val quoteResult = watchlistQuotes[item.symbol]  // ⭐ QuoteResult
                            val stockName = item.getDisplayName()
                            val isInWatchlist = true

                            EnhancedStockItemCard(
                                symbol = item.symbol,
                                name = stockName,
                                quoteResult = quoteResult,  // ⭐ QuoteResult 전달
                                isInWatchlist = isInWatchlist,
                                onClick = {
                                    // 성공한 경우에만 상세화면 이동
                                    quoteResult?.data?.let { quote ->
                                        onStockClick(
                                            StockDetail(
                                                symbol = item.symbol,
                                                name = stockName,
                                                exchange = item.exchange,
                                                stockType = if (item.symbol.isDomesticStock())
                                                    StockType.DOMESTIC else StockType.OVERSEAS,
                                                currency = if (item.symbol.isDomesticStock())
                                                    Currency.KRW else Currency.USD
                                            )
                                        )
                                    }
                                },
                                onToggleWatchlist = {
                                    mainViewModel.removeFromWatchlist(item.symbol, item.exchange)
                                    context.showToast("${stockName} 관심종목 삭제")
                                }
                            )
                        }
                    }

                    // 전체 종목 리스트
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "전체 종목",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(
                        items = allStocks,
                        key = { "${it.market}_${it.symbol}" }
                    ) { stock ->
                        val quoteResult = allStockQuotes[stock.symbol]
                        val isInWatchlist = watchlist.any { it.symbol == stock.symbol }

                        // 전체 종목도 관심종목과 동일한 카드 사용
                        EnhancedStockItemCard(
                            symbol = stock.symbol,
                            name = stock.name,
                            quoteResult = quoteResult,
                            isInWatchlist = isInWatchlist,
                            onClick = {
                                // quoteResult?.data != null 일 때만 상세로 이동
                                val stockType = if (stock.symbol.isDomesticStock())
                                    StockType.DOMESTIC else StockType.OVERSEAS
                                val currency = if (stockType == StockType.DOMESTIC)
                                    Currency.KRW else Currency.USD

                                onStockClick(
                                    StockDetail(
                                        symbol = stock.symbol,
                                        name = stock.name,
                                        exchange = null,
                                        stockType = stockType,
                                        currency = currency
                                    )
                                )
                            },
                            onToggleWatchlist = {
                                if (isInWatchlist) {
                                    mainViewModel.removeFromWatchlist(stock.symbol, null)
                                    context.showToast("${stock.name} 관심종목 삭제")
                                } else {
                                    mainViewModel.addToWatchlist(stock.symbol, null)
                                    context.showToast("${stock.name} 관심종목 추가")
                                }
                            }
                        )
                    }

                }
            }
        }
    }
}

/**
 * ⭐ 개선된 종목 카드 (QuoteResult.status 처리)
 */
@Composable
private fun EnhancedStockItemCard(
    symbol: String,
    name: String,
    quoteResult: QuoteResult?,
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick, enabled = quoteResult?.data != null),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 왼쪽: 종목 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // ⭐ 중간: 가격 정보 (status에 따라 처리)
            when {
                quoteResult == null -> {
                    // 로딩 중
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                quoteResult.status == ResultStatus.SUCCESS -> {
                    // ✅ 성공: 정상 표시
                    val quote = quoteResult.data!!
                    val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD

                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = quote.currentPrice.toFormattedCurrency(currency),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = quote.change.toFormattedChange(currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = quote.change.getPriceChangeColor()
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = quote.percentChange.toFormattedPercent(),
                                style = MaterialTheme.typography.bodySmall,
                                color = quote.change.getPriceChangeColor()
                            )
                        }
                    }
                }
                quoteResult.status == ResultStatus.FAILED -> {
                    // ⚠️ 실패: last-known-good or 플레이스홀더
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (quoteResult.lastKnownPrice != null) {
                            // last-known-good 가격 표시
                            val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD
                            Text(
                                text = quoteResult.lastKnownPrice.toFormattedCurrency(currency),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "마지막 시세",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        } else {
                            // 에러 아이콘 표시
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "조회 실패",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = when (quoteResult.reason) {
                                    "TIMEOUT" -> "시간 초과"
                                    "RATE_LIMIT" -> "제한 초과"
                                    "API_ERROR" -> "API 오류"
                                    else -> "오류"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                quoteResult.status == ResultStatus.CACHED -> {
                    // 📦 캐시된 데이터 (SUCCESS와 동일하게 표시)
                    val quote = quoteResult.data!!
                    val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD

                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = quote.currentPrice.toFormattedCurrency(currency),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Cached,
                                contentDescription = "캐시",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = quote.change.toFormattedChange(currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = quote.change.getPriceChangeColor()
                            )
                        }
                    }
                }
            }

            // 오른쪽: 관심종목 토글
            IconButton(
                onClick = onToggleWatchlist,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isInWatchlist) "관심종목 삭제" else "관심종목 추가",
                    tint = if (isInWatchlist) Color(0xFFFFC107)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * 기존 종목 카드 (전체 종목용 - UnifiedQuoteResponse 사용)
 */
@Composable
private fun EnhancedStockItemCardOld(
    symbol: String,
    name: String,
    quote: UnifiedQuoteResponse?,
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick, enabled = quote != null),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (quote != null) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val quoteData = quote.quote
                    Text(
                        text = quoteData.currentPrice.toFormattedCurrency(quote.currency),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quoteData.change.toFormattedChange(quote.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = quoteData.change.getPriceChangeColor()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = quoteData.percentChange.toFormattedPercent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = quoteData.change.getPriceChangeColor()
                        )
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }

            IconButton(
                onClick = onToggleWatchlist,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isInWatchlist) "관심종목 삭제" else "관심종목 추가",
                    tint = if (isInWatchlist) Color(0xFFFFC107)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    displayName: String,
    cashKrw: Double,
    cashUsd: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "${displayName}님의 잔고",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "KRW",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = cashKrw.toFormattedCurrency(Currency.KRW),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "USD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = cashUsd.toFormattedCurrency(Currency.USD),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}


@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("종목 검색 (예: 삼성전자, AAPL)") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "지우기")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SearchResultItem(
    stock: StockSearchResult,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stock.symbol} · ${stock.market}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "관심종목 추가",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyWatchlistCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "관심종목이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun HotStocksSection(
    items: List<HotStockItem>,
    onItemClick: (HotStockItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI 주목 종목",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "뉴스 이슈 기반",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { item ->
                HotStockItemCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
                if (item != items.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HotStockItemCard(
    item: HotStockItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${item.rank}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = item.symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "주목: ${item.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "리스크: ${item.risk}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}