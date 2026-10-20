package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/** Voice biometrics are not implemented in this preview.
 * UNAVAILABLE MUST NOT be treated as MATCHED. Future speaker verification
 * plugs in here, independently of hotword detection and ASR.
 */
enum class SpeakerEvidence { UNAVAILABLE, MATCHED, MISMATCH, UNCERTAIN, REPLAY_SUSPECTED }
enum class PreviewWake { IGNORED, WAKE_ONLY, LOCAL_TIME, LOCAL_BATTERY, STOP, BLOCKED_IDENTITY }
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
            "jarvis bateria", "jarvis cuanta bateria tengo",
            "jarvis dime la bateria" -> PreviewWake.LOCAL_BATTERY
            "jarvis para", "jarvis detente" -> PreviewWake.STOP
            else -> PreviewWake.IGNORED
        }
        if (action == PreviewWake.IGNORED) return action
        // Emergency STOP revokes the local microphone session; never use voice
        // identity as a prerequisite for cancelling microphone access.
        if (action == PreviewWake.STOP) return PreviewWake.STOP
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

    fun remainingMillis(nowMillis: Long): Long {
        val began = startedAt ?: return 0L
        if (nowMillis < began || nowMillis < 0L) return 0L
        val elapsed = nowMillis - began
        return if (elapsed < 0L || elapsed >= durationMillis) 0L
            else durationMillis - elapsed
    }

    fun stop() { startedAt = null }
}

/** Recover only from short-lived local ASR silence/disconnect, with a strict limit.
 * Android SpeechRecognizer is NOT a permanent keyword-spotting engine.
 */
enum class PreviewAsrDecision { RETRY, STOP }
data class PreviewAsrResult(
    val decision: PreviewAsrDecision,
    val reasonCode: String,
    val delayMillis: Long = 0L
)

class PreviewAsrRecoveryPolicy {
    fun afterError(errorCode: Int, attemptsSoFar: Int, remainingMillis: Long): PreviewAsrResult {
        fun stop(code: String) = PreviewAsrResult(PreviewAsrDecision.STOP, code)
        if (remainingMillis <= 2100L) return stop("DEADLINE")
        if (attemptsSoFar < 1 || attemptsSoFar >= MAX_ATTEMPTS) return stop("RETRY_LIMIT")
        return when (errorCode) {
            6, 7 -> PreviewAsrResult(
                PreviewAsrDecision.RETRY, "SILENCE_OR_NO_MATCH",
                minOf(3500L, 1400L + attemptsSoFar * 350L)
            )
            // Local recognizer occasionally reports a binder disconnect on lock.
            5, 8, 11 -> if (attemptsSoFar < 3) PreviewAsrResult(
                PreviewAsrDecision.RETRY, "LOCAL_RECOGNIZER_INTERRUPTED", 1800L
            ) else stop("RECOGNIZER_UNAVAILABLE")
            9 -> stop("PERMISSION_REVOKED")
            12, 13 -> stop("LANGUAGE_OFFLINE_UNAVAILABLE")
            1, 2 -> stop("NETWORK_ENGINE_UNEXPECTED")
            3 -> stop("AUDIO_INPUT_FAILURE")
            else -> stop("ASR_ERROR_$errorCode")
        }.let { result ->
            if (result.decision == PreviewAsrDecision.RETRY &&
                result.delayMillis + 700L >= remainingMillis) stop("DEADLINE")
            else result
        }
    }
    private companion object { const val MAX_ATTEMPTS = 6 }
}

/** A persisted ACTIVE state after process death does not prove Funtouch killed it. */
object PreviewDiagnosticSummary {
    fun visible(wasMarkedActive: Boolean, serviceRunning: Boolean, lastEvent: String): String {
        if (wasMarkedActive && !serviceRunning) {
            return "La sesión fue interrumpida sin cierre confirmado; " +
                "el registro no permite confirmar si Android, Funtouch u otra causa la detuvo. " +
                "Último estado: $lastEvent"
        }
        return lastEvent
    }
}
