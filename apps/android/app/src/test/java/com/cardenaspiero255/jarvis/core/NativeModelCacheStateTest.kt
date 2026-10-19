package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class NativeModelCacheStateTest {
    @Test fun firstShaIsColdAndRepeatedExactRevisionCanReuse() {
        val policy = NativeModelCacheState()
        val firstSha = "1".repeat(64)
        val nextSha = "2".repeat(64)
        assertEquals(CacheAction.LOAD, policy.classify("selected.gguf", firstSha))
        assertEquals(CacheAction.REUSE, policy.classify("selected.gguf", firstSha))
        assertEquals(CacheAction.REPLACE, policy.classify("selected.gguf", nextSha))
        assertEquals(CacheAction.REUSE, policy.classify("selected.gguf", nextSha))
    }

    @Test fun filenameAloneIsNotValidCacheIdentityAndEvictionIsExplicit() {
        val policy = NativeModelCacheState()
        assertEquals(CacheAction.REJECT, policy.classify("selected.gguf", "broken"))
        assertEquals(CacheAction.LOAD, policy.classify("selected.gguf", "a".repeat(64)))
        policy.invalidate()
        assertEquals(CacheAction.LOAD, policy.classify("selected.gguf", "a".repeat(64)))
        assertEquals(CacheAction.REPLACE,
            policy.classify("other.gguf", "a".repeat(64)))
    }
}
