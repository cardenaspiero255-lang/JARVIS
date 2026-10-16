package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamingReadinessPolicyTest {
    @Test fun lowMemoryIsConservative() {
        assertEquals(GamingAdvice.LOW_MEMORY, GamingReadinessPolicy.classify(8192L, true))
        assertEquals(GamingAdvice.LOW_MEMORY, GamingReadinessPolicy.classify(256L, false))
    }

    @Test fun unknownIsNotReportedAsOptimized() {
        assertEquals(GamingAdvice.CHECK_RESOURCES, GamingReadinessPolicy.classify(null, null))
        assertEquals(GamingAdvice.READY_FOR_MANUAL_LAUNCH,
            GamingReadinessPolicy.classify(1024L, false))
        assertTrue(GamingReadinessPolicy.message(GamingAdvice.READY_FOR_MANUAL_LAUNCH)
            .contains("no se han aplicado optimizaciones"))
    }
}
