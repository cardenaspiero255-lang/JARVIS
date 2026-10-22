package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class GgufNativePerformanceTest {
    @Test fun nativeTimingParsesOnlyNonnegativeSafeCounters() {
        val p = GgufNativePerformance.fromCounters(
            longArrayOf(1200, 180, 30, 200, 700, 23, 4, 1))
        assertNotNull(p)
        assertEquals(180L, p!!.loadMs)
        assertEquals(200L, p.prefillMs)
        assertEquals(700L, p.decodeMs)
        assertEquals(23L, p.decodedTokens)
        assertEquals(4L, p.cpuThreads)
        assertTrue(p.warmCacheHit)
    }
    @Test fun missingOrImpossibleMeasurementsDoNotBecomeEvidence() {
        assertNull(GgufNativePerformance.fromCounters(longArrayOf(123L)))
        assertNull(GgufNativePerformance.fromCounters(longArrayOf(
            -1, 0, 0, 0, 0, 0, 2, 0)))
        assertNull(GgufNativePerformance.fromCounters(longArrayOf(
            5, 0, 0, 0, 0, 0, 0, 0)))
        assertNull(GgufNativePerformance.fromCounters(longArrayOf(
            5, 0, 0, 0, 0, 0, 2, 3)))
    }
}
