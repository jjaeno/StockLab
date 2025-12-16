package com.example.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.ApiResult
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

/**
 * 메인 화면 ViewModel (개선 ver. - API 호출 최적화)
 *
 * 1. 관심종목 + 시세를 한 번에 로드 (다중 조회 API 사용)
 * 2. 10초 주기로 자동 갱신
 * 3. UI에서는 API 호출 없이 StateFlow만 구독
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: StockLabRepository,
) : ViewModel() {

    private var currentUid: String? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 관심종목 리스트
    private val _watchlist = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WatchlistItem>> = _watchlist.asStateFlow()

    // 관심종목 시세 (Map<Symbol, Quote>)
    private val _watchlistQuotes = MutableStateFlow<Map<String, UnifiedQuoteResponse>>(emptyMap())
    val watchlistQuotes: StateFlow<Map<String, UnifiedQuoteResponse>> = _watchlistQuotes.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private val _allStocks = MutableStateFlow<List<StockData.Stock>>(emptyList())
    val allStocks: StateFlow<List<StockData.Stock>> = _allStocks.asStateFlow()

    // 전체 종목 시세 (상위 10개만)
    private val _allStockQuotes = MutableStateFlow<Map<String, UnifiedQuoteResponse>>(emptyMap())
    val allStockQuotes: StateFlow<Map<String, UnifiedQuoteResponse>> = _allStockQuotes.asStateFlow()

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

    fun setUid(uid: String) {
        if (currentUid == uid && _watchlist.value.isNotEmpty()) return
        currentUid = uid

        // 초기 로드 + 자동 갱신 시작
        loadWatchlistWithQuotes()
        startPeriodicRefresh()
    }

    /**
     * 핵심 메서드: 관심종목 + 시세 한 번에 로드
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

                            if (items.isNotEmpty()) {
                                // 2. 심볼 리스트 생성 (쉼표 구분)
                                val symbols = items.joinToString(",") { it.symbol }

                                // 3. 다중 시세 조회 (단일 API 호출!)
                                repository.getMultipleQuotes(symbols).collect { quotesResult ->
                                    when (quotesResult) {
                                        is ApiResult.Success -> {
                                            // 4. Map으로 변환하여 저장
                                            val quotesMap = quotesResult.data.associateBy {
                                                "${it.market}_${it.quote.symbol}"
                                            }
                                            _watchlistQuotes.value = quotesMap
                                        }
                                        is ApiResult.Error -> {
                                            _errorMessage.value = quotesResult.message
                                        }
                                        is ApiResult.Loading -> {}
                                    }
                                }
                            } else {
                                // 관심종목이 없으면 빈 맵
                                _watchlistQuotes.value = emptyMap()
                            }
                        }
                        is ApiResult.Error -> {
                            _errorMessage.value = result.message
                        }
                        is ApiResult.Loading -> {}
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 10초마다 자동 갱신
     */
    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L) // 10초
                loadWatchlistWithQuotes()
            }
        }
    }

    /**
     * 수동 새로고침 (SwipeRefresh용)
     */
    fun refresh() {
        loadWatchlistWithQuotes()
        loadTopStockQuotes()
    }

    /**
     * 전체 종목 상위 10개 시세 조회 (초기 로드용)
     */
    private fun loadTopStockQuotes() {
        viewModelScope.launch {
            val quotes = mutableMapOf<String, UnifiedQuoteResponse>()
            val topStocks = _allStocks.value.take(10)

            val symbols = topStocks.joinToString(",") { it.symbol }
            if (symbols.isNotEmpty()) {
                repository.getMultipleQuotes(symbols).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            result.data.forEach { quote ->
                                quotes["${quote.market}_${quote.quote.symbol}"] = quote
                            }
                            _allStockQuotes.value = quotes
                        }
                        else -> {}
                    }
                }
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