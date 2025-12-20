package com.example.android.ui.order

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.ApiResult
import com.example.android.data.model.*
import com.example.android.data.repository.StockLabRepository
import com.example.android.ui.portfolio.PortfolioViewModel
import com.example.android.util.*
import com.example.android.viewmodel.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val repository: StockLabRepository,
) : ViewModel() {

    private val _orderState = MutableStateFlow<UiState<OrderResponse>>(UiState.Idle)
    val orderState: StateFlow<UiState<OrderResponse>> = _orderState.asStateFlow()

    fun createOrder(
        uid: String,
        symbol: String,
        side: OrderSide,
        quantity: Double,
        exchange: String?,
        onBalanceUpdate: (Currency, Double) -> Unit
    ) {
        viewModelScope.launch {
            _orderState.value = UiState.Loading

            val request = OrderRequest(
                symbol = symbol,
                side = side,
                quantity = quantity,
                exchange = exchange
            )

            repository.createOrder(uid, request).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _orderState.value = UiState.Success(result.data)
                        onBalanceUpdate(result.data.currency, result.data.totalAmount)
                    }
                    is ApiResult.Error -> {
                        _orderState.value = UiState.Error(result.message, result.code)
                    }
                    is ApiResult.Loading -> {
                        _orderState.value = UiState.Loading
                    }
                }
            }
        }
    }

    fun resetOrderState() {
        _orderState.value = UiState.Idle
    }
}

@Composable
fun OrderConfirmScreen(
    symbol: String,
    side: OrderSide,
    currentPrice: Double,
    exchange: String?,
    authViewModel: AuthViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel(),
    portfolioViewModel: PortfolioViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onOrderSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authResponse by authViewModel.authResponse.collectAsState()
    val orderState by orderViewModel.orderState.collectAsState()

    val uid = authViewModel.uid
    LaunchedEffect(uid) {
        uid?.let { portfolioViewModel.loadPortfolio(it) }
    }

    val holdingQuantity =
        if (side == OrderSide.SELL) portfolioViewModel.getHoldingQuantity(symbol) else 0.0

    var quantity by remember { mutableStateOf("1") }
    val quantityDouble = quantity.toDoubleOrNull() ?: 0.0

    val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD
    val totalAmount = currentPrice * quantityDouble
    val isQuantityExceeded = side == OrderSide.SELL && quantityDouble > holdingQuantity

    LaunchedEffect(orderState) {
        when (orderState) {
            is UiState.Success -> {
                context.showToast("주문이 체결되었습니다")
                authViewModel.uid?.let { portfolioViewModel.loadPortfolio(it) }
                orderViewModel.resetOrderState()
                onOrderSuccess()
            }
            is UiState.Error -> {
                context.showToast((orderState as UiState.Error).message)
            }
            else -> Unit
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {

            // 헤더
            Text(
                text = if (side == OrderSide.BUY) "매수 주문" else "매도 주문",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (side == OrderSide.BUY)
                    Constants.Colors.RedUp else Constants.Colors.BlueDown
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = symbol,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            if (side == OrderSide.SELL) {
                InfoRow(
                    label = "보유 수량",
                    value = "${holdingQuantity.toFormattedNumber(2)}주",
                    highlight = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "최대 판매 가능 수량입니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            InfoRow(
                label = "현재가",
                value = currentPrice.toFormattedCurrency(currency)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    if (it.isEmpty() || it.isValidQuantity()) quantity = it
                },
                label = { Text("수량") },
                suffix = { Text("주") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = isQuantityExceeded,
                supportingText = {
                    if (isQuantityExceeded) {
                        Text(
                            "보유 수량을 초과할 수 없습니다",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                label = "주문 금액",
                value = totalAmount.toFormattedCurrency(currency),
                highlight = true
            )

            authResponse?.let {
                val cash = if (currency == Currency.KRW) it.cashKrw else it.cashUsd

                Spacer(modifier = Modifier.height(4.dp))

                InfoRow(
                    label = "보유 현금",
                    value = cash.toFormattedCurrency(currency),
                    subtle = true
                )

                if (side == OrderSide.BUY && totalAmount > cash) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "잔액이 부족합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = orderState !is UiState.Loading
                ) {
                    Text("취소")
                }

                Button(
                    onClick = {
                        val userId = authViewModel.uid ?: return@Button
                        orderViewModel.createOrder(
                            uid = userId,
                            symbol = symbol,
                            side = side,
                            quantity = quantityDouble,
                            exchange = exchange,
                            onBalanceUpdate = { cur, amount ->
                                if (side == OrderSide.BUY) {
                                    if (cur == Currency.KRW) {
                                        authViewModel.updateBalance(
                                            cashKrw = (authResponse?.cashKrw ?: 0.0) - amount
                                        )
                                    } else {
                                        authViewModel.updateBalance(
                                            cashUsd = (authResponse?.cashUsd ?: 0.0) - amount
                                        )
                                    }
                                } else {
                                    if (cur == Currency.KRW) {
                                        authViewModel.updateBalance(
                                            cashKrw = (authResponse?.cashKrw ?: 0.0) + amount
                                        )
                                    } else {
                                        authViewModel.updateBalance(
                                            cashUsd = (authResponse?.cashUsd ?: 0.0) + amount
                                        )
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = orderState !is UiState.Loading &&
                            quantityDouble > 0 &&
                            !isQuantityExceeded,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == OrderSide.BUY)
                            Constants.Colors.RedUp else Constants.Colors.BlueDown
                    )
                ) {
                    if (orderState is UiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (side == OrderSide.BUY) "매수 확정" else "매도 확정",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    subtle: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                subtle -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}
