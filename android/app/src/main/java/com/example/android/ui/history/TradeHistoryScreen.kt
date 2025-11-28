package com.example.android.ui.history

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
 * 거래내역 ViewModel
 */
@HiltViewModel
class TradeHistoryViewModel @Inject constructor(
    private val repository: StockLabRepository
) : ViewModel() {

    private val _orders = MutableStateFlow<List<OrderEntity>>(emptyList())
    val orders: StateFlow<List<OrderEntity>> = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadOrders(uid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getUserOrders(uid).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        // 날짜순 정렬 (최신순)
                        _orders.value = result.data.sortedByDescending { it.createdAt }
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

    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * 거래내역 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeHistoryScreen(
    viewModel: TradeHistoryViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uid = authViewModel.uid

    LaunchedEffect(uid) {
        uid?.let { viewModel.loadOrders(it) }
    }

    val orders by viewModel.orders.collectAsState()
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
                        "거래내역",
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
                uid?.let { viewModel.loadOrders(it) }
            },
            modifier = Modifier.padding(paddingValues)
        ) {
            if (orders.isEmpty() && !isLoading) {
                EmptyOrdersCard()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders) { order ->
                        OrderHistoryCard(order = order)
                    }
                }
            }
        }
    }
}

/**
 * 주문 내역 카드
 */
@Composable
private fun OrderHistoryCard(order: OrderEntity) {
    val stockName = StockData.getStockName(order.symbol)
    val currency = if (order.symbol.isDomesticStock()) Currency.KRW else Currency.USD
    val totalAmount = order.price * order.quantity

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (order.side == OrderSide.BUY) {
                Color(0xFFFFF3F3)
            } else {
                Color(0xFFF0F8FF)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 헤더: 종목 이름 + 매수/매도
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stockName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = order.symbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (order.side == OrderSide.BUY) {
                        Constants.Colors.RedUp
                    } else {
                        Constants.Colors.BlueDown
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (order.side == OrderSide.BUY)
                                Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = order.side.koreanName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            Spacer(modifier = Modifier.height(12.dp))

            // 거래 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    label = "수량",
                    value = "${order.quantity.toFormattedNumber(2)}주"
                )
                InfoItem(
                    label = "가격",
                    value = order.price.toFormattedCurrency(currency)
                )
                InfoItem(
                    label = "총 금액",
                    value = totalAmount.toFormattedCurrency(currency),
                    valueColor = if (order.side == OrderSide.BUY) {
                        Constants.Colors.RedUp
                    } else {
                        Constants.Colors.BlueDown
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 거래 일시
            Text(
                text = "거래일: ${order.createdAt.toFormattedDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 정보 아이템
 */
@Composable
private fun InfoItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = valueColor
        )
    }
}

/**
 * 빈 거래내역 카드
 */
@Composable
private fun EmptyOrdersCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "거래 내역이 없습니다",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "첫 거래를 시작해보세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}