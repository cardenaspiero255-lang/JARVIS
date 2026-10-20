package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies PCM attribution at capture, NOT at delayed UI callback time. */
class LockScreenCaptureAccumulatorTest {
    @Test fun unlockingBeforeUiCallbackCannotRewriteCapturedScreenOffFrames() {
        val meter = LockScreenCaptureAccumulator()
        meter.observe(4096, interactive = false)
        meter.observe(1024, interactive = true)
        assertEquals(4096L, meter.drainScreenOff())
        assertEquals(0L, meter.drainScreenOff())
    }

    @Test fun lockingJustBeforeCallbackCannotClaimOldScreenOnFrames() {
        val meter = LockScreenCaptureAccumulator()
        meter.observe(16000, interactive = true)
        meter.observe(1024, interactive = false)
        assertEquals(1024L, meter.drainScreenOff())
    }

    @Test fun malformedOrEmptyReadsNeverCreateScreenOffEvidence() {
        val meter = LockScreenCaptureAccumulator()
        meter.observe(0, interactive = false)
        meter.observe(-7, interactive = false)
        meter.observe(9000000, interactive = false)
        assertEquals(0L, meter.drainScreenOff())
    }
}
