package com.cardenaspiero255.jarvis.core

/** Explicit, in-memory foreground-only microphone opt-in. No background service. */
enum class ListeningDecision { RETRY, STOP }

class ForegroundListeningSession {
    var active: Boolean = false
        private set
    private var missed = 0

    fun enableByTap() {
        missed = 0
        active = true
    }

    fun stop() {
        active = false
        missed = 0
    }

    /** Only after a consumed foreground-gated Android speech result. */
    fun onResult(): Boolean {
        if (!active) return false
        missed = 0
        return true
    }

    /** ERROR_NO_MATCH=7 and ERROR_SPEECH_TIMEOUT=6 are not privilege failures. */
    fun onError(error: Int): ListeningDecision {
        if (!active) return ListeningDecision.STOP
        if (error != 7 && error != 6) {
            stop()
            return ListeningDecision.STOP
        }
        missed++
        if (missed >= MAX_MISSED_ROUNDS) {
            stop()
            return ListeningDecision.STOP
        }
        return ListeningDecision.RETRY
    }

    companion object {
        /** Back off after repeated silence to prevent OEM speech service throttling. */
        const val MAX_MISSED_ROUNDS = 5
        const val RETRY_DELAY_MS = 1500L
        const val AFTER_REPLY_DELAY_MS = 5000L
    }
}
