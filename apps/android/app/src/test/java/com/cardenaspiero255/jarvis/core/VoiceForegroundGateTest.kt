package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceForegroundGateTest {
    @Test fun resultWhenActivityStoppedNeverCreatesConsent() {
        val gate = VoiceForegroundGate()
        gate.onStart()
        assertTrue(gate.canAcceptRecognitionResult())
        gate.onStop()
        assertFalse(gate.canAcceptRecognitionResult())
    }

    @Test fun resultBeforeFirstStartOrAfterProcessRestartIsRejected() {
        assertFalse(VoiceForegroundGate().canAcceptRecognitionResult())
        val gate = VoiceForegroundGate()
        gate.onStart()
        gate.onStop()
        assertFalse(gate.canAcceptRecognitionResult())
        assertFalse(VoiceForegroundGate().canAcceptRecognitionResult())
    }

    @Test fun foregroundRestoredAllowsFreshResultProcessing() {
        val gate = VoiceForegroundGate()
        gate.onStart()
        gate.onStop()
        gate.onStart()
        assertTrue(gate.canAcceptRecognitionResult())
    }
}
