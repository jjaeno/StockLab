package com.example.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.example.android.data.model.OrderSide
import com.example.android.data.model.StockDetail
import com.example.android.ui.auth.LoginScreen
import com.example.android.ui.detail.DetailScreen
import com.example.android.ui.main.MainScreen
import com.example.android.ui.order.OrderConfirmScreen
import com.example.android.ui.portfolio.PortfolioScreen
import com.example.android.ui.theme.StockLabTheme
import com.example.android.viewmodel.AuthState
import com.example.android.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * StockLab MainActivity
 * - Firebase 초기화
 * - Hilt 의존성 주입
 * - Jetpack Compose UI
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase 초기화
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        setContent {
            StockLabTheme {
                StockLabApp()
            }
        }
    }
}

/**
 * 메인 앱 컴포저블
 */
@Composable
fun StockLabApp() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    val navController = rememberNavController()

    // 인증 상태에 따라 화면 전환
    when (authState) {
        is AuthState.Authenticated -> {
            MainNavigation(navController)
        }
        else -> {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    // 로그인 성공 시 자동으로 Authenticated 상태가 됨
                }
            )
        }
    }
}

/**
 * 메인 네비게이션 (로그인 후)
 */
@Composable
fun MainNavigation(navController: NavHostController) {
    val bottomNavItems = listOf(
        BottomNavItem.Main,
        BottomNavItem.Portfolio,
        BottomNavItem.History,
        BottomNavItem.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.MAIN,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 메인 화면
            composable(Route.MAIN) {
                MainScreen(
                    onStockClick = { stockDetail ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("stockDetail", stockDetail)
                        navController.navigate(Route.DETAIL)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Route.SETTINGS)
                    }
                )
            }

            // 종목 상세 화면
            composable(Route.DETAIL) {
                val stockDetail = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<StockDetail>("stockDetail")

                stockDetail?.let { detail ->
                    DetailScreen(
                        stockDetail = detail,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToOrder = { symbol, side, price, exchange ->
                            navController.currentBackStackEntry?.savedStateHandle?.apply {
                                set("orderSymbol", symbol)
                                set("orderSide", side)
                                set("orderPrice", price)
                                set("orderExchange", exchange)
                            }
                            navController.navigate(Route.ORDER_CONFIRM)
                        }
                    )
                }
            }

            // 주문 확인 화면
            composable(Route.ORDER_CONFIRM) {
                val symbol = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<String>("orderSymbol") ?: ""
                val side = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<OrderSide>("orderSide") ?: OrderSide.BUY
                val price = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<Double>("orderPrice") ?: 0.0
                val exchange = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<String?>("orderExchange")

                OrderConfirmScreen(
                    symbol = symbol,
                    side = side,
                    currentPrice = price,
                    exchange = exchange,
                    onDismiss = { navController.popBackStack() },
                    onOrderSuccess = {
                        navController.popBackStack(Route.DETAIL, inclusive = false)
                    }
                )
            }

            // 포트폴리오 화면
            composable(Route.PORTFOLIO) {
                PortfolioScreen(
                    onStockClick = { stockDetail ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("stockDetail", stockDetail)
                        navController.navigate(Route.DETAIL)
                    }
                )
            }

            // 거래 내역 화면 (간단하게 구현)
            composable(Route.HISTORY) {
                OrderHistoryScreen()
            }

            // 설정 화면 (간단하게 구현)
            composable(Route.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

/**
 * 네비게이션 라우트
 */
object Route {
    const val MAIN = "main"
    const val DETAIL = "detail"
    const val ORDER_CONFIRM = "order_confirm"
    const val PORTFOLIO = "portfolio"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * Bottom Navigation 아이템
 */
sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val title: String
) {
    object Main : BottomNavItem(Route.MAIN, Icons.Default.Home, "홈")
    object Portfolio : BottomNavItem(Route.PORTFOLIO, Icons.Default.AccountBalance, "포트폴리오")
    object History : BottomNavItem(Route.HISTORY, Icons.Default.List, "내역")
    object Settings : BottomNavItem(Route.SETTINGS, Icons.Default.Settings, "설정")
}

/**
 * 거래 내역 화면 (간단 구현)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("거래 내역") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        // 실제로는 OrderRepository를 통해 거래 내역을 불러와야 함
        Box(
            modifier = Modifier.padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("거래 내역 화면 (구현 예정)")
        }
    }
}

/**
 * 설정 화면 (간단 구현)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authResponse by authViewModel.authResponse.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp)
        ) {
            authResponse?.let { auth ->
                Card(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
                        Text(
                            text = "계정 정보",
                            style = MaterialTheme.typography.titleMedium
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                        Text("이름: ${auth.displayName}")
                        Text("이메일: ${auth.email}")
                        Text("UID: ${auth.uid}")
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                Button(
                    onClick = { authViewModel.signOut() },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    Text("로그아웃")
                }
            }
        }
    }
}