package com.cardenaspiero255.jarvis.core

/** Stateless read-only fallback for manually captured voice math, never commands. */
object VoiceMathGate {
    fun answer(transcript: String?): String? {
        if (transcript.isNullOrBlank() || transcript.length > 160) return null
        return OfflineArithmetic.evaluate(transcript)?.answer
    }
}
