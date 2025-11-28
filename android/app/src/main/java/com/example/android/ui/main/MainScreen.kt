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

/**
 * 메인 화면
 * - 잔고 표시
 * - 관심종목 리스트
 * - 검색 기능
 * - 스와이프 새로고침
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
    val watchlist by mainViewModel.watchlist.collectAsState()
    val watchlistQuotes by mainViewModel.watchlistQuotes.collectAsState()
    val searchQuery by mainViewModel.searchQuery.collectAsState()
    val searchResults by mainViewModel.searchResults.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val errorMessage by mainViewModel.errorMessage.collectAsState()

    // uid 들어오면 ViewModel에 전달해서 관심종목 로드
    LaunchedEffect(authResponse?.uid) {
        authResponse?.uid?.let { uid ->
            mainViewModel.setUid(uid)
        }
    }

    // 에러 메시지 표시
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.showToast(it)
            mainViewModel.clearError()
        }
    }

    // Swipe Refresh State
    val swipeRefreshState = rememberSwipeRefreshState(isLoading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StockLab", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { mainViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 잔고 표시 섹션
                authResponse?.let { auth ->
                    BalanceCard(
                        cashKrw = auth.cashKrw,
                        cashUsd = auth.cashUsd,
                        displayName = auth.displayName
                    )
                }

                // 검색 바
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { mainViewModel.updateSearchQuery(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 검색 중이면 검색 결과 표시
                if (searchQuery.isNotEmpty()) {
                    SearchResultsList(
                        results = searchResults,
                        onStockClick = { stock ->
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
                        onAddToWatchlist = { stock ->
                            mainViewModel.addToWatchlist(stock.symbol, null)
                            context.showToast("${stock.name} 관심종목 추가")
                        }
                    )
                } else {
                    // 관심종목 리스트
                    WatchlistSection(
                        watchlist = watchlist,
                        quotes = watchlistQuotes,
                        onStockClick = { item, quote ->
                            onStockClick(
                                StockDetail(
                                    symbol = item.symbol,
                                    name = item.symbol, // 이름은 실제로는 별도 API나 로컬 DB에서 가져와야 함
                                    exchange = item.exchange,
                                    stockType = quote.stockType,
                                    currency = quote.currency
                                )
                            )
                        },
                        onRemoveClick = { item ->
                            mainViewModel.removeFromWatchlist(item.symbol, item.exchange)
                            context.showToast("${item.symbol} 관심종목 삭제")
                        }
                    )
                }
            }
        }
    }
}

/**
 * 잔고 카드
 */
@Composable
private fun BalanceCard(
    cashKrw: Double,
    cashUsd: Double,
    displayName: String
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
                style = MaterialTheme.typography.titleMedium,
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

/**
 * 검색 바
 */
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

/**
 * 검색 결과 리스트
 */
@Composable
private fun SearchResultsList(
    results: List<StockSearchResult>,
    onStockClick: (StockSearchResult) -> Unit,
    onAddToWatchlist: (StockSearchResult) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { stock ->
            SearchResultItem(
                stock = stock,
                onClick = { onStockClick(stock) },
                onAddClick = { onAddToWatchlist(stock) }
            )
        }
    }
}

/**
 * 검색 결과 아이템
 */
@Composable
private fun SearchResultItem(
    stock: StockSearchResult,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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

/**
 * 관심종목 섹션
 */
@Composable
private fun WatchlistSection(
    watchlist: List<WatchlistItem>,
    quotes: Map<String, UnifiedQuoteResponse>,
    onStockClick: (WatchlistItem, UnifiedQuoteResponse) -> Unit,
    onRemoveClick: (WatchlistItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 섹션 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "관심종목",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "${watchlist.size}개",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        if (watchlist.isEmpty()) {
            // 빈 상태
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "관심종목이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "검색을 통해 종목을 추가해보세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            // 관심종목 리스트
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchlist) { item ->
                    val quote = quotes[item.symbol]
                    WatchlistItemCard(
                        item = item,
                        quote = quote,
                        onClick = {
                            quote?.let { onStockClick(item, it) }
                        },
                        onRemoveClick = { onRemoveClick(item) }
                    )
                }
            }
        }
    }
}

/**
 * 관심종목 아이템 카드
 */
@Composable
private fun WatchlistItemCard(
    item: WatchlistItem,
    quote: UnifiedQuoteResponse?,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = quote != null),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    text = item.symbol,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (quote != null) {
                    val quoteData = quote.quote
                    Text(
                        text = quoteData.currentPrice.toFormattedCurrency(quote.currency),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quoteData.change.toFormattedChange(quote.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = quoteData.change.getPriceChangeColor()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = quoteData.percentChange.toFormattedPercent(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = quoteData.change.getPriceChangeColor()
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
