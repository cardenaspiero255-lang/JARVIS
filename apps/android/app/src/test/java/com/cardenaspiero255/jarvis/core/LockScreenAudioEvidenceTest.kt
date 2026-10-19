package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** No microphone or private voice samples; deterministic elapsedRealtime inputs. */
class LockScreenAudioEvidenceTest {
    @Test fun screenOffFramesProvideEvidenceWithoutAudioContent() {
        val evidence = LockScreenAudioEvidence()
        evidence.observe(1000L, 16000, interactive = true)
        assertEquals(LockScreenAudioState.NO_SCREEN_OFF_AUDIO, evidence.snapshot().state)
        evidence.observe(2000L, 8000, interactive = false)
        evidence.observe(3000L, 8000, interactive = false)
        val snapshot = evidence.snapshot()
        assertEquals(LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED, snapshot.state)
        assertEquals(16000L, snapshot.screenOffSamples)
        assertEquals(2, snapshot.screenOffObservations)
        assertEquals(3000L, snapshot.lastScreenOffElapsedMs)
    }

    @Test fun invalidOrRegressingSamplesNeverCreateFalseProof() {
        val evidence = LockScreenAudioEvidence()
        evidence.observe(1000L, 8000, interactive = false)
        evidence.observe(900L, 16000, interactive = false)
        evidence.observe(2000L, 0, interactive = false)
        evidence.observe(3000L, -1, interactive = false)
        assertEquals(LockScreenAudioState.INSUFFICIENT_EVIDENCE, evidence.snapshot().state)
        assertEquals(8000L, evidence.snapshot().screenOffSamples)
    }

    @Test fun screenOffActiveDoesNotProveOwnerVoiceOrWakeDetection() {
        val evidence = LockScreenAudioEvidence()
        evidence.observe(2000L, 16000, interactive = false)
        assertEquals(LockScreenAudioState.INSUFFICIENT_EVIDENCE, evidence.snapshot().state)
        evidence.observe(4000L, 16000, interactive = false)
        assertEquals(LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED, evidence.snapshot().state)
        evidence.reset()
        assertEquals(LockScreenAudioState.NO_SCREEN_OFF_AUDIO, evidence.snapshot().state)
    }
}
