package com.cardenaspiero255.jarvis.core

/**
 * Ephemeral permission to sample an Android MediaProjection session.
 * Consent is a *per-session* UI/OS event; never backed up or persisted.
 */
class ScreenObservationPolicy {
    private var started: Long? = null
    private var sampledAt: Long? = null

    @Synchronized
    fun start(explicitUserAndOsConsent: Boolean, nowMillis: Long): Boolean {
        if (!explicitUserAndOsConsent || nowMillis < 0L || started != null) return false
        started = nowMillis
        sampledAt = null
        return true
    }

    @Synchronized
    fun isActive(nowMillis: Long): Boolean {
        val origin = started ?: return false
        if (nowMillis < origin) return false
        val elapsed = nowMillis - origin
        return elapsed >= 0 && elapsed < MAX_SESSION_MILLIS
    }

    @Synchronized
    fun sample(nowMillis: Long): Boolean {
        if (!isActive(nowMillis)) return false
        val last = sampledAt
        if (last != null && nowMillis - last in 0L until MIN_SAMPLE_INTERVAL_MILLIS) {
            return false
        }
        sampledAt = nowMillis
        return true
    }

    @Synchronized
    fun stop() {
        started = null
        sampledAt = null
    }

    companion object {
        const val MIN_SAMPLE_INTERVAL_MILLIS = 3_000L
        const val MAX_SESSION_MILLIS = 600_000L
    }
}
