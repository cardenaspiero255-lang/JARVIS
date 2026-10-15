package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognitionRequestGateTest {
    @Test fun acceptsOnlyOneResultForAnExplicitRequest() {
        val gate = VoiceRecognitionRequestGate()
        assertFalse(gate.consume(101L))
        assertTrue(gate.begin(100L))
        assertFalse(gate.begin(101L))
        assertTrue(gate.isPending(102L))
        assertTrue(gate.consume(103L))
        assertFalse(gate.consume(104L))
        assertFalse(gate.isPending(105L))
    }

    @Test fun expiredAndRolledBackClocksRejectCallbacks() {
        val gate = VoiceRecognitionRequestGate()
        assertTrue(gate.begin(100L))
        assertFalse(gate.isPending(99L))
        assertFalse(gate.consume(99L))
        assertTrue(gate.begin(1000L))
        assertFalse(gate.consume(181_000L))
        assertTrue(gate.begin(181_001L))
        assertFalse(gate.consume(Long.MAX_VALUE))
    }

    @Test fun invalidBeginsAndUserCancellationNeverGrantAuthority() {
        val gate = VoiceRecognitionRequestGate()
        assertFalse(gate.begin(-1L))
        assertFalse(gate.consume(2L))
        assertTrue(gate.begin(0L))
        gate.cancel()
        assertFalse(gate.isPending(1L))
        assertFalse(gate.consume(2L))
    }

    @Test fun previousRequestCannotBeReusedAfterTimeoutOrNewRequest() {
        val gate = VoiceRecognitionRequestGate()
        assertTrue(gate.begin(10L))
        assertFalse(gate.begin(11L))
        assertFalse(gate.consume(180_010L))
        assertTrue(gate.begin(180_011L))
        assertTrue(gate.consume(180_012L))
        assertFalse(gate.consume(180_013L))
    }
}
