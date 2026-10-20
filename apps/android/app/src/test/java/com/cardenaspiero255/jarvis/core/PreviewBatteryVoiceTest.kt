package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Public, strictly read-only battery status by local Spanish speech. */
class PreviewBatteryVoiceTest {
    private val policy = HandsFreePreviewPolicy()

    @Test fun directBatteryRequestsAreExactAndSafe() {
        for (phrase in listOf(
            "Jarvis batería", "Jarvis cuánta batería tengo",
            "Jarvis dime la batería"
        )) assertEquals(PreviewWake.LOCAL_BATTERY,
            policy.classify(phrase, SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun isolatedWakeAllowsSingleBoundedBatteryFollowup() {
        val gate = PreviewWakeTurnGate()
        assertEquals(WakeTurnDecision.WAITING,
            gate.observe("Jarvis", 500L, SpeakerEvidence.UNAVAILABLE))
        assertEquals(WakeTurnDecision.LOCAL_BATTERY,
            gate.observe("cuanta bateria tengo", 3_500L, SpeakerEvidence.UNAVAILABLE))
        assertFalse(gate.waiting())
    }

    @Test fun negativeAndCompoundPhrasesCannotBecomeBatteryCommands() {
        for (phrase in listOf(
            "no jarvis bateria", "jarvis no me digas la bateria",
            "jarvis bateria y enciende x4", "jarvis borra todo y bateria",
            "cuanta bateria tengo", "jarvis bateria por favor",
            "que pasaria si jarvis bateria"
        )) assertEquals(PreviewWake.IGNORED,
            policy.classify(phrase, SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun speakerMismatchAndOwnerOnlyStayFailClosed() {
        assertEquals(PreviewWake.BLOCKED_IDENTITY,
            policy.classify("Jarvis bateria", SpeakerEvidence.MISMATCH))
        assertEquals(PreviewWake.BLOCKED_IDENTITY,
            policy.classify("Jarvis bateria", SpeakerEvidence.UNAVAILABLE, ownerOnly = true))
        assertEquals(PreviewWake.STOP,
            policy.classify("Jarvis detente", SpeakerEvidence.MISMATCH, ownerOnly = true))
    }

    @Test fun expiredWindowCannotAuthorizeBattery() {
        val gate = PreviewWakeTurnGate()
        gate.observe("Jarvis", 1_000L, SpeakerEvidence.UNAVAILABLE)
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("bateria", 9_500L, SpeakerEvidence.UNAVAILABLE))
    }
}
