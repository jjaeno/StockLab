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
 * 메인 화면 (완전 개선)
 * - 관심종목 섹션 (맨 위)
 * - 전체 종목 리스트 (가격변동 + 관심종목 토글 버튼)
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
    val allStocks by mainViewModel.allStocks.collectAsState()
    val allStockQuotes by mainViewModel.allStockQuotes.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val errorMessage by mainViewModel.errorMessage.collectAsState()

    LaunchedEffect(authResponse?.uid) {
        authResponse?.uid?.let { uid ->
            mainViewModel.setUid(uid)
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

                    items(searchResults) { stock ->
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
                        items(watchlist) { item ->
                            val quote = watchlistQuotes[item.symbol]
                            val stockName = item.getDisplayName()
                            val isInWatchlist = true

                            EnhancedStockItemCard(
                                symbol = item.symbol,
                                name = stockName,
                                quote = quote,
                                isInWatchlist = isInWatchlist,
                                onClick = {
                                    quote?.let {
                                        onStockClick(
                                            StockDetail(
                                                symbol = item.symbol,
                                                name = stockName,
                                                exchange = item.exchange,
                                                stockType = it.stockType,
                                                currency = it.currency
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

                    items(allStocks) { stock ->
                        val quote = allStockQuotes[stock.symbol]
                        val isInWatchlist = watchlist.any { it.symbol == stock.symbol }

                        EnhancedStockItemCard(
                            symbol = stock.symbol,
                            name = stock.name,
                            quote = quote,
                            isInWatchlist = isInWatchlist,
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
 * 개선된 종목 아이템 카드 (가격변동 + 관심종목 토글)
 */
@Composable
private fun EnhancedStockItemCard(
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

            // 중간: 가격 정보
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

            // 오른쪽: 관심종목 토글
            IconButton(
                onClick = onToggleWatchlist,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isInWatchlist) "관심종목 삭제" else "관심종목 추가",
                    tint = if (isInWatchlist) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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