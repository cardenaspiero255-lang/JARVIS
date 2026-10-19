package com.cardenaspiero255.jarvis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.PreviewWake
import com.cardenaspiero255.jarvis.core.SpeakerEvidence
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * JNI real-model regression with reproducible SYNTHETIC PCM.
 *
 * It proves native inference stability and safe policy boundaries, NOT actual
 * human voice recognition/owner FAR/FRR/anti-replay or real Vivo endurance.
 */
@RunWith(AndroidJUnit4::class)
class NativeVoiceStressInstrumentedTest {
    @Test fun repeatedOnnxSilenceOpenDecodeCloseNeverCrashesOrWakes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repeat(3) { iteration ->
            var reason = "not started"
            val bridge = SherpaNativeWakeBridge.tryOpen(context) { reason = it }
            assertNotNull("Cycle $iteration cannot load verified ONNX model: $reason", bridge)
            try {
                val silence = ShortArray(1600)
                repeat(14) {
                    assertFalse("Synthetic silence must not trigger native KWS",
                        bridge!!.consume(silence, silence.size,
                            SystemClock.elapsedRealtime()))
                }
                // Invalid count can never become a wake candidate.
                assertFalse(bridge!!.consume(silence, -1, SystemClock.elapsedRealtime()))
                assertFalse(bridge.consume(silence, silence.size + 1,
                    SystemClock.elapsedRealtime()))
            } finally {
                bridge?.close()
            }
        }
    }

    @Test fun variedSyntheticNonSpeechDoesNotElevateIdentityAuthorization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var reason = ""
        val bridge = SherpaNativeWakeBridge.tryOpen(context) { reason = it }
        assertNotNull("Missing native KWS: $reason", bridge)
        try {
            // Deterministic amplitude-limited noise exercises native PCM tensor path.
            // It is not a genuine negative speech corpus. We do not assert its FAR.
            var seed = 0x12345678
            val noise = ShortArray(1600) {
                seed = seed * 1664525 + 1013904223
                ((seed ushr 20) % 400 - 200).toShort()
            }
            repeat(12) {
                bridge!!.consume(noise, noise.size, SystemClock.elapsedRealtime())
            }
            val policy = HandsFreePreviewPolicy()
            for (identity in arrayOf(SpeakerEvidence.UNAVAILABLE,
                SpeakerEvidence.REPLAY_SUSPECTED, SpeakerEvidence.MISMATCH,
                SpeakerEvidence.UNCERTAIN)) {
                assertEquals(PreviewWake.BLOCKED_IDENTITY, policy.classify(
                    "Jarvis, dime la hora", identity, ownerOnly = true))
            }
        } finally {
            bridge?.close()
        }
    }
}
