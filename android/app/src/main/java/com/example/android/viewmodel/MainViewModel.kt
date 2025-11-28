package com.example.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.ApiResult
import com.example.android.data.model.*
import com.example.android.data.repository.StockLabRepository
import com.example.android.util.StockData
import com.example.android.util.isDomesticStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 메인 화면 ViewModel
 * - 주식 리스트 표시
 * - 관심종목 관리
 * - 검색 기능
 * - 시세 조회
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: StockLabRepository,
) : ViewModel() {

    // 현재 로그인한 유저 uid (MainScreen에서 한번만 세팅)
    private var currentUid: String? = null

    // 검색 쿼리
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 관심종목 목록
    private val _watchlist = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WatchlistItem>> = _watchlist.asStateFlow()

    // 관심종목 시세 정보
    private val _watchlistQuotes =
        MutableStateFlow<Map<String, UnifiedQuoteResponse>>(emptyMap())
    val watchlistQuotes: StateFlow<Map<String, UnifiedQuoteResponse>> =
        _watchlistQuotes.asStateFlow()

    // 검색 결과
    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    // 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 에러 메시지
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 검색 쿼리 변경 감지
        viewModelScope.launch {
            _searchQuery
                .debounce(300) // 300ms 디바운스
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    /**
     * 로그인한 유저 uid 설정
     * - MainScreen에서 authResponse.uid 들어오면 한 번만 호출
     */
    fun setUid(uid: String) {
        // 같은 uid로 이미 로드했으면 다시 로드 안 함
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
                        // 관심종목 시세 조회
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

                        is ApiResult.Error -> {
                            // 개별 종목 오류는 무시
                        }

                        is ApiResult.Loading -> {
                            // 로딩 상태 유지
                        }
                    }
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * 관심종목 추가
     */
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

    /**
     * 관심종목 삭제
     */
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

    /**
     * 검색 쿼리 업데이트
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 검색 실행 (로컬 데이터 기반)
     */
    private fun performSearch(query: String) {
        val results = StockData.searchStocks(query)

        _searchResults.value = results.map { stock ->
            StockSearchResult(
                symbol = stock.symbol,
                name = stock.name,
                market = stock.market
                // exchange, currency는 기본값 사용 (필요 없으니 안 넘김)
            )
        }
    }

    /**
     * 단일 종목 시세 조회
     */
    fun getQuote(symbol: String, exchange: String? = null): Flow<ApiResult<UnifiedQuoteResponse>> {
        return repository.getQuote(symbol, exchange)
    }

    /**
     * 에러 메시지 초기화
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 새로고침
     */
    fun refresh() {
        loadWatchlist()
    }
}
