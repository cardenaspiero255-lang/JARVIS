package com.cardenaspiero255.jarvis.core

/**
 * Local, one-use, bounded permission to hand off to the explicit GameHub
 * launcher. This is not authenticated IPC and grants no background authority.
 */
class GameHubPreflightGate {
    private var stagedAtMillis: Long? = null
    private var stagedPreference: GamingPreference? = null

    fun stageByTap(
        level: GameReadinessLevel,
        preference: GamingPreference,
        nowMillis: Long
    ): Boolean {
        if (nowMillis < 0 || nowMillis > Long.MAX_VALUE - MAX_WAIT_MS) {
            cancel()
            return false
        }
        if (level != GameReadinessLevel.READY && level != GameReadinessLevel.CAUTION) {
            cancel()
            return false
        }
        if (pending(nowMillis)) return false
        stagedAtMillis = nowMillis
        stagedPreference = preference
        return true
    }

    fun consumeByTap(currentPreference: GamingPreference, nowMillis: Long): Boolean {
        val accepted = pending(nowMillis) && currentPreference == stagedPreference
        cancel() // Atomic from UI main thread; no replay or automatic retry.
        return accepted
    }

    fun cancel() {
        stagedAtMillis = null
        stagedPreference = null
    }

    private fun pending(nowMillis: Long): Boolean {
        val start = stagedAtMillis ?: return false
        if (nowMillis < start || nowMillis - start >= MAX_WAIT_MS) {
            cancel()
            return false
        }
        return true
    }

    companion object {
        const val MAX_WAIT_MS = 30_000L
    }
}
