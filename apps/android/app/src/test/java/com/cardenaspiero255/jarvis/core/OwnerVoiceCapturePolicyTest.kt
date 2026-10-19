package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class OwnerVoiceCapturePolicyTest {
    @Test fun noSamplesWithoutExplicitConsentAndMicPermission() {
        val p = OwnerVoiceCapturePolicy()
        assertFalse(p.canCapture(consented = false, permissionGranted = true, activityVisible = true, busy = false))
        assertFalse(p.canCapture(consented = true, permissionGranted = false, activityVisible = true, busy = false))
        assertFalse(p.canCapture(consented = true, permissionGranted = true, activityVisible = false, busy = false))
        assertFalse(p.canCapture(consented = true, permissionGranted = true, activityVisible = true, busy = true))
        assertTrue(p.canCapture(consented = true, permissionGranted = true, activityVisible = true, busy = false))
    }

    @Test fun threeIndependentVoiceSegmentsWithAdequateSignalRequired() {
        val p = OwnerVoiceCapturePolicy()
        assertFalse(p.validSample(ShortArray(24_000)))
        assertFalse(p.validSample(ShortArray(20_000) { 5000 }))
        assertTrue(p.validSample(ShortArray(40_000) { if (it % 2 == 0) 3500 else -3500 }))
        assertFalse(p.validSample(ShortArray(170_000) { 3500 }))
    }

    @Test fun stopClearsAnyPendingSamples() {
        val p = OwnerVoiceCapturePolicy()
        assertEquals(3, p.requiredSamples)
        assertEquals(40_000, p.captureSamples)
    }
}
