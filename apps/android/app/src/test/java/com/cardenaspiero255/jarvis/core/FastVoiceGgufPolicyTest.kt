package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class FastVoiceGgufPolicyTest {
    @Test fun lowLatencyVoiceIsBoundedAndPreservesFullDirectAnswerBudget() {
        assertEquals(48, FastVoiceGgufPolicy.VOICE_MAX_TOKENS)
        assertEquals(96, FastVoiceGgufPolicy.DIRECT_MAX_TOKENS)
        assertTrue(FastVoiceGgufPolicy.VOICE_MAX_TOKENS > 0)
        assertTrue(FastVoiceGgufPolicy.VOICE_MAX_TOKENS < FastVoiceGgufPolicy.DIRECT_MAX_TOKENS)
    }

    @Test fun warmCacheBudgetNeedsMemoryHeadroom() {
        assertEquals(300_000_000L, FastVoiceGgufPolicy.warmCacheMaxBytes(500L * 1024 * 1024, false))
        assertEquals(300_000_000L, FastVoiceGgufPolicy.warmCacheMaxBytes(5L * 1024 * 1024 * 1024, true))
        assertEquals(650_000_000L, FastVoiceGgufPolicy.warmCacheMaxBytes(5L * 1024 * 1024 * 1024, false))
    }
}
