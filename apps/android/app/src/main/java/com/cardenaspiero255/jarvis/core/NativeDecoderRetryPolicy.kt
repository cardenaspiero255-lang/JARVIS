package com.cardenaspiero255.jarvis.core

import java.io.IOException

/** At most one local recovery of a Vosk model/recognizer initialization error.
 * A microphone permission failure, ABI mismatch or cancelled session cannot retry.
 */
class NativeDecoderRetryPolicy {
    private var used = false

    fun shouldRebuild(cause: Throwable, isActive: Boolean): Boolean {
        if (used || !isActive || cause !is IOException) return false
        used = true
        return true
    }
}
