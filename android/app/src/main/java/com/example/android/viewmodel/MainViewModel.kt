package com.example.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.ApiResult
import com.example.android.data.local.QuoteCache
import com.example.android.data.model.*
import com.example.android.data.repository.StockLabRepository
import com.example.android.util.StockData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

/**
 * 메인 화면 ViewModel (개선 ver. - API 호출 최적화)
 *
 * 1. 다중 조회 API 사용 (/quotes/batch)
 * 2. 응답 누락 0% (status로 구분)
 * 3. 화면 재진입/스크롤 시 재호출 방지 (hasInitialized)
 * 4. 로컬 캐시 + last-known-good 폴백
 * 5. 30초 주기 자동 갱신 (백그라운드)
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: StockLabRepository,
    private val quoteCache: QuoteCache
) : ViewModel() {

    private var currentUid: String? = null
    // 초기화 플래그 (중복 호출 방지)
    private val hasInitialized = MutableStateFlow(false)
    private var watchlistRefreshJob: Job? = null
    private var allStocksRefreshJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 관심종목 리스트
    private val _watchlist = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WatchlistItem>> = _watchlist.asStateFlow()

    // 관심종목 시세 (Map<Symbol, QuoteResult>)
    private val _watchlistQuotes = MutableStateFlow<Map<String, QuoteResult>>(emptyMap())
    val watchlistQuotes: StateFlow<Map<String, QuoteResult>> = _watchlistQuotes.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private val _allStocks = MutableStateFlow<List<StockData.Stock>>(emptyList())
    val allStocks: StateFlow<List<StockData.Stock>> = _allStocks.asStateFlow()

    // 전체 종목 시세 (상위 10개만)
    private val _allStockQuotes = MutableStateFlow<Map<String, QuoteResult>>(emptyMap())
    val allStockQuotes: StateFlow<Map<String, QuoteResult>> = _allStockQuotes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 자동 갱신 Job
    private var refreshJob: Job? = null

    init {
        _allStocks.value = StockData.getAllStocks()

        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    /**
     * UID 설정 (최초 1회만)
     */
    fun setUid(uid: String) {
        if (currentUid == uid && hasInitialized.value) return

        currentUid = uid
        hasInitialized.value = true

        // 관심종목
        loadWatchlistWithQuotes()
        startPeriodicRefresh()

        // 전체 종목 시세
        loadTopStockQuotes()
        startAllStocksRefresh()
    }

    /**
    * 핵심 메서드: 관심종목 + 시세 일괄 조회
    *
    * 흐름:
    * 1. 관심종목 목록 조회
    * 2. POST /quotes/batch로 한 번에 시세 조회
    * 3. QuoteResult.status에 따라 처리:
    *    - SUCCESS: 정상 표시
    *    - FAILED: lastKnownPrice or 플레이스홀더
    *    - CACHED: 캐시 데이터 사용
    */
    private fun loadWatchlistWithQuotes() {
        val uid = currentUid ?: return

        viewModelScope.launch {
            _isLoading.value = true

            try {
                // 1. 관심종목 목록 조회
                repository.getWatchlist(uid).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            val items = result.data.items
                            _watchlist.value = items

                            if (items.isEmpty()) {
                                _watchlistQuotes.value = emptyMap()
                                _isLoading.value = false
                                return@collect
                            }

                            // 2. 심볼 리스트 생성
                            val symbols = items.map { it.symbol }

                            Log.i("MainViewModel", "다중 시세 조회 시작: ${symbols.size}건")

                            // 3. ⭐ 다중 시세 일괄 조회 (POST /quotes/batch)
                            repository.getBatchQuotes(symbols).collect { batchResult ->
                                when (batchResult) {
                                    is ApiResult.Success -> {
                                        val batchResponse = batchResult.data

                                        Log.i("MainViewModel",
                                            "다중 시세 조회 완료: 총 ${batchResponse.totalRequested}건, " +
                                                    "성공 ${batchResponse.successCount}건, " +
                                                    "실패 ${batchResponse.failedCount}건"
                                        )

                                        // 4. ⭐ 검증: 응답 개수 확인
                                        if (batchResponse.results.size != symbols.size) {
                                            Log.e("MainViewModel",
                                                "❌ 응답 누락! 요청: ${symbols.size}, 응답: ${batchResponse.results.size}"
                                            )
                                        }

                                        // 5. Map으로 변환
                                        val quotesMap = batchResponse.results.associateBy { it.symbol }

                                        // 6. 성공한 결과는 로컬 캐시에 저장
                                        batchResponse.results.forEach { quoteResult ->
                                            if (quoteResult.status == ResultStatus.SUCCESS) {
                                                quoteCache.saveSuccess(quoteResult.symbol, quoteResult)
                                            }
                                        }

                                        _watchlistQuotes.value = quotesMap
                                        _isLoading.value = false
                                    }
                                    is ApiResult.Error -> {
                                        _errorMessage.value = batchResult.message
                                        _isLoading.value = false
                                    }
                                    is ApiResult.Loading -> {
                                        // 이미 로딩 중
                                    }
                                }
                            }
                        }
                        is ApiResult.Error -> {
                            _errorMessage.value = result.message
                            _isLoading.value = false
                        }
                        is ApiResult.Loading -> {
                            // 이미 로딩 중
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "시세 조회 실패", e)
                _errorMessage.value = e.localizedMessage
                _isLoading.value = false
            }
        }
    }

    /**
     * 30초 주기 자동 갱신
     */
    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L) // 30초
                Log.d("MainViewModel", "백그라운드 갱신 시작")
                loadWatchlistWithQuotes()
            }
        }
    }

    /**
     * 수동 새로고침 (SwipeRefresh용)
     */
    fun refresh() {
        Log.i("MainViewModel", "수동 새로고침")
        loadWatchlistWithQuotes()
    }

    /**
     * 전체 종목 상위 10개 시세 조회 (초기 로드용)
     */
    private fun loadTopStockQuotes() {
        viewModelScope.launch {
            val topStocks = _allStocks.value.take(15)
            if (topStocks.isEmpty()) return@launch

            val symbols = topStocks.map { it.symbol }

            repository.getBatchQuotes(symbols).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val batch = result.data
                        // 응답 누락 검사
                        if (batch.results.size != symbols.size) {
                            Log.e(
                                "MainViewModel",
                                "❌ 전체 종목 batch 응답 누락: 요청 ${symbols.size}, 응답 ${batch.results.size}"
                            )
                        }
                        // Map<symbol, QuoteResult> 로 변환
                        val quotesMap = batch.results.associateBy { it.symbol }
                        // 성공한 결과는 캐시에 저장
                        batch.results.forEach { qr ->
                            if (qr.status == ResultStatus.SUCCESS) {
                                quoteCache.saveSuccess(qr.symbol, qr)
                            }
                        }
                        _allStockQuotes.value = quotesMap
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value =
                            result.message ?: "전체 종목 시세(batch)를 불러오지 못했습니다."
                    }
                    // 로딩 상태는 UI에 굳이 반영하지 않음
                    else -> {}
                }
            }
        }
    }

//    private fun loadAllStockQuotes() {
//        viewModelScope.launch {
//            val symbols = _allStocks.value.joinToString(",") { it.symbol }
//            if (symbols.isBlank()) return@launch
//
//            repository.getMultipleQuotes(symbols).collect { result ->
//                when (result) {
//                    is ApiResult.Success -> {
//                        // 모든 종목 시세 Map (key = symbol)
//                        val quotesMap = result.data.associateBy { it.quote.symbol }
//                        _allStockQuotes.value = quotesMap
//                    }
//
//                    is ApiResult.Error -> {
//                        // 에러 발생 시 기존 데이터 유지 + 에러 메시지 세팅
//                        _errorMessage.value =
//                            result.message ?: "전체 종목 시세를 불러오지 못했습니다."
//                    }
//
//                    is ApiResult.Loading -> {
//                        // 전체 종목은 로딩 상태를 굳이 UI에 반영하지 않음
//                    }
//                }
//            }
//        }
//    }
    private fun startAllStocksRefresh() {
        allStocksRefreshJob?.cancel()
        allStocksRefreshJob = viewModelScope.launch {
            while (isActive) {
                loadTopStockQuotes()
                delay(600_000L) // 5분
            }
        }
    }
    private fun startWatchlistRefresh() {
        watchlistRefreshJob?.cancel()
        watchlistRefreshJob = viewModelScope.launch {
            while (isActive) {
                loadWatchlistWithQuotes()
                delay(30_000L) // 10초
            }
        }
    }


    fun addToWatchlist(symbol: String, exchange: String? = null) {
        val uid = currentUid ?: return

        viewModelScope.launch {
            repository.addToWatchlist(uid, symbol, exchange).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        // 추가 후 즉시 갱신
                        loadWatchlistWithQuotes()
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value = result.message
                    }
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                }
            }
        }
    }

    fun removeFromWatchlist(symbol: String, exchange: String? = null) {
        val uid = currentUid ?: return

        viewModelScope.launch {
            repository.removeFromWatchlist(uid, symbol, exchange).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        // 삭제 후 즉시 갱신
                        loadWatchlistWithQuotes()
                    }
                    is ApiResult.Error -> {
                        _errorMessage.value = result.message
                    }
                    is ApiResult.Loading -> {
                        _isLoading.value = true
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun performSearch(query: String) {
        val results = StockData.searchStocks(query)

        _searchResults.value = results.map { stock ->
            StockSearchResult(
                symbol = stock.symbol,
                name = stock.name,
                market = stock.market
            )
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}