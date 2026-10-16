package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxDiagnosticAllowlistTest {
    @Test fun commandDefinitionsAreHardcodedAndReadOnly() {
        assertEquals("/data/data/com.termux/files/usr/bin/id", TermuxProbe.IDENTITY.executable)
        assertEquals(emptyList<String>(), TermuxProbe.IDENTITY.arguments)
        assertEquals("/system/bin/getprop", TermuxProbe.ANDROID_VERSION.executable)
        assertEquals(listOf("ro.build.version.release"), TermuxProbe.ANDROID_VERSION.arguments)
    }

    @Test fun aVersionIsVerifiedOnlyFromMatchingCallback() {
        val tracker = TermuxResultTracker()
        val token = tracker.begin(100L, TermuxProbe.ANDROID_VERSION)
        assertEquals(TermuxResultState.WAITING, tracker.status(101L))
        assertNull(tracker.verifiedProbe(101L))
        assertFalse(tracker.receive(token + 1, 0, -1, "14\n", 102L))
        assertTrue(tracker.receive(token, 0, -1, "14\n", 103L))
        assertEquals(TermuxResultState.VERIFIED, tracker.status(104L))
        assertEquals(TermuxProbe.ANDROID_VERSION, tracker.verifiedProbe(104L))
        assertEquals("14", tracker.verifiedAndroidVersion(104L))
    }

    @Test fun malformedOrHugeVersionCannotProveSuccess() {
        val tracker = TermuxResultTracker()
        val token = tracker.begin(100L, TermuxProbe.ANDROID_VERSION)
        assertTrue(tracker.receive(token, 0, -1, "uid=1000(termux)", 101L))
        assertEquals(TermuxResultState.FAILED, tracker.status(101L))
        assertNull(tracker.verifiedAndroidVersion(101L))
        val token2 = tracker.begin(200L, TermuxProbe.ANDROID_VERSION)
        assertTrue(tracker.receive(token2, 0, -1, "A".repeat(1000), 201L))
        assertEquals(TermuxResultState.FAILED, tracker.status(201L))
    }

    @Test fun expiredRequestsAndWrongResultTypesDoNotVerify() {
        val tracker = TermuxResultTracker()
        val t = tracker.begin(50L, TermuxProbe.ANDROID_VERSION)
        assertEquals(TermuxResultState.TIMED_OUT, tracker.status(60052L))
        assertFalse(tracker.receive(t, 0, -1, "14", 60053L))
        assertNull(tracker.verifiedProbe(60053L))
    }

    @Test fun newVoiceCommandsOnlyReturnPlans() {
        assertEquals(SafeRoutine.TERMUX_ID_PROBE,
            SafeAutomationVoiceRouter.parse("Jarvis, prueba Termux"))
        assertEquals(SafeRoutine.TERMUX_VERSION_PROBE,
            SafeAutomationVoiceRouter.parse("Jarvis, consulta versión Android con Termux"))
        assertEquals(SafeRoutine.TERMUX_LAST_RESULT,
            SafeAutomationVoiceRouter.parse("Jarvis, revisa conexión Termux"))
        assertNull(SafeAutomationVoiceRouter.parse("Jarvis, ejecuta rm -rf con Termux"))
        assertNull(SafeAutomationVoiceRouter.parse("Jarvis, no pruebes Termux"))
    }

    @Test fun newTermuxPlansCannotRunWithoutOneTap() {
        val g = object: SafeAutomationGateway {
            var dispatched = 0
            override fun perform(step: SafeAutomationStep): Boolean {
                dispatched += 1
                return true
            }
        }
        val engine = SafeAutomationEngine(g)
        engine.prepare(SafeRoutine.TERMUX_ID_PROBE)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.TERMUX_ID_PROBE, false).outcome)
        assertEquals(0, g.dispatched)
        assertEquals(SafeAutomationOutcome.COMPLETED,
            engine.confirmAndRun(SafeRoutine.TERMUX_ID_PROBE, true).outcome)
        assertEquals(1, g.dispatched)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.TERMUX_ID_PROBE, true).outcome)
    }
}
