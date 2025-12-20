package com.example.android.data.local

import com.example.android.data.model.QuoteResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모리 기반 Quote 캐시
 * - 성공 결과: 60초 TTL
 * - last-known-good: 무기한 보관
 */
@Singleton
class QuoteCache @Inject constructor() {

    private val successCache = mutableMapOf<String, CachedQuote>()
    private val lastKnownGood = mutableMapOf<String, Double>()

    private data class CachedQuote(
        val result: QuoteResult,
        val timestamp: Long
    )

    /**
     * 성공 결과 저장 (60초 TTL)
     */
    fun saveSuccess(symbol: String, result: QuoteResult) {
        successCache[symbol] = CachedQuote(result, System.currentTimeMillis())

        // last-known-good도 업데이트
        result.data?.currentPrice?.let {
            lastKnownGood[symbol] = it
        }
    }

    /**
     * 캐시에서 조회 (60초 이내만 유효)
     */
    fun get(symbol: String): QuoteResult? {
        val cached = successCache[symbol] ?: return null
        val age = System.currentTimeMillis() - cached.timestamp

        return if (age < 60_000) cached.result else null
    }

    /**
     * last-known-good 조회
     */
    fun getLastKnownPrice(symbol: String): Double? {
        return lastKnownGood[symbol]
    }

    /**
     * 전체 캐시 클리어
     */
    fun clear() {
        successCache.clear()
        // last-known-good은 유지
    }
}