package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class LocalBenchmarkStatsTest {
    @Test fun medianHandlesEvenAndOddSamples() {
        assertEquals(8.0f, LocalBenchmarkStats.medianPositive(listOf(4f, 12f, 8f))!!, 0.01f)
        assertEquals(6.0f, LocalBenchmarkStats.medianPositive(listOf(2f, 10f, 4f, 8f))!!, 0.01f)
    }

    @Test fun invalidOrMissingThroughputIsNotPresentedAsRealMeasurement() {
        assertNull(LocalBenchmarkStats.medianPositive(emptyList()))
        assertNull(LocalBenchmarkStats.medianPositive(listOf(0f, -2f, Float.NaN)))
        assertEquals(12f, LocalBenchmarkStats.medianPositive(listOf(0f, 12f, Float.POSITIVE_INFINITY))!!, 0.01f)
    }
}
