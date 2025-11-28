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
import com.example.android.util.*
import com.example.android.viewmodel.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 주문 ViewModel
 * AuthViewModel 을 Hilt 주입하면 에러가 나므로
 * AuthViewModel 은 Compose 측에서 넘겨받는 방식 유지
 */
@HiltViewModel
class OrderViewModel @Inject constructor(
    private val repository: StockLabRepository,
) : ViewModel() {

    private val _orderState = MutableStateFlow<UiState<OrderResponse>>(UiState.Idle)
    val orderState: StateFlow<UiState<OrderResponse>> = _orderState.asStateFlow()

    /**
     * 주문 생성 요청
     */
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

                        // 잔고 업데이트 콜백 실행
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

/**
 * 주문 확인 화면
 */
@Composable
fun OrderConfirmScreen(
    symbol: String,
    side: OrderSide,
    currentPrice: Double,
    exchange: String?,
    authViewModel: AuthViewModel = hiltViewModel(),
    orderViewModel: OrderViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onOrderSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authResponse by authViewModel.authResponse.collectAsState()
    val orderState by orderViewModel.orderState.collectAsState()

    var quantity by remember { mutableStateOf("1") }
    val quantityDouble = quantity.toDoubleOrNull() ?: 0.0

    val currency = if (symbol.isDomesticStock()) Currency.KRW else Currency.USD
    val totalAmount = currentPrice * quantityDouble

    // 주문 성공 처리
    LaunchedEffect(orderState) {
        when (orderState) {
            is UiState.Success -> {
                context.showToast("주문이 체결되었습니다")
                orderViewModel.resetOrderState()
                onOrderSuccess()
            }

            is UiState.Error -> {
                val error = (orderState as UiState.Error).message
                context.showToast(error)
            }

            else -> {}
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // 헤더
            Text(
                text = if (side == OrderSide.BUY) "매수 주문" else "매도 주문",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (side == OrderSide.BUY) Constants.Colors.RedUp
                else Constants.Colors.BlueDown
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("현재가", style = MaterialTheme.typography.bodyLarge)
                Text(
                    currentPrice.toFormattedCurrency(currency),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 수량 입력
            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    if (it.isEmpty() || it.isValidQuantity()) quantity = it
                },
                label = { Text("수량") },
                suffix = { Text("주") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 주문 금액
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("주문 금액", style = MaterialTheme.typography.titleMedium)
                Text(
                    totalAmount.toFormattedCurrency(currency),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 보유 현금
            authResponse?.let { auth ->
                val availableCash =
                    if (currency == Currency.KRW) auth.cashKrw else auth.cashUsd

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "보유 현금",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        availableCash.toFormattedCurrency(currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (side == OrderSide.BUY && totalAmount > availableCash) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "⚠️ 잔액이 부족합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = orderState !is UiState.Loading
                ) {
                    Text("취소")
                }

                Button(
                    onClick = {
                        val uid = authViewModel.uid ?: return@Button

                        orderViewModel.createOrder(
                            uid = uid,
                            symbol = symbol,
                            side = side,
                            quantity = quantityDouble,
                            exchange = exchange,
                            onBalanceUpdate = { cur, totalAmount ->
                                // 잔고 업데이트
                                if (side == OrderSide.BUY) {
                                    if (cur == Currency.KRW) {
                                        authViewModel.updateBalance(
                                            cashKrw = (authResponse?.cashKrw ?: 0.0) - totalAmount
                                        )
                                    } else {
                                        authViewModel.updateBalance(
                                            cashUsd = (authResponse?.cashUsd ?: 0.0) - totalAmount
                                        )
                                    }
                                } else {
                                    if (cur == Currency.KRW) {
                                        authViewModel.updateBalance(
                                            cashKrw = (authResponse?.cashKrw ?: 0.0) + totalAmount
                                        )
                                    } else {
                                        authViewModel.updateBalance(
                                            cashUsd = (authResponse?.cashUsd ?: 0.0) + totalAmount
                                        )
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = orderState !is UiState.Loading && quantityDouble > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == OrderSide.BUY)
                            Constants.Colors.RedUp else Constants.Colors.BlueDown
                    )
                ) {
                    if (orderState is UiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            if (side == OrderSide.BUY) "매수 확정" else "매도 확정",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
