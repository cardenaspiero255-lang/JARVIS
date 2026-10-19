package com.cardenaspiero255.jarvis.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Separates decoding from TTS playback: while the model is generating,
 * Vosk may still recognize an exact, owner-independent emergency STOP.
 * No other speech can schedule a second model request or an Android action.
 *
 * TTS playback remains separately muted to avoid acoustic feedback.
 */
class OfflineVoiceInferenceGate {
    private val decoding = AtomicBoolean(false)
    private val stopPolicy = HandsFreePreviewPolicy()

    fun beginInference(): Boolean = decoding.compareAndSet(false, true)

    fun inferenceActive(): Boolean = decoding.get()

    fun isEmergencyStop(utterance: String): Boolean =
        decoding.get() && stopPolicy.classify(
            utterance, SpeakerEvidence.UNAVAILABLE
        ) == PreviewWake.STOP

    fun finishInference() { decoding.set(false) }
}
