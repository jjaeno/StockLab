package com.example.android.ui.main

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.android.data.model.*
import com.example.android.util.*
import com.example.android.viewmodel.AuthViewModel
import com.example.android.viewmodel.MainViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

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
                            contentDescription = "새로고침"
                        )
                    }
                },
                // 증권 앱 톤: 과한 컬러 배경 대신 기본 톤 유지
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
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
                item {
                    authResponse?.let { auth ->
                        BalanceCard(
                            displayName = auth.displayName,
                            cashKrw = auth.cashKrw,
                            cashUsd = auth.cashUsd
                        )
                    }
                }

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

                item {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { mainViewModel.updateSearchQuery(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

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
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "관심종목",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${watchlist.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (watchlist.isEmpty()) {
                        item {
                            EmptyWatchlistCard()
                        }
                    } else {
                        items(
                            items = watchlist,
                            key = { "${it.exchange ?: "NONE"}_${it.symbol}" }
                        ) { item ->
                            val quoteResult = watchlistQuotes[item.symbol]
                            val stockName = item.getDisplayName()
                            val isInWatchlist = true

                            EnhancedStockItemCard(
                                symbol = item.symbol,
                                name = stockName,
                                quoteResult = quoteResult,
                                isInWatchlist = isInWatchlist,
                                onClick = {
                                    quoteResult?.data?.let { _ ->
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

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "전체 종목",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    items(
                        items = allStocks,
                        key = { "${it.market}_${it.symbol}" }
                    ) { stock ->
                        val quoteResult = allStockQuotes[stock.symbol]
                        val isInWatchlist = watchlist.any { it.symbol == stock.symbol }

                        EnhancedStockItemCard(
                            symbol = stock.symbol,
                            name = stock.name,
                            quoteResult = quoteResult,
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

@Composable
private fun EnhancedStockItemCard(
    symbol: String,
    name: String,
    quoteResult: QuoteResult?,
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    // 증권 앱 톤: 카드 느낌 최소화 + 리스트형 밀도
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = quoteResult?.data != null)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    quoteResult == null -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    quoteResult.status == ResultStatus.SUCCESS -> {
                        val quote = quoteResult.data!!
                        val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD

                        Column(
                            modifier = Modifier.padding(end = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = quote.currentPrice.toFormattedCurrency(currency),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${quote.change.toFormattedChange(currency)} ${quote.percentChange.toFormattedPercent()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = quote.change.getPriceChangeColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                    quoteResult.status == ResultStatus.FAILED -> {
                        Column(
                            modifier = Modifier.padding(end = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            if (quoteResult.lastKnownPrice != null) {
                                val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD
                                Text(
                                    text = quoteResult.lastKnownPrice.toFormattedCurrency(currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "조회 실패",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "조회 실패",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    quoteResult.status == ResultStatus.CACHED -> {
                        val quote = quoteResult.data!!
                        val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD

                        Column(
                            modifier = Modifier.padding(end = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = quote.currentPrice.toFormattedCurrency(currency),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${quote.change.toFormattedChange(currency)} ${quote.percentChange.toFormattedPercent()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = quote.change.getPriceChangeColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isInWatchlist) "관심종목 삭제" else "관심종목 추가",
                        tint = if (isInWatchlist) Color(0xFFFFC107)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 구분선 (증권 앱 리스트 느낌)
        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun BalanceCard(
    displayName: String,
    cashKrw: Double,
    cashUsd: Double
) {
    // 증권 앱 톤: 라운드 + surfaceVariant + 정보 밀도
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "${displayName}님",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "총 자산",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cashKrw.toFormattedCurrency(Currency.KRW),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "USD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cashUsd.toFormattedCurrency(Currency.USD),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
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
    // 증권 앱 톤: 라운드, 높이 조금 낮게, 배경 톤 정리
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth(),
        placeholder = { Text("종목 검색", fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "지우기",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    )
}

@Composable
private fun SearchResultItem(
    stock: StockSearchResult,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    // 증권 앱 톤: 줄 간격/정보 밀도 + 구분선
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${stock.symbol} · ${stock.market}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "관심종목 추가",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun EmptyWatchlistCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "관심종목이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun HotStocksSection(
    items: List<HotStockItem>,
    onItemClick: (HotStockItem) -> Unit
) {
    // 증권 앱 톤: 섹션 컨테이너를 라운드로 잡고, 내부는 리스트형
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = "이슈 종목",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { item ->
                HotStockItemCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
                if (item != items.last()) {
                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
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
    // 요구사항: reason이 길면 토글식으로 펼칠 수 있게 (시그니처/호출 구조 유지)
    var expanded by remember(item.symbol, item.rank) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 랭크는 과한 배지 대신 작게 정돈 (기능/데이터는 유지)
            Text(
                text = "${item.rank}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.width(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            }

            // 토글 버튼: onClick(종목 이동)과 분리되게 IconButton 사용
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "접기" else "펼치기",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // reason: 기본 1줄 + 펼치면 전체 표시
        Text(
            text = item.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 34.dp) // rank 폭(24) + 간격(10)
        )

        // 텍스트 길이 상관없이 UX 통일: 작은 '더보기/접기' 라벨 제공
        Text(
            text = if (expanded) "접기" else "더보기",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier
                .padding(start = 34.dp, top = 6.dp)
                .clickable { expanded = !expanded }
        )
    }
}
