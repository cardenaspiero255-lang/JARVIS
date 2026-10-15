package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenObservationPolicyTest {
    @Test fun neverStartsWithoutFreshVisibleOperatingSystemConsent() {
        val policy = ScreenObservationPolicy()
        assertFalse(policy.isActive(nowMillis = 100L))
        assertFalse(policy.start(explicitUserAndOsConsent = false, nowMillis = 100L))
        assertFalse(policy.sample(nowMillis = 101L))
        assertTrue(policy.start(explicitUserAndOsConsent = true, nowMillis = 100L))
        assertFalse(policy.start(explicitUserAndOsConsent = true, nowMillis = 102L))
    }

    @Test fun limitsFramesToEveryThreeSecondsAndStopsAfterTenMinutes() {
        val policy = ScreenObservationPolicy()
        assertTrue(policy.start(true, 1000L))
        assertTrue(policy.sample(1000L))
        assertFalse(policy.sample(1001L))
        assertFalse(policy.sample(3999L))
        assertTrue(policy.sample(4000L))
        assertTrue(policy.isActive(1000L + 599_999L))
        assertFalse(policy.isActive(1000L + 600_000L))
        assertFalse(policy.sample(1000L + 600_001L))
    }

    @Test fun revocationAndProcessRestartAlwaysFailClosed() {
        val policy = ScreenObservationPolicy()
        policy.start(true, 1000L)
        policy.stop()
        assertFalse(policy.sample(4000L))
        assertFalse(ScreenObservationPolicy().isActive(4000L))
    }

    @Test fun clockRollbackAndInvalidStartNeverExtendSession() {
        val policy = ScreenObservationPolicy()
        assertFalse(policy.start(true, -1L))
        assertTrue(policy.start(true, 1000L))
        assertFalse(policy.sample(999L))
        assertTrue(policy.sample(1000L))
        assertFalse(policy.sample(999L)) // rollback after a valid sample
        assertFalse(policy.isActive(Long.MAX_VALUE))
    }
}
