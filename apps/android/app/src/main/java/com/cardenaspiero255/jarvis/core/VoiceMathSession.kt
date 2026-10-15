package com.cardenaspiero255.jarvis.core

import java.math.BigDecimal

/**
 * Ephemeral, numeric-only arithmetic context for an explicitly initiated
 * Android speech-recognition session. No transcripts, files, network or commands.
 */
class VoiceMathSession {
    private var previous: BigDecimal? = null
    private var previousAtMillis: Long = 0L

    fun answer(transcript: String?, nowMillis: Long): String? {
        if (nowMillis < 0L || transcript.isNullOrBlank() || transcript.length > 160) {
            clear()
            return null
        }
        val elapsed = nowMillis - previousAtMillis
        val freshValue = previous?.takeIf {
            nowMillis >= previousAtMillis && elapsed >= 0L && elapsed < MAX_AGE_MILLIS
        }
        // Only numeric data, never speech transcripts, are used as context.
        val evaluation = OfflineArithmetic.evaluate(transcript, freshValue)
        if (evaluation?.value != null) {
            previous = evaluation.value
            previousAtMillis = nowMillis
        } else {
            clear() // Invalid/non-math/new instructions cannot preserve a stale result.
        }
        return evaluation?.answer
    }

    fun clear() {
        previous = null
        previousAtMillis = 0L
    }

    companion object {
        const val MAX_AGE_MILLIS = 45_000L
    }
}
