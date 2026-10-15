package com.cardenaspiero255.jarvis.core

/**
 * One-use local consent for a user-selected document-picker result.
 *
 * Android's legacy onActivityResult callback lacks a request identity beyond
 * requestCode. This gate allocates a fresh code for each attempted selection to
 * prevent an older picker callback from consuming a newer authorization.
 * No background capture, image storage or device-control capabilities.
 */
class VisionPickerRequestGate(initialIssuedCodes: Int = 0) {
    // Restoring a counter never restores the previous Activity's consent.
    // Invalid saved state exhausts the range, rather than reusing older codes.
    private var issuedCodes =
        if (initialIssuedCodes in 0..REQUEST_COUNT) initialIssuedCodes else REQUEST_COUNT
    private var pendingCode: Int? = null
    private var pendingAtMillis = 0L

    /** Save only monotonic allocation progress across Activity recreation. */
    @Synchronized
    fun issuedCount(): Int = issuedCodes

    /** Only a visible picker button can call this; return null if unavailable. */
    @Synchronized
    fun begin(nowMillis: Long): Int? {
        if (nowMillis < 0L || nowMillis > Long.MAX_VALUE - MAX_AGE_MILLIS) {
            cancel()
            return null
        }
        if (pendingCode != null && fresh(nowMillis)) return null
        // Expired, interrupted or impossible clock states cannot rearm old codes.
        pendingCode = null
        if (issuedCodes >= REQUEST_COUNT) return null
        val code = FIRST_REQUEST_CODE + issuedCodes++
        pendingCode = code
        pendingAtMillis = nowMillis
        return code
    }

    fun owns(requestCode: Int): Boolean =
        requestCode in FIRST_REQUEST_CODE..LAST_REQUEST_CODE

    /** Unmatched callbacks do not consume a fresh request. */
    @Synchronized
    fun consume(requestCode: Int, nowMillis: Long): Boolean {
        if (!owns(requestCode) || pendingCode != requestCode) return false
        val accepted = fresh(nowMillis)
        cancel() // Always one use, including stale/cancelled callbacks.
        return accepted
    }

    @Synchronized
    fun cancel() {
        pendingCode = null
        pendingAtMillis = 0L
    }

    private fun fresh(nowMillis: Long): Boolean {
        if (pendingCode == null || nowMillis < pendingAtMillis) return false
        val elapsed = nowMillis - pendingAtMillis
        return elapsed >= 0L && elapsed < MAX_AGE_MILLIS
    }

    companion object {
        const val FIRST_REQUEST_CODE = 7002
        const val LAST_REQUEST_CODE = 7999
        const val REQUEST_COUNT = LAST_REQUEST_CODE - FIRST_REQUEST_CODE + 1
        const val MAX_AGE_MILLIS = 300_000L
    }
}
