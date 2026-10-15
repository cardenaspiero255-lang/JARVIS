package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionRequestGateTest {
    @Test fun oldResultIsRejectedAfterNewSelection() {
        val gate = VisionRequestGate()
        val first = gate.begin()
        assertTrue(gate.accepts(first))
        val second = gate.begin()
        assertFalse(gate.accepts(first))
        assertTrue(gate.accepts(second))
    }

    @Test fun backgroundingCancelsPendingResult() {
        val gate = VisionRequestGate()
        val request = gate.begin()
        gate.cancel()
        assertFalse(gate.accepts(request))
    }

    @Test fun unrequestedOrReplayedResultIsNotAuthorized() {
        val gate = VisionRequestGate()
        assertFalse(gate.accepts(1L))
        val request = gate.begin()
        assertTrue(gate.accepts(request))
        gate.cancel()
        assertFalse(gate.accepts(request))
        assertFalse(VisionRequestGate().accepts(request))
    }
}
