package com.cardenaspiero255.jarvis.core

/** A Termux START_SERVICE acknowledgement is not execution evidence. */
enum class TermuxResultState { IDLE, WAITING, VERIFIED, FAILED, TIMED_OUT }

class TermuxResultTracker {
    private var nextToken = 1000
    private var activeToken: Int? = null
    private var requestedAt = 0L
    private var current = TermuxResultState.IDLE
    private val uidPattern = Regex("^uid=[0-9]+(?:\\([^)]{1,64}\\))?(?:\\s|$)")

    @Synchronized
    fun begin(nowElapsedMs: Long): Int {
        nextToken = if (nextToken == Int.MAX_VALUE) 1000 else nextToken + 1
        activeToken = nextToken
        requestedAt = nowElapsedMs
        current = TermuxResultState.WAITING
        return nextToken
    }

    @Synchronized
    fun cancel(token: Int) {
        if (activeToken == token && current == TermuxResultState.WAITING) {
            activeToken = null
            current = TermuxResultState.FAILED
        }
    }

    @Synchronized
    fun receive(
        token: Int,
        exitCode: Int,
        errCode: Int,
        stdout: String,
        nowElapsedMs: Long
    ): Boolean {
        if (status(nowElapsedMs) != TermuxResultState.WAITING ||
            activeToken != token) return false
        val outputIsUidOnly = stdout.length <= 256 &&
            uidPattern.containsMatchIn(stdout.trim())
        current = if (exitCode == 0 && errCode == -1 && outputIsUidOnly)
            TermuxResultState.VERIFIED else TermuxResultState.FAILED
        activeToken = null
        return true
    }

    @Synchronized
    fun status(nowElapsedMs: Long): TermuxResultState {
        if (current == TermuxResultState.WAITING &&
            (nowElapsedMs < requestedAt || nowElapsedMs - requestedAt > 60_000L)) {
            current = TermuxResultState.TIMED_OUT
            activeToken = null
        }
        return current
    }
}

enum class OwnDroidLockState { NOT_VERIFIED, OBSERVED_LOCKED, INCONCLUSIVE }

/** Observed locked != cryptographic proof that OwnDroid executed LOCK. */
object OwnDroidLockEvidence {
    fun evaluate(sent: Boolean, beforeLocked: Boolean, afterLocked: Boolean): OwnDroidLockState =
        when {
            !sent || !afterLocked -> OwnDroidLockState.NOT_VERIFIED
            beforeLocked -> OwnDroidLockState.INCONCLUSIVE
            else -> OwnDroidLockState.OBSERVED_LOCKED
        }
}
