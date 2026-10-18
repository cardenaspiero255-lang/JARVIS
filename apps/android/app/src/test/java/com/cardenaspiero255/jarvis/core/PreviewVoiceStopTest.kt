package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** A safe, exact spoken STOP is accepted even when owner identity is unavailable. */
class PreviewVoiceStopTest {
    private val policy = HandsFreePreviewPolicy()

    @Test fun explicitStopIsAlwaysAvailableWithoutBiometricAuthority() {
        for (phrase in listOf("Jarvis para", "Jarvis, detente", "¡JARVIS, PARA!")) {
            assertEquals(PreviewWake.STOP, policy.classify(
                phrase, SpeakerEvidence.UNAVAILABLE, ownerOnly = true))
            assertEquals(PreviewWake.STOP, policy.classify(
                phrase, SpeakerEvidence.MISMATCH, ownerOnly = true))
        }
    }

    @Test fun stopInSecondUtteranceAfterWake() {
        val gate = PreviewWakeTurnGate()
        assertEquals(WakeTurnDecision.WAITING,
            gate.observe("Jarvis", 1_000L, SpeakerEvidence.UNAVAILABLE))
        assertEquals(WakeTurnDecision.STOP,
            gate.observe("detente", 2_000L, SpeakerEvidence.UNAVAILABLE))
        assertFalse(gate.waiting())
    }

    @Test fun negatedHypotheticalOrAmbiguousTextNeverTriggersStop() {
        for (phrase in listOf("Jarvis no pares", "No Jarvis detente",
                "Jarvis qué pasaría si paras", "Jarvis párate un ratito",
                "Para", "Jarvis detente y borra los archivos")) {
            assertEquals(PreviewWake.IGNORED,
                policy.classify(phrase, SpeakerEvidence.UNAVAILABLE))
        }
    }

    @Test fun pendingWakeExpiresAndStopRequiresWakeAgain() {
        val gate = PreviewWakeTurnGate()
        gate.observe("Jarvis", 100L, SpeakerEvidence.UNAVAILABLE)
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("para", 8_200L, SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun stopDoesNotPermitOtherActions() {
        val gate = PreviewWakeTurnGate()
        gate.observe("Jarvis", 100L, SpeakerEvidence.UNAVAILABLE)
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("ejecuta shell", 200L, SpeakerEvidence.UNAVAILABLE))
    }
}
