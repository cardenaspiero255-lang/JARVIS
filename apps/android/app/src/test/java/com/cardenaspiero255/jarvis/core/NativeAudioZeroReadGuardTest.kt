package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** A zero-sample READ_BLOCKING response must not create an unbounded CPU spin. */
class NativeAudioZeroReadGuardTest {
    @Test fun continuousZeroReadsHaveBoundedRetryBudget() {
        val guard = NativeAudioZeroReadGuard()
        repeat(NativeAudioZeroReadGuard.MAX_CONSECUTIVE_ZERO_READS - 1) {
            assertFalse(guard.observe(0))
        }
        assertTrue(guard.observe(0))
        // A stalled capture may not be reclassified as natural silence.
        assertTrue(guard.observe(0))
    }

    @Test fun realPcmFramesResetTheZeroReadBudget() {
        val guard = NativeAudioZeroReadGuard()
        repeat(NativeAudioZeroReadGuard.MAX_CONSECUTIVE_ZERO_READS - 1) {
            assertFalse(guard.observe(0))
        }
        assertFalse(guard.observe(1024))
        assertFalse(guard.observe(0))
    }

    @Test fun rejectsNegativeReadErrorsAndDoesNotCountThemAsSilence() {
        val guard = NativeAudioZeroReadGuard()
        try {
            guard.observe(-3)
            fail("A negative AudioRecord.read must use the existing fatal path")
        } catch (_: IllegalArgumentException) { }
        assertFalse(guard.observe(0))
        assertTrue(NativeAudioZeroReadGuard.BACKOFF_MS >= 10L)
    }
}
