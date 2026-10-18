package com.cardenaspiero255.jarvis.core

import kotlin.math.sqrt

/**
 * Enrollment accepts only independently collected, long-enough snippets
 * represented by output vectors of a real (future) on-device speaker model.
 * Similar vectors do NOT authenticate a live caller.
 */
data class SpeakerEnrollmentSample(
    val recordingId: String,
    val durationMillis: Long,
    val embedding: FloatArray
)

class SpeakerEnrollmentPolicy {
    fun create(samples: List<SpeakerEnrollmentSample>): FloatArray? {
        if (samples.size != REQUIRED_RECORDINGS ||
            samples.map { it.recordingId }.any { it.isBlank() } ||
            samples.map { it.recordingId }.distinct().size != REQUIRED_RECORDINGS ||
            samples.any { it.durationMillis < MIN_DURATION_MILLIS }
        ) return null
        val count = samples[0].embedding.size
        if (count !in MIN_DIMENSION..MAX_DIMENSION ||
            samples.any { it.embedding.size != count }) return null
        val normalized = samples.map { normalized(it.embedding) ?: return null }
        for (a in normalized.indices) {
            for (b in a + 1 until normalized.size) {
                val similarity = normalized[a].indices.sumOf {
                    normalized[a][it].toDouble() * normalized[b][it].toDouble()
                }
                // Conservative consistency only. THIS IS NOT an authentication
                // acceptance threshold and must never substitute FAR/FRR tuning.
                if (similarity < MIN_ENROLLMENT_CONSISTENCY) return null
            }
        }
        val averaged = FloatArray(count) { k ->
            normalized.sumOf { it[k].toDouble() }.div(normalized.size).toFloat()
        }
        return normalized(averaged)
    }

    private fun normalized(vector: FloatArray): FloatArray? {
        if (vector.any { !it.isFinite() }) return null
        val squared = vector.sumOf { it.toDouble() * it.toDouble() }
        if (!squared.isFinite() || squared <= 1e-10) return null
        val norm = sqrt(squared)
        return FloatArray(vector.size) { i -> (vector[i] / norm).toFloat() }
    }

    private companion object {
        const val REQUIRED_RECORDINGS = 3
        const val MIN_DURATION_MILLIS = 1500L
        const val MIN_DIMENSION = 64
        const val MAX_DIMENSION = 1024
        const val MIN_ENROLLMENT_CONSISTENCY = 0.78
    }
}

/**
 * This type cannot mark a caller MATCHED until a model-specific independent
 * calibration supplies valid FAR/FRR thresholds and robust replay testing.
 * No such calibration exists in this Android preview.
 */
class VoiceMatchDecisionPolicy {
    fun classify(
        template: FloatArray?,
        probe: FloatArray?,
        modelAvailable: Boolean,
        calibrated: Boolean
    ): SpeakerEvidence {
        if (!modelAvailable || !calibrated || template == null || probe == null) {
            return SpeakerEvidence.UNAVAILABLE
        }
        if (template.size != probe.size || template.size !in 64..1024 ||
            template.any { !it.isFinite() } || probe.any { !it.isFinite() }) {
            return SpeakerEvidence.UNCERTAIN
        }
        // Deliberate fail closed until confidence calibration is implemented.
        return SpeakerEvidence.UNCERTAIN
    }
}
