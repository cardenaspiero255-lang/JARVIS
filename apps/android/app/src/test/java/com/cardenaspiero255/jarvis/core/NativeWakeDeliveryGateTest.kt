package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWakeDeliveryGateTest {
    private fun canDispatch(ended: Boolean, active: Boolean = true,
        running: Boolean = true, closed: Boolean = false, native: Boolean = true,
        consumed: Boolean = false, extended: Boolean = true) =
        NativeWakeDeliveryGate.allow(running, closed, ended, native, active,
            consumed, extended)

    @Test fun stopRejectsQueuedWakeBeforeServiceIsDestroyed() {
        assertTrue(canDispatch(ended = false))
        // stopSelf is asynchronous: isRunning and timeGate may still appear active.
        assertFalse(canDispatch(ended = true))
        assertFalse(canDispatch(ended = true, extended = false))
    }

    @Test fun closedInactiveAndConsumedSessionsNeverAcceptVoice() {
        assertFalse(canDispatch(ended = false, active = false))
        assertFalse(canDispatch(ended = false, running = false))
        assertFalse(canDispatch(ended = false, closed = true))
        assertFalse(canDispatch(ended = false, native = false))
        assertFalse(canDispatch(ended = false, consumed = true, extended = false))
        assertTrue(canDispatch(ended = false, consumed = true, extended = true))
    }
}
