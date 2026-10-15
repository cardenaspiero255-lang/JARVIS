package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPickerRequestGateTest {
    @Test fun callbackRequiresFreshMatchingOneTimeTap() {
        val gate = VisionPickerRequestGate()
        val id = gate.begin(1_000L)
        assertEquals(7002, id)
        assertTrue(gate.owns(id!!))
        assertTrue(gate.consume(id, 1_100L))
        assertFalse(gate.consume(id, 1_101L))
        assertFalse(VisionPickerRequestGate().consume(id, 1_101L))
    }

    @Test fun staleCallbackCannotUseNewerPickerAuthorization() {
        val gate = VisionPickerRequestGate()
        val old = gate.begin(10L)!!
        gate.cancel() // E.g. privacy lock.
        val fresh = gate.begin(20L)!!
        assertEquals(old + 1, fresh)
        assertFalse(gate.consume(old, 21L))
        assertTrue(gate.consume(fresh, 22L))
        assertFalse(gate.owns(7001)) // Speech recognizer owns this request code.
        assertFalse(gate.owns(8000))
    }

    @Test fun overlappingPickersAreRejectedWithoutInvalidatingCurrentOne() {
        val gate = VisionPickerRequestGate()
        val active = gate.begin(100L)!!
        assertNull(gate.begin(101L))
        assertTrue(gate.consume(active, 102L))
    }

    @Test fun expiredOrRolledBackRequestsNeverStartOcr() {
        val gate = VisionPickerRequestGate()
        val old = gate.begin(1_000L)!!
        assertFalse(gate.consume(old, 999L))
        val current = gate.begin(2_000L)!!
        assertFalse(gate.consume(current, 2_000L + VisionPickerRequestGate.MAX_AGE_MILLIS))
        val next = gate.begin(3_000L)!!
        assertTrue(gate.consume(next, 3_100L))
        assertNull(gate.begin(-1L))
        assertNull(gate.begin(Long.MAX_VALUE))
    }

    @Test fun cancellationAndResultCodeExhaustionFailClosed() {
        val gate = VisionPickerRequestGate()
        repeat(VisionPickerRequestGate.REQUEST_COUNT) { index ->
            val id = gate.begin(0L)
            assertNotNull(id)
            assertEquals(7002 + index, id)
            assertTrue(gate.consume(id!!, 0L))
        }
        assertNull(gate.begin(0L))
    }

    @Test fun restoringAfterRotationAdvancesCodesWithoutRestoringPermission() {
        val oldGate = VisionPickerRequestGate()
        val old = oldGate.begin(500L)!!
        val recreated = VisionPickerRequestGate(oldGate.issuedCount())
        assertFalse(recreated.consume(old, 550L))
        val fresh = recreated.begin(600L)!!
        assertEquals(old + 1, fresh)
        assertFalse(recreated.consume(old, 601L))
        assertTrue(recreated.consume(fresh, 602L))
    }

    @Test fun corruptedRestoredCounterFailsClosed() {
        assertNull(VisionPickerRequestGate(-1).begin(0L))
        assertNull(VisionPickerRequestGate(Int.MAX_VALUE).begin(0L))
        assertNull(VisionPickerRequestGate(VisionPickerRequestGate.REQUEST_COUNT).begin(0L))
    }

    @Test fun canceledRequestDoesNotRearmAfterAndroidLifecycleRestart() {
        val gate = VisionPickerRequestGate()
        val old = gate.begin(5L)!!
        gate.cancel()
        assertFalse(gate.consume(old, 6L))
        assertFalse(VisionPickerRequestGate().consume(old, 6L))
        val newId = gate.begin(7L)!!
        assertFalse(gate.consume(old, 8L))
        assertTrue(gate.consume(newId, 9L))
    }
}
