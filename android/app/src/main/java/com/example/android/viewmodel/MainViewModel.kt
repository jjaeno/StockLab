package com.example.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.ApiResult
import com.example.android.data.model.*
import com.example.android.data.repository.StockLabRepository
import com.example.android.util.StockData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 메인 화면 ViewModel (완전 개선)
 * - 전체 종목 시세 조회
 * - 관심종목 관리
 * - 검색 기능
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: StockLabRepository,
) : ViewModel() {

    private var currentUid: String? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _watchlist = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WatchlistItem>> = _watchlist.asStateFlow()

    private val _watchlistQuotes = MutableStateFlow<Map<String, UnifiedQuoteResponse>>(emptyMap())
    val watchlistQuotes: StateFlow<Map<String, UnifiedQuoteResponse>> = _watchlistQuotes.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private val _allStocks = MutableStateFlow<List<StockData.Stock>>(emptyList())
    val allStocks: StateFlow<List<StockData.Stock>> = _allStocks.asStateFlow()

    // 전체 종목 시세 (새로 추가)
    private val _allStockQuotes = MutableStateFlow<Map<String, UnifiedQuoteResponse>>(emptyMap())
    val allStockQuotes: StateFlow<Map<String, UnifiedQuoteResponse>> = _allStockQuotes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

        // 전체 종목 시세 로드 (일부만)
        loadAllStockQuotes()
    }

    fun setUid(uid: String) {
        if (currentUid == uid && _watchlist.value.isNotEmpty()) return
        currentUid = uid
        loadWatchlist()
    }

    /**
     * 관심종목 목록 로드
     */
    fun loadWatchlist() {
        val uid = currentUid ?: return

        viewModelScope.launch {
            repository.getWatchlist(uid).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _watchlist.value = result.data.items
                        loadWatchlistQuotes()
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
     * 관심종목 시세 조회
     */
    private fun loadWatchlistQuotes() {
        val items = _watchlist.value

        if (items.isEmpty()) {
            _watchlistQuotes.value = emptyMap()
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            val quotes = mutableMapOf<String, UnifiedQuoteResponse>()

            items.forEach { item ->
                repository.getQuote(item.symbol, item.exchange).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            quotes[item.symbol] = result.data
                            _watchlistQuotes.value = quotes.toMap()
                        }
                        is ApiResult.Error -> {}
                        is ApiResult.Loading -> {}
                    }
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * 전체 종목 시세 조회 (일부만 - 상위 10개)
     */
    private fun loadAllStockQuotes() {
        viewModelScope.launch {
            val quotes = mutableMapOf<String, UnifiedQuoteResponse>()

            // 상위 10개 종목만 조회 (API 부하 방지)
            val topStocks = _allStocks.value.take(10)

            topStocks.forEach { stock ->
                repository.getQuote(stock.symbol, null).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            quotes[stock.symbol] = result.data
                            _allStockQuotes.value = quotes.toMap()
                        }
                        is ApiResult.Error -> {}
                        is ApiResult.Loading -> {}
                    }
                }
            }
        }
    }

    /**
     * 특정 종목 시세 조회 (필요 시)
     */
    fun loadQuoteForStock(symbol: String) {
        viewModelScope.launch {
            repository.getQuote(symbol, null).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val currentQuotes = _allStockQuotes.value.toMutableMap()
                        currentQuotes[symbol] = result.data
                        _allStockQuotes.value = currentQuotes
                    }
                    is ApiResult.Error -> {}
                    is ApiResult.Loading -> {}
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
                        loadWatchlist()
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
                        loadWatchlist()
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

    fun getQuote(symbol: String, exchange: String? = null): Flow<ApiResult<UnifiedQuoteResponse>> {
        return repository.getQuote(symbol, exchange)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refresh() {
        loadWatchlist()
        loadAllStockQuotes()
    }
}