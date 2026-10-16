package com.cardenaspiero255.jarvis.core

/** A Termux START_SERVICE acknowledgement is not execution evidence. */
enum class TermuxResultState { IDLE, WAITING, VERIFIED, FAILED, TIMED_OUT }

/** One of two hardcoded read-only Termux requests; never derived from voice input. */
enum class TermuxProbe(val executable: String, val arguments: List<String>, val label: String) {
    IDENTITY("/data/data/com.termux/files/usr/bin/id", emptyList(), "Identidad Termux"),
    ANDROID_VERSION("/system/bin/getprop", listOf("ro.build.version.release"), "Versión Android")
}

class TermuxResultTracker {
    private var nextToken = 1000
    private var activeToken: Int? = null
    private var requestedAt = 0L
    private var current = TermuxResultState.IDLE
    private var activeProbe: TermuxProbe? = null
    private var validatedAndroidVersion: String? = null
    private val versionPattern = Regex("^[1-9][0-9]?(?:\\.[0-9]{1,2}){0,2}$")
    private val uidPattern = Regex("^uid=[0-9]+(?:\\([^)]{1,64}\\))?(?:\\s|$)")

    @Synchronized
    fun begin(nowElapsedMs: Long, probe: TermuxProbe = TermuxProbe.IDENTITY): Int {
        activeProbe = probe
        validatedAndroidVersion = null
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
        val trimmed = stdout.trim()
        val validOutput = stdout.length <= 256 && when (activeProbe) {
            TermuxProbe.IDENTITY -> uidPattern.containsMatchIn(trimmed)
            TermuxProbe.ANDROID_VERSION -> versionPattern.matches(trimmed)
            null -> false
        }
        current = if (exitCode == 0 && errCode == -1 && validOutput)
            TermuxResultState.VERIFIED else TermuxResultState.FAILED
        validatedAndroidVersion = if (current == TermuxResultState.VERIFIED &&
            activeProbe == TermuxProbe.ANDROID_VERSION) trimmed else null
        activeToken = null
        return true
    }

    /** Ephemeral evidence for this process, never a general privileged-access claim. */
    @Synchronized
    fun verifiedProbe(nowElapsedMs: Long): TermuxProbe? =
        activeProbe.takeIf { status(nowElapsedMs) == TermuxResultState.VERIFIED }

    @Synchronized
    fun verifiedAndroidVersion(nowElapsedMs: Long): String? =
        validatedAndroidVersion.takeIf { status(nowElapsedMs) == TermuxResultState.VERIFIED }

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
