package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenConsentRequestGateTest {
    @Test fun oneTimeAuthorizationRejectsOldCallbacksAndDuplicateResults() {
        val gate = ScreenConsentRequestGate()
        val old = gate.begin(1000L)!!
        assertEquals(8100, old)
        assertNull(gate.begin(1100L))
        gate.cancel()
        val next = gate.begin(1200L)!!
        assertEquals(8101, next)
        assertFalse(gate.consume(old, 1201L))
        assertTrue(gate.consume(next, 1202L))
        assertFalse(gate.consume(next, 1203L))
        assertFalse(gate.owns(7002))
        assertFalse(gate.owns(7001))
    }

    @Test fun expirationRollbackAndRotationNeverRestoreConsent() {
        val gate = ScreenConsentRequestGate()
        val old = gate.begin(2000L)!!
        assertFalse(gate.consume(old, 1999L))
        val newer = gate.begin(3000L)!!
        assertFalse(gate.consume(newer, 3000L + ScreenConsentRequestGate.MAX_AGE_MILLIS))
        val recreated = ScreenConsentRequestGate(gate.issuedCount())
        assertFalse(recreated.consume(newer, 3001L))
        assertEquals(newer + 1, recreated.begin(3100L))
        assertNull(gate.begin(-1L))
        assertNull(gate.begin(Long.MAX_VALUE))
    }

    @Test fun capacityAndCorruptRestorationFailClosed() {
        assertNull(ScreenConsentRequestGate(-1).begin(0L))
        assertNull(ScreenConsentRequestGate(Int.MAX_VALUE).begin(0L))
        val gate = ScreenConsentRequestGate()
        repeat(ScreenConsentRequestGate.REQUEST_COUNT) {
            val code = gate.begin(0L)!!
            assertTrue(gate.consume(code, 0L))
        }
        assertNull(gate.begin(0L))
    }
}
