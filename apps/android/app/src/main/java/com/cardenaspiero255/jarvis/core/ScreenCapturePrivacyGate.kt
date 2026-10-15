package com.cardenaspiero255.jarvis.core

/**
 * Local one-shot exception for user-visible Android screenshot debugging.
 * Starts secure; no persistence, network, timers or implicit permissions.
 */
class ScreenCapturePrivacyGate {
    private var allowedSinceMillis: Long? = null

    fun allowForDebugByTap(nowMillis: Long): Boolean {
        if (nowMillis < 0L || nowMillis > Long.MAX_VALUE - MAX_WINDOW_MS) return false
        if (captureAllowed(nowMillis)) return false
        allowedSinceMillis = nowMillis
        return true
    }

    /** Monotonic elapsed-time guard; rollback, expiry, or clock overflow fail closed. */
    fun captureAllowed(nowMillis: Long): Boolean {
        val start = allowedSinceMillis ?: return false
        if (nowMillis < start || nowMillis - start >= MAX_WINDOW_MS) {
            revoke()
            return false
        }
        return true
    }

    fun revoke() { allowedSinceMillis = null }

    companion object {
        private const val MAX_WINDOW_MS = 60_000L
    }
}
