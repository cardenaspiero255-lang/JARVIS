package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCapturePrivacyGateTest {
    @Test fun secureByDefault() {
        val gate = ScreenCapturePrivacyGate()
        assertFalse(gate.captureAllowed(0L))
        assertFalse(gate.captureAllowed(20_000L))
    }

    @Test fun tapGrantsOnlySixtySeconds() {
        val gate = ScreenCapturePrivacyGate()
        assertTrue(gate.allowForDebugByTap(1_000L))
        assertTrue(gate.captureAllowed(1_000L))
        assertTrue(gate.captureAllowed(60_999L))
        assertFalse(gate.captureAllowed(61_000L))
        assertFalse(gate.captureAllowed(61_001L))
    }

    @Test fun duplicateTapCannotExtendAnActiveWindow() {
        val gate = ScreenCapturePrivacyGate()
        assertTrue(gate.allowForDebugByTap(100L))
        assertFalse(gate.allowForDebugByTap(50_000L))
        assertFalse(gate.captureAllowed(60_100L))
        assertTrue(gate.allowForDebugByTap(61_000L))
        assertTrue(gate.captureAllowed(61_000L))
    }

    @Test fun lockOrBackgroundRevokesPermissionImmediately() {
        val gate = ScreenCapturePrivacyGate()
        assertTrue(gate.allowForDebugByTap(10L))
        gate.revoke()
        assertFalse(gate.captureAllowed(11L))
        assertFalse(gate.captureAllowed(40_000L))
    }

    @Test fun rollbackAndOverflowCannotGrantCapture() {
        val gate = ScreenCapturePrivacyGate()
        assertFalse(gate.allowForDebugByTap(-1L))
        assertFalse(gate.allowForDebugByTap(Long.MAX_VALUE))
        assertTrue(gate.allowForDebugByTap(20L))
        assertFalse(gate.captureAllowed(19L))
        assertFalse(gate.captureAllowed(21L))
    }

    @Test fun requiresNewTapAfterExpiration() {
        val gate = ScreenCapturePrivacyGate()
        assertTrue(gate.allowForDebugByTap(0L))
        assertFalse(gate.captureAllowed(60_000L))
        assertTrue(gate.allowForDebugByTap(61_000L))
        assertTrue(gate.captureAllowed(61_000L))
    }
}
