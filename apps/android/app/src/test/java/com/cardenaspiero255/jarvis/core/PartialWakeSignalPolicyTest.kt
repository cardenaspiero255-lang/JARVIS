package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Unconfirmed partial ASR text may only raise a UI status, never grant actions. */
class PartialWakeSignalPolicyTest {
    @Test fun exactWakeProducesOnlyOneStatusUntilNextCompletedUtterance() {
        val policy = PartialWakeSignalPolicy()
        assertTrue(policy.shouldReport("jarvis"))
        assertFalse(policy.shouldReport("JARVIS"))
        policy.onUtteranceCompleted()
        assertTrue(policy.shouldReport("Jarvis"))
    }

    @Test fun otherSpeakersAndLongerCommandsGetNoEarlyAuthorization() {
        val policy = PartialWakeSignalPolicy()
        for (text in listOf("no jarvis", "jarvis para", "jarvis abre ajustes",
                "jarvis dime la hora", "oye jarvis", "no", "")) {
            assertFalse(policy.shouldReport(text))
        }
    }

    @Test fun onlyPunctuationAndCaseAreNormalized() {
        val policy = PartialWakeSignalPolicy()
        assertTrue(policy.shouldReport("¡JARVIS!"))
        policy.onUtteranceCompleted()
        assertFalse(policy.shouldReport("jarvisjarvis"))
    }

    @Test fun negativePartialDoesNotConsumeCandidate() {
        val policy = PartialWakeSignalPolicy()
        assertFalse(policy.shouldReport("hoy"))
        assertTrue(policy.shouldReport("Jarvis"))
    }
}
