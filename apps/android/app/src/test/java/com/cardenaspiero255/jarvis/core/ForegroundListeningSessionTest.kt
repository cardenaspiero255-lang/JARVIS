package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundListeningSessionTest {
    @Test fun startsOnlyFromExplicitTapAndStopsAfterActivityLeaves() {
        val session = ForegroundListeningSession()
        assertFalse(session.active)
        session.enableByTap()
        assertTrue(session.active)
        session.stop()
        assertFalse(session.active)
        assertFalse(session.onResult())
    }

    @Test fun codeSevenAndSixAreUnrecognizedSpeechNotFatalErrors() {
        val session = ForegroundListeningSession()
        session.enableByTap()
        assertEquals(ListeningDecision.RETRY, session.onError(7))
        assertEquals(ListeningDecision.RETRY, session.onError(6))
        assertTrue(session.active)
        assertEquals(ListeningDecision.STOP, session.onError(7).let {
            session.onError(7); session.onError(7)
        })
        assertFalse(session.active)
    }

    @Test fun otherRecognitionFailuresStopInsteadOfLooping() {
        for (code in listOf(1, 2, 3, 5, 8, 9, 10, 11, 12, 13)) {
            val session = ForegroundListeningSession()
            session.enableByTap()
            assertEquals(ListeningDecision.STOP, session.onError(code))
            assertFalse(session.active)
        }
    }

    @Test fun confirmedResultsResetMissCounter() {
        val session = ForegroundListeningSession()
        session.enableByTap()
        repeat(4) { assertEquals(ListeningDecision.RETRY, session.onError(7)) }
        assertTrue(session.onResult())
        assertEquals(ListeningDecision.RETRY, session.onError(7))
        session.stop()
        assertFalse(session.onResult())
    }
}
