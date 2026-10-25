package com.cardenaspiero255.jarvis.core

/**
 * Tracks a single explicitly requested TTS utterance.
 * All callbacks must match the most recent ticket; stale callbacks are ignored.
 */
class SpeechUtteranceTracker {
    private enum class Phase { QUEUED, STARTED }
    private var nextId = 0L
    private var currentId: String? = null
    private var phase: Phase? = null

    @Synchronized
    fun begin(): String? {
        if (nextId == Long.MAX_VALUE) return null
        val id = "jarvis-omega-" + (++nextId)
        currentId = id
        phase = Phase.QUEUED
        return id
    }

    @Synchronized
    fun started(id: String?): Boolean {
        if (id == null || id != currentId || phase != Phase.QUEUED) return false
        phase = Phase.STARTED
        return true
    }

    @Synchronized
    fun done(id: String?): Boolean {
        if (id == null || id != currentId || phase != Phase.STARTED) return false
        cancel()
        return true
    }

    @Synchronized
    fun failed(id: String?): Boolean {
        if (id == null || id != currentId) return false
        cancel()
        return true
    }

    @Synchronized
    fun timedOut(id: String?): Boolean {
        if (id == null || id != currentId || phase != Phase.QUEUED) return false
        cancel()
        return true
    }

    /** A separate deadline bounds a started engine that never reports completion. */
    @Synchronized
    fun playbackTimedOut(id: String?): Boolean {
        if (id == null || id != currentId || phase != Phase.STARTED) return false
        cancel()
        return true
    }

    @Synchronized
    fun cancel() {
        currentId = null
        phase = null
    }
}
