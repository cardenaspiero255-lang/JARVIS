package com.cardenaspiero255.jarvis.core

/** Bounded two-utterance follow-up for the existing 60-second offline preview.
 * This does not implement hotword KWS, voice verification, or a permanent session.
 */
enum class WakeTurnDecision { IGNORED, WAITING, LOCAL_TIME, STOP, BLOCKED_IDENTITY }

class PreviewWakeTurnGate(
    private val followupMillis: Long = 8_000L,
    private val policy: HandsFreePreviewPolicy = HandsFreePreviewPolicy()
) {
    init { require(followupMillis in 1L..30_000L) }
    private var activatedAt: Long? = null

    fun observe(text: String, now: Long, speaker: SpeakerEvidence): WakeTurnDecision {
        if (now < 0L) return WakeTurnDecision.IGNORED
        expiredAt(now)
        val direct = policy.classify(text, speaker)
        when (direct) {
            PreviewWake.STOP -> {
                activatedAt = null
                return WakeTurnDecision.STOP
            }
            PreviewWake.LOCAL_TIME -> {
                activatedAt = null
                return WakeTurnDecision.LOCAL_TIME
            }
            PreviewWake.BLOCKED_IDENTITY -> {
                activatedAt = null
                return WakeTurnDecision.BLOCKED_IDENTITY
            }
            PreviewWake.WAKE_ONLY -> {
                activatedAt = now
                return WakeTurnDecision.WAITING
            }
            PreviewWake.IGNORED -> Unit
        }
        val pending = activatedAt != null
        // A non-command utterance cancels the short follow-up window.
        activatedAt = null
        if (!pending || text.isBlank()) return WakeTurnDecision.IGNORED
        // Reuse existing exact command/identity policy; not additional authority.
        val followup = policy.classify("Jarvis " + text, speaker)
        return when (followup) {
            PreviewWake.STOP -> WakeTurnDecision.STOP
            PreviewWake.LOCAL_TIME -> WakeTurnDecision.LOCAL_TIME
            PreviewWake.BLOCKED_IDENTITY -> WakeTurnDecision.BLOCKED_IDENTITY
            else -> WakeTurnDecision.IGNORED
        }
    }

    fun expiredAt(now: Long): Boolean {
        val since = activatedAt ?: return false
        if (now < since || now - since >= followupMillis) {
            activatedAt = null
            return true
        }
        return false
    }

    fun waiting(): Boolean = activatedAt != null
}
