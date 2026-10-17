package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class HandsFreeScreenOffRegressionTest {
    private val recovery = PreviewAsrRecoveryPolicy()

    @Test fun silenceAndNoMatchDoNotKillPreviewImmediately() {
        for (code in listOf(6, 7)) {
            val next = recovery.afterError(code, attemptsSoFar = 1, remainingMillis = 45000)
            assertEquals(PreviewAsrDecision.RETRY, next.decision)
            assertTrue(next.delayMillis >= 500L)
            assertTrue(next.delayMillis < 5000L)
        }
    }

    @Test fun neverRetriesForeverOrBeyondDeadline() {
        assertEquals(PreviewAsrDecision.STOP,
            recovery.afterError(6, attemptsSoFar = 6, remainingMillis = 45000).decision)
        assertEquals(PreviewAsrDecision.STOP,
            recovery.afterError(7, attemptsSoFar = 2, remainingMillis = 900).decision)
        assertEquals(PreviewAsrDecision.STOP,
            recovery.afterError(6, attemptsSoFar = Int.MAX_VALUE, remainingMillis = Long.MAX_VALUE).decision)
    }

    @Test fun permanentErrorsFailClosedAndExplainCause() {
        val expected = mapOf(
            9 to "PERMISSION", 12 to "LANGUAGE", 13 to "LANGUAGE",
            1 to "NETWORK", 2 to "NETWORK"
        )
        for ((errorCode, explanation) in expected) {
            val next = recovery.afterError(errorCode, attemptsSoFar = 1, remainingMillis = 45000)
            assertEquals(PreviewAsrDecision.STOP, next.decision)
            assertTrue(next.reasonCode.contains(explanation))
        }
    }

    @Test fun oemRecognizerDisconnectedCanRetryOnlyWithinShortBound() {
        assertEquals(PreviewAsrDecision.RETRY,
            recovery.afterError(11, attemptsSoFar = 1, remainingMillis = 45000).decision)
        assertEquals(PreviewAsrDecision.STOP,
            recovery.afterError(11, attemptsSoFar = 3, remainingMillis = 45000).decision)
    }

    @Test fun persistedActiveFlagNotMistakenForKnownOemCause() {
        val state = PreviewDiagnosticSummary.visible(
            wasMarkedActive = true, serviceRunning = false,
            lastEvent = "Escucha activa"
        )
        assertTrue(state.contains("interrumpida"))
        assertTrue(state.contains("no permite confirmar"))
        assertFalse(state.contains("Funtouch cerró"))
    }

    @Test fun normalTimeoutAndStopStayClear() {
        assertEquals("Prueba terminada a los 60 s.",
            PreviewDiagnosticSummary.visible(false, false, "Prueba terminada a los 60 s."))
        assertEquals("Micrófono en uso.",
            PreviewDiagnosticSummary.visible(true, true, "Micrófono en uso."))
    }
}
