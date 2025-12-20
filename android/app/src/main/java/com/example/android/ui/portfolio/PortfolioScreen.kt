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
import androidx.compose.foundation.clickable
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

    fun getHoldingQuantity(symbol: String): Double {
        return _portfolio.value?.positions
            ?.find { it.symbol == symbol }
            ?.quantity ?: 0.0
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

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
                        text = "포트폴리오",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { uid?.let { viewModel.loadPortfolio(it) } },
            modifier = Modifier.padding(paddingValues)
        ) {
            portfolio?.let { data ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        TotalAssetsSection(data)
                    }

                    item {
                        Text(
                            text = "보유 종목",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                        )
                    }

                    if (data.positions.isEmpty()) {
                        item { EmptyPositionsSection() }
                    } else {
                        items(
                            items = data.positions,
                            key = { it.symbol }
                        ) { position ->
                            PositionListItem(
                                position = position,
                                onClick = {
                                    val stockType =
                                        if (position.symbol.isDomesticStock())
                                            StockType.DOMESTIC
                                        else StockType.OVERSEAS

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
private fun TotalAssetsSection(portfolio: PortfolioResponse) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "총 자산",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = portfolio.totalAssetsKrw.toFormattedCurrency(Currency.KRW),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "평가 ${portfolio.totalMarketValueKrw.toFormattedCurrency(Currency.KRW)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Text(
                text = portfolio.totalAssetsUsd.toFormattedCurrency(Currency.USD),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                )
            )

            Text(
                text = "평가 ${portfolio.totalMarketValueUsd.toFormattedCurrency(Currency.USD)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun PositionListItem(
    position: PositionView,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = position.getDisplayName(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${position.symbol} · ${position.quantity.toFormattedNumber(2)}주",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "평균 ${position.avgPrice.toFormattedCurrency(position.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = position.marketValue.toFormattedCurrency(position.currency),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (position.isProfit)
                            Icons.Default.TrendingUp
                        else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (position.isProfit)
                            Constants.Colors.ProfitGreen
                        else Constants.Colors.LossRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${position.profitLoss.toFormattedChange(position.currency)} " +
                                position.profitLossPercent.toFormattedPercent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (position.isProfit)
                            Constants.Colors.ProfitGreen
                        else Constants.Colors.LossRed
                    )
                }
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun EmptyPositionsSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "보유 종목이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "종목을 매수하여 포트폴리오를 구성해보세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}
