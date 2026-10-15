package com.cardenaspiero255.jarvis.core

/**
 * In-memory cancellation of stale OCR result display. Does not persist grants
 * or authorize actions based on image contents.
 */
class VisionRequestGate {
    private var serial: Long = 0L
    private var active: Long? = null

    @Synchronized
    fun begin(): Long {
        serial += 1L
        active = serial
        return serial
    }

    @Synchronized
    fun accepts(requestId: Long): Boolean = active == requestId

    @Synchronized
    fun cancel() { active = null }
}
