package com.cardenaspiero255.jarvis.core

/**
 * Captures only bounded PCM frame counts while Android reports a locked screen.
 * The screen state is sampled by the AudioRecord worker just after each read,
 * not by a delayed main-thread callback. Never stores samples/audio content.
 */
class LockScreenCaptureAccumulator {
    private var pendingScreenOff = 0L

    @Synchronized
    fun observe(samples: Int, interactive: Boolean) {
        if (samples !in 1..160_000 || interactive) return
        pendingScreenOff = (pendingScreenOff + samples).coerceAtMost(160_000L)
    }

    @Synchronized
    fun drainScreenOff(): Long {
        val value = pendingScreenOff
        pendingScreenOff = 0L
        return value
    }
}
