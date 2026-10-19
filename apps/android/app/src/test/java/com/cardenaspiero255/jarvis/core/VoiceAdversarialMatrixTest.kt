package com.cardenaspiero255.jarvis.core

import com.cardenaspiero255.jarvis.OwnerVoiceCosine
import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic high-volume edge-case regression; *zero* microphone/device use.
 * This proves parser/privacy/clock invariants, NOT real speech recognition.
 */
class VoiceAdversarialMatrixTest {
    private val policy = HandsFreePreviewPolicy()

    @Test fun emergencyStopWorksEvenWithUnverifiedOrReplayedVoice() {
        for (speaker in SpeakerEvidence.values()) {
            for (utterance in arrayOf("Jarvis, para", "Jarvis detente")) {
                assertEquals(PreviewWake.STOP, policy.classify(
                    utterance, speaker, ownerOnly = true))
            }
        }
    }

    @Test fun noVoiceCanElevateOwnerOnlyCommandWithoutIndependentMatch() {
        for (speaker in arrayOf(SpeakerEvidence.UNAVAILABLE,
            SpeakerEvidence.MISMATCH, SpeakerEvidence.REPLAY_SUSPECTED,
            SpeakerEvidence.UNCERTAIN)) {
            assertEquals(PreviewWake.BLOCKED_IDENTITY, policy.classify(
                "jarvis dime la hora", speaker, ownerOnly = true))
            assertEquals(PreviewWake.BLOCKED_IDENTITY, policy.classify(
                "jarvis", speaker, ownerOnly = true))
        }
        assertEquals(PreviewWake.LOCAL_TIME, policy.classify(
            "jarvis dime la hora", SpeakerEvidence.UNAVAILABLE, ownerOnly = false))
    }

    @Test fun invalidAndNegatedRequestsNeverBecomeSensitiveCommands() {
        for (phrase in arrayOf(
            "No desactives X4", "Jarvis no desactives X4",
            "Si activara X4 que pasaria", "Jarvis abre mi banco",
            "Jarvis envia todas mis fotos", "Jarvis borra memoria",
            "Jarvis autoriza Termux", "Jarvis toca el boton pagar",
            "JARVIS " + "A".repeat(200)
        )) {
            assertEquals(PreviewWake.IGNORED,
                policy.classify(phrase, SpeakerEvidence.MATCHED))
        }
    }

    @Test fun wakeWindowExpiresAtExactEightSecondsAndDoesNotStick() {
        val time = 3_000_000L
        val gate = PreviewWakeTurnGate()
        assertEquals(WakeTurnDecision.WAITING,
            gate.observe("jarvis", time, SpeakerEvidence.UNAVAILABLE))
        assertTrue(gate.waiting())
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("dime la hora", time + 8_000L, SpeakerEvidence.UNAVAILABLE))
        assertFalse(gate.waiting())
        assertEquals(WakeTurnDecision.IGNORED,
            gate.observe("dime la hora", time + 8_100L, SpeakerEvidence.UNAVAILABLE))
        assertEquals(WakeTurnDecision.STOP,
            gate.observe("jarvis para", time + 8_200L,
                SpeakerEvidence.REPLAY_SUSPECTED))
    }

    @Test fun deterministicTenThousandWakeCyclesDoNotLeakPreviousAuthorization() {
        val gate = PreviewWakeTurnGate()
        var time = 0L
        repeat(10_000) { index ->
            time += 9_000L
            assertEquals(WakeTurnDecision.WAITING,
                gate.observe("Jarvis", time, SpeakerEvidence.UNAVAILABLE))
            if (index % 2 == 0) {
                assertEquals(WakeTurnDecision.LOCAL_TIME,
                    gate.observe("dime la hora", time + 200L, SpeakerEvidence.UNAVAILABLE))
            } else {
                assertEquals(WakeTurnDecision.IGNORED,
                    gate.observe("dime la hora", time + 8_500L,
                        SpeakerEvidence.UNAVAILABLE))
            }
            assertFalse(gate.waiting())
        }
    }

    @Test fun virtualThirtyMinuteSessionHasHardBoundary() {
        val gate = PreviewTimeGate(30 * 60_000L)
        val started = 52_000L
        assertTrue(gate.begin(started))
        assertFalse(gate.begin(started + 1))
        assertTrue(gate.isActiveAt(started))
        assertTrue(gate.isActiveAt(started + 30 * 60_000L - 1))
        assertEquals(1L, gate.remainingMillis(started + 30 * 60_000L - 1))
        assertFalse(gate.isActiveAt(started + 30 * 60_000L))
        assertEquals(0L, gate.remainingMillis(started + 30 * 60_000L))
        gate.stop()
        assertFalse(gate.isActiveAt(started + 30 * 60_000L + 1))
    }

    @Test fun virtualThirtyMinuteScreenOffEvidenceNeverClaimsOwnerIdentity() {
        val gate = LockScreenAudioEvidence()
        var t = 100L
        repeat(720) { i -> // 720 virtual 2.5s chunks = 30 min
            gate.observe(t, 40_000L, interactive = i < 2)
            t += 2_500L
        }
        val snap = gate.snapshot()
        assertEquals(LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED, snap.state)
        assertEquals(718, snap.screenOffObservations)
        assertEquals(718L * 40_000L, snap.screenOffSamples)
        gate.reset()
        assertEquals(LockScreenAudioState.NO_SCREEN_OFF_AUDIO, gate.snapshot().state)
    }

    @Test fun syntheticBiometricVectorsAreNotEvidenceOfOwner() {
        val owner = FloatArray(256) { if (it % 2 == 0) 0.2f else -0.4f }
        val other = FloatArray(256) { -owner[it] }
        assertEquals(1.0, OwnerVoiceCosine.compare(owner, owner)!!, 1e-8)
        assertEquals(-1.0, OwnerVoiceCosine.compare(owner, other)!!, 1e-8)
        val decision = VoiceMatchDecisionPolicy()
        assertEquals(SpeakerEvidence.UNAVAILABLE,
            decision.classify(owner, owner, modelAvailable = true, calibrated = false))
        // Even a synthetic perfect match must not get privileges from the model.
        assertEquals(SpeakerEvidence.UNCERTAIN,
            decision.classify(owner, owner, modelAvailable = true, calibrated = true))
    }
}
