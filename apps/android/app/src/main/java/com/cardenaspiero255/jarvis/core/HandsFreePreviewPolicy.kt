package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/** Voice biometrics are not implemented in this preview.
 * UNAVAILABLE MUST NOT be treated as MATCHED. Future speaker verification
 * plugs in here, independently of hotword detection and ASR.
 */
enum class SpeakerEvidence { UNAVAILABLE, MATCHED, MISMATCH, UNCERTAIN, REPLAY_SUSPECTED }
enum class PreviewWake { IGNORED, WAKE_ONLY, LOCAL_TIME, BLOCKED_IDENTITY }
enum class PreviewStart { ALLOWED, DENIED, OFFLINE_ENGINE_MISSING }

/**
 * A strictly bounded capability: one explicit, on-device Spanish ASR request,
 * accepting only the wake phrase or a read-only time request. This does NOT
 * implement always-on hotwording, diarization or speaker identification.
 */
class HandsFreePreviewPolicy {
    fun canStart(
        explicitUserTap: Boolean,
        micGranted: Boolean,
        notificationsGranted: Boolean,
        offlineRecognizerAvailable: Boolean
    ): PreviewStart = when {
        !explicitUserTap || !micGranted || !notificationsGranted -> PreviewStart.DENIED
        !offlineRecognizerAvailable -> PreviewStart.OFFLINE_ENGINE_MISSING
        else -> PreviewStart.ALLOWED
    }

    fun classify(
        transcript: String,
        speaker: SpeakerEvidence,
        ownerOnly: Boolean = false
    ): PreviewWake {
        if (transcript.isBlank() || transcript.length > 160) return PreviewWake.IGNORED
        val phrase = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val action = when (phrase) {
            "jarvis" -> PreviewWake.WAKE_ONLY
            "jarvis dime la hora", "jarvis que hora es" -> PreviewWake.LOCAL_TIME
            else -> PreviewWake.IGNORED
        }
        if (action == PreviewWake.IGNORED) return action
        // A wake word is NOT biometric proof. Fail closed for owner-only mode
        // until a separately reviewed native speaker verifier supplies evidence.
        if (speaker == SpeakerEvidence.MISMATCH || speaker == SpeakerEvidence.UNCERTAIN ||
            speaker == SpeakerEvidence.REPLAY_SUSPECTED ||
            (ownerOnly && speaker != SpeakerEvidence.MATCHED)
        ) return PreviewWake.BLOCKED_IDENTITY
        return action
    }
}

/** Uses elapsedRealtime on Android; no persisted enrollment or service state. */
class PreviewTimeGate(private val durationMillis: Long) {
    private var startedAt: Long? = null

    fun begin(nowMillis: Long): Boolean {
        if (nowMillis < 0L || durationMillis <= 0 || startedAt != null) return false
        startedAt = nowMillis
        return true
    }

    fun isActiveAt(nowMillis: Long): Boolean {
        val began = startedAt ?: return false
        if (nowMillis < began || nowMillis < 0L) return false
        val elapsed = nowMillis - began
        return elapsed >= 0L && elapsed < durationMillis
    }

    fun stop() { startedAt = null }
}
