package com.cardenaspiero255.jarvis.core

/**
 * Fast short-spoken answer; direct text questions preserve their previous limit.
 * Native decoding is CPU-only. These are bounded budgets, not speed guarantees.
 */
object FastVoiceGgufPolicy {
    const val VOICE_MAX_TOKENS = 48
    const val DIRECT_MAX_TOKENS = LocalGgufPolicy.MAX_NEW_TOKENS
    private const val NORMAL_WARM_BYTES = 300_000_000L
    private const val MEMORY_SAFE_WARM_BYTES = 650_000_000L
    private const val MIN_FREE_FOR_LARGE_CACHE = 3L * 1024 * 1024 * 1024

    fun warmCacheMaxBytes(availableMemory: Long, lowMemory: Boolean): Long =
        if (!lowMemory && availableMemory >= MIN_FREE_FOR_LARGE_CACHE)
            MEMORY_SAFE_WARM_BYTES else NORMAL_WARM_BYTES
}
