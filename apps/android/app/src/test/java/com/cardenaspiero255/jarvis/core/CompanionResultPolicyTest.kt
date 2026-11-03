package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionResultPolicyTest {
    @Test fun termuxNeverCountsIntentDispatchAsSuccess() {
        val state = TermuxResultTracker()
        val token = state.begin(1000L)
        assertEquals(TermuxResultState.WAITING, state.status(1001L))
        assertFalse(state.receive(token + 1, 0, -1, "uid=1000(test)", 1010L))
        assertEquals(TermuxResultState.WAITING, state.status(1010L))
        assertTrue(state.receive(token, 0, -1, "uid=1000(test) gid=1000(test)", 1011L))
        assertEquals(TermuxResultState.VERIFIED, state.status(1011L))
        assertFalse(state.receive(token, 0, -1, "uid=1000(test)", 1012L))
    }

    @Test fun termuxFailsClosedOnErrorTimeoutAndMalformedOutput() {
        val state = TermuxResultTracker()
        var token = state.begin(2000L)
        assertTrue(state.receive(token, 1, -1, "uid=1000(test)", 2001L))
        assertEquals(TermuxResultState.FAILED, state.status(2001L))
        token = state.begin(3000L)
        assertTrue(state.receive(token, 0, -1, "unexpected", 3002L))
        assertEquals(TermuxResultState.FAILED, state.status(3002L))
        token = state.begin(4000L)
        assertEquals(TermuxResultState.TIMED_OUT, state.status(70001L))
        assertFalse(state.receive(token, 0, -1, "uid=1000", 70002L))
        assertEquals(TermuxResultState.TIMED_OUT, state.status(70002L))
    }

    @Test fun termuxRequiresEntireAuthenticatedIdOutputNotJustUidPrefix() {
        val malformed = listOf(
            "uid=1000(test) INJECTED_PAYLOAD",
            "uid=1000(test) gid=1000(test) arbitrary",
            "uid=1000(test) gid=oops",
            "uid=1000(test) gid=1000(test)\nINJECTED",
            "uid=1000(test) groups=1000(test),oops",
            "uid=1000(test) context=u:r:untrusted_app:s0\nadmin"
        )
        for (stdout in malformed) {
            val state = TermuxResultTracker()
            val token = state.begin(1000L)
            assertTrue(state.receive(token, 0, -1, stdout, 1001L))
            assertEquals("Untrusted output must not be accepted: $stdout",
                TermuxResultState.FAILED, state.status(1001L))
            assertFalse(state.receive(token, 0, -1, stdout, 1002L))
        }
    }

    @Test fun termuxPreservesLegitimateIdFormatsAfterValidatingWholeLine() {
        val valid = listOf(
            "uid=1000(test)",
            "uid=1000(test) gid=1000(test)",
            "uid=1000(test) gid=1000(test) groups=1000(test),4(adm)",
            "uid=1000(test) gid=1000(test) groups=1000(test) context=u:r:untrusted_app:s0"
        )
        for (stdout in valid) {
            val state = TermuxResultTracker()
            val token = state.begin(2000L)
            assertTrue(state.receive(token, 0, -1, stdout, 2001L))
            assertEquals(TermuxResultState.VERIFIED, state.status(2001L))
        }
    }

    @Test fun lockOutcomeRequiresActualFreshDeviceLockObservation() {
        assertEquals(OwnDroidLockState.NOT_VERIFIED,
            OwnDroidLockEvidence.evaluate(sent = true, beforeLocked = false, afterLocked = false))
        assertEquals(OwnDroidLockState.OBSERVED_LOCKED,
            OwnDroidLockEvidence.evaluate(sent = true, beforeLocked = false, afterLocked = true))
        assertEquals(OwnDroidLockState.NOT_VERIFIED,
            OwnDroidLockEvidence.evaluate(sent = false, beforeLocked = false, afterLocked = true))
        assertEquals(OwnDroidLockState.INCONCLUSIVE,
            OwnDroidLockEvidence.evaluate(sent = true, beforeLocked = true, afterLocked = true))
    }
}
