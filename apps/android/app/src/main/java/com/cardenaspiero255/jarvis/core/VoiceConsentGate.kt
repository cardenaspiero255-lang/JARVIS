package com.cardenaspiero255.jarvis.core

/**
 * Local, ephemeral confirmation for a speech-originated request.
 *
 * Voice transcripts are untrusted input; recording a request here never
 * authorizes an action. The user must press the confirmation button while
 * the foreground Activity still owns this gate.
 */
enum class VoiceRequestedAction { ACCESSIBILITY_SETTINGS, GAMEHUB_ULTRA_LAUNCH }

class VoiceConsentGate {
    private var requestedAction: VoiceRequestedAction? = null
    private var requestedAtMillis: Long = 0L

    fun request(action: VoiceRequestedAction, nowMillis: Long): Boolean {
        cancel()
        if (nowMillis < 0L) return false
        requestedAction = action
        requestedAtMillis = nowMillis
        return true
    }

    fun hasPending(action: VoiceRequestedAction, nowMillis: Long): Boolean =
        requestedAction == action && isFresh(nowMillis)

    fun consume(action: VoiceRequestedAction, nowMillis: Long): Boolean {
        val approved = requestedAction == action && isFresh(nowMillis)
        cancel() // Always single-use, including invalid, stale or wrong requests.
        return approved
    }

    fun cancel() {
        requestedAction = null
        requestedAtMillis = 0L
    }

    private fun isFresh(nowMillis: Long): Boolean {
        if (requestedAction == null || nowMillis < requestedAtMillis) return false
        val elapsed = nowMillis - requestedAtMillis
        // Also rejects signed overflow from untrusted clocks.
        return elapsed >= 0L && elapsed < MAX_AGE_MILLIS
    }

    private companion object {
        const val MAX_AGE_MILLIS = 30_000L
    }
}
