package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class PreviewWakeTurnGateTest {
    @Test fun separatedWakeAndReadOnlyClockWithinEightSeconds() {
        val gate = PreviewWakeTurnGate()
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("dime la hora", 1_000L, SpeakerEvidence.UNAVAILABLE))
        assertEquals(WakeTurnDecision.WAITING,
            gate.observe("Jarvis", 2_000L, SpeakerEvidence.UNAVAILABLE))
        assertTrue(gate.waiting())
        assertEquals(WakeTurnDecision.LOCAL_TIME,
            gate.observe("¿Qué hora es?", 5_000L, SpeakerEvidence.UNAVAILABLE))
        assertFalse(gate.waiting())
    }

    @Test fun wholeUtteranceStillWorks() {
        assertEquals(WakeTurnDecision.LOCAL_TIME,
            PreviewWakeTurnGate().observe("Jarvis, dime la hora", 300L, SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun expiredWakeNeverGrantsFollowupWithoutNewWake() {
        val gate = PreviewWakeTurnGate()
        assertEquals(WakeTurnDecision.WAITING,
            gate.observe("Jarvis", 1_000L, SpeakerEvidence.UNAVAILABLE))
        assertTrue(gate.expiredAt(9_000L))
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("dime la hora", 9_001L, SpeakerEvidence.UNAVAILABLE))
        assertFalse(gate.waiting())
    }

    @Test fun negativeInstructionCancelsPendingWake() {
        val gate = PreviewWakeTurnGate()
        gate.observe("Jarvis", 1_000L, SpeakerEvidence.UNAVAILABLE)
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("no dime la hora", 1_200L, SpeakerEvidence.UNAVAILABLE))
        assertFalse(gate.waiting())
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("dime la hora", 1_500L, SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun knownBadOrReplayedSpeakerIsRejected() {
        val gate = PreviewWakeTurnGate()
        assertEquals(WakeTurnDecision.BLOCKED_IDENTITY,
            gate.observe("Jarvis", 1_000L, SpeakerEvidence.REPLAY_SUSPECTED))
        assertEquals(WakeTurnDecision.WAITING,
            gate.observe("Jarvis", 2_000L, SpeakerEvidence.UNAVAILABLE))
        assertEquals(WakeTurnDecision.BLOCKED_IDENTITY,
            gate.observe("dime la hora", 2_500L, SpeakerEvidence.MISMATCH))
        assertFalse(gate.waiting())
    }

    @Test fun unsafeCommandsNeverBecomeEnabled() {
        val gate = PreviewWakeTurnGate()
        gate.observe("Jarvis", 1_000L, SpeakerEvidence.UNAVAILABLE)
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("activa Shizuku", 2_000L, SpeakerEvidence.UNAVAILABLE))
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("jarvis abre GameHub", 2_100L, SpeakerEvidence.UNAVAILABLE))
    }
}
