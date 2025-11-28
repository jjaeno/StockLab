package com.example.android.ui.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.android.data.api.ApiResult
import com.example.android.data.model.*
import com.example.android.data.repository.StockLabRepository
import com.example.android.util.*
import com.example.android.viewmodel.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 포트폴리오 ViewModel
 */
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: StockLabRepository
) : ViewModel() {

    private val _portfolio = MutableStateFlow<PortfolioResponse?>(null)
    val portfolio: StateFlow<PortfolioResponse?> = _portfolio.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadPortfolio(uid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getPortfolio(uid).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _portfolio.value = result.data
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

    /**
     * 특정 종목의 보유 수량 조회
     */
    fun getHoldingQuantity(symbol: String): Double {
        return _portfolio.value?.positions?.find { it.symbol == symbol }?.quantity ?: 0.0
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * 포트폴리오 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    onStockClick: (StockDetail) -> Unit
) {
    val context = LocalContext.current

    val uid = authViewModel.uid
    LaunchedEffect(uid) {
        uid?.let { viewModel.loadPortfolio(it) }
    }

    val portfolio by viewModel.portfolio.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.showToast(it)
            viewModel.clearError()
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(isLoading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "포트폴리오",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                uid?.let { viewModel.loadPortfolio(it) }
            },
            modifier = Modifier.padding(paddingValues)
        ) {
            portfolio?.let { data ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        TotalAssetsCard(portfolio = data)
                    }

                    item {
                        Text(
                            text = "보유 종목",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    if (data.positions.isEmpty()) {
                        item { EmptyPositionsCard() }
                    } else {
                        items(data.positions) { position ->
                            PositionCard(
                                position = position,
                                onClick = {
                                    val stockType = if (position.symbol.isDomesticStock())
                                        StockType.DOMESTIC else StockType.OVERSEAS

                                    onStockClick(
                                        StockDetail(
                                            symbol = position.symbol,
                                            name = position.getDisplayName(),
                                            exchange = null,
                                            stockType = stockType,
                                            currency = position.currency
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalAssetsCard(portfolio: PortfolioResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = "총 자산",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KRW",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = portfolio.totalAssetsKrw.toFormattedCurrency(Currency.KRW),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "평가: ${portfolio.totalMarketValueKrw.toFormattedCurrency(Currency.KRW)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "USD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = portfolio.totalAssetsUsd.toFormattedCurrency(Currency.USD),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "평가: ${portfolio.totalMarketValueUsd.toFormattedCurrency(Currency.USD)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionCard(
    position: PositionView,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
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
                // 종목명 표시 (개선)
                Text(
                    text = position.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                // 심볼 작게 표시
                Text(
                    text = position.symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${position.quantity.toFormattedNumber(2)}주 · 평균 ${position.avgPrice.toFormattedCurrency(position.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = position.marketValue.toFormattedCurrency(position.currency),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        imageVector = if (position.isProfit) Icons.Default.TrendingUp
                        else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (position.isProfit) Constants.Colors.ProfitGreen
                        else Constants.Colors.LossRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${position.profitLoss.toFormattedChange(position.currency)} (${position.profitLossPercent.toFormattedPercent()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (position.isProfit) Constants.Colors.ProfitGreen
                        else Constants.Colors.LossRed
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPositionsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
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
                Text(
                    text = "보유 종목이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "종목을 매수하여 포트폴리오를 구성해보세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}