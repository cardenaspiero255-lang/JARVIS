package com.cardenaspiero255.jarvis.core

/**
 * Owns a single JNI inference slot until worker teardown actually completes.
 * Future.cancel/isDone cannot be the source of truth: a cancelled Future may
 * report done while native llama_decode is still running.
 */
class LocalGgufRunState {
    private var busy = false
    private var closed = false

    @Synchronized fun tryBegin(): Boolean {
        if (busy || closed) return false
        busy = true
        return true
    }

    @Synchronized fun finish() {
        busy = false
    }

    @Synchronized fun isBusy(): Boolean = busy

    @Synchronized fun close() {
        closed = true
    }
}
