package com.cardenaspiero255.jarvis.core

/**
 * A unique request code per OS consent dialog, bounded to Activity lifetime.
 * Restoring the allocation counter never restores an authorization.
 */
class ScreenConsentRequestGate(initialIssuedCodes: Int = 0) {
    private var issuedCodes =
        if (initialIssuedCodes in 0..REQUEST_COUNT) initialIssuedCodes else REQUEST_COUNT
    private var pendingCode: Int? = null
    private var pendingAtMillis = 0L

    @Synchronized
    fun begin(nowMillis: Long): Int? {
        if (nowMillis < 0L || nowMillis > Long.MAX_VALUE - MAX_AGE_MILLIS) {
            cancel()
            return null
        }
        if (pendingCode != null && fresh(nowMillis)) return null
        pendingCode = null
        if (issuedCodes >= REQUEST_COUNT) return null
        val code = FIRST_REQUEST_CODE + issuedCodes++
        pendingCode = code
        pendingAtMillis = nowMillis
        return code
    }

    fun owns(code: Int) = code in FIRST_REQUEST_CODE..LAST_REQUEST_CODE

    @Synchronized
    fun consume(code: Int, nowMillis: Long): Boolean {
        if (!owns(code) || pendingCode != code) return false
        val accepted = fresh(nowMillis)
        cancel()
        return accepted
    }

    @Synchronized
    fun cancel() {
        pendingCode = null
        pendingAtMillis = 0L
    }

    @Synchronized
    fun issuedCount(): Int = issuedCodes

    private fun fresh(nowMillis: Long): Boolean =
        pendingCode != null && nowMillis >= pendingAtMillis &&
            nowMillis - pendingAtMillis < MAX_AGE_MILLIS

    companion object {
        const val FIRST_REQUEST_CODE = 8100
        const val LAST_REQUEST_CODE = 8999
        const val REQUEST_COUNT = LAST_REQUEST_CODE - FIRST_REQUEST_CODE + 1
        const val MAX_AGE_MILLIS = 180_000L
    }
}
