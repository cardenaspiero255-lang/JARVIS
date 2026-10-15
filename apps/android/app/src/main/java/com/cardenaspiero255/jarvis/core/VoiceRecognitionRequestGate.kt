package com.cardenaspiero255.jarvis.core

/**
 * Accept a platform speech-recognizer callback only after a visible user request.
 * A request expires and can produce at most one result; no transcripts are kept.
 */
class VoiceRecognitionRequestGate {
    private var startedAtMillis: Long? = null

    fun begin(nowMillis: Long): Boolean {
        if (nowMillis < 0L) {
            cancel()
            return false
        }
        if (isPending(nowMillis)) return false
        startedAtMillis = nowMillis
        return true
    }

    fun isPending(nowMillis: Long): Boolean {
        val started = startedAtMillis ?: return false
        if (nowMillis < started) return false
        val elapsed = nowMillis - started
        return elapsed >= 0L && elapsed < MAX_RESULT_AGE_MILLIS
    }

    fun consume(nowMillis: Long): Boolean {
        val accepted = isPending(nowMillis)
        cancel() // Reject duplicates, stale callbacks and replays.
        return accepted
    }

    fun cancel() {
        startedAtMillis = null
    }

    companion object {
        const val MAX_RESULT_AGE_MILLIS = 180_000L
    }
}
