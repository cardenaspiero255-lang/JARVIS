package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class SpeakerEnrollmentPolicyTest {
    private fun sample(id: String, vector: FloatArray, duration: Long = 2200) =
        SpeakerEnrollmentSample(id, duration, vector)

    @Test fun insufficientOrRepeatedSamplesFailClosed() {
        val p = SpeakerEnrollmentPolicy()
        val a = floatArrayOf(0.7f, 0.3f, 0.2f, 0.5f)
        assertNull(p.create(listOf(sample("1", a), sample("2", a))))
        assertNull(p.create(listOf(sample("1", a), sample("1", a), sample("2", a))))
    }

    @Test fun tooShortOrInvalidAudioCannotEnroll() {
        val p = SpeakerEnrollmentPolicy()
        val a = FloatArray(64) { if (it == 0) 1f else 0.1f }
        assertNull(p.create(listOf(
            sample("a", a, 300), sample("b", a), sample("c", a))))
        val corrupted = a.copyOf().apply { this[5] = Float.NaN }
        assertNull(p.create(listOf(
            sample("a", a), sample("b", corrupted), sample("c", a))))
    }

    @Test fun mismatchedDimensionsOrTinyEmbeddingsAreRejected() {
        val p = SpeakerEnrollmentPolicy()
        val a = FloatArray(64) { if (it == 0) 1f else 0.1f }
        val b = FloatArray(128) { 0.1f }
        assertNull(p.create(listOf(
            sample("a", a), sample("b", b), sample("c", a))))
        assertNull(p.create(listOf(
            sample("a", FloatArray(64)), sample("b", FloatArray(64)),
            sample("c", FloatArray(64)))))
        assertNull(p.create(listOf(
            sample("a", floatArrayOf(1f, 0f)), sample("b", floatArrayOf(1f, 0f)),
            sample("c", floatArrayOf(1f, 0f)))))
    }

    @Test fun independentSimilarEmbeddingsCreateNormalizedPrivateTemplate() {
        val a = FloatArray(64) { if (it == 0) 0.9f else 0.1f }
        val b = FloatArray(64) { if (it == 1) 0.3f else 0.15f }.apply { this[0]=0.95f }
        val c = FloatArray(64) { if (it == 2) 0.3f else 0.15f }.apply { this[0]=0.95f }
        val t = SpeakerEnrollmentPolicy().create(listOf(
            sample("a", a), sample("b", b), sample("c", c)))
        assertNotNull(t)
        assertEquals(64, t!!.size)
        assertEquals(1.0, kotlin.math.sqrt(t.map { (it * it).toDouble() }.sum()), 0.00001)
    }

    @Test fun failedSpeakerPolicyCannotGrantOwnerPrivilegesWithoutModelCalibration() {
        val p = VoiceMatchDecisionPolicy()
        val a = FloatArray(64) { if (it == 0) 1f else 0f }
        assertEquals(SpeakerEvidence.UNAVAILABLE,
            p.classify(a, a, modelAvailable = false, calibrated = false))
        assertEquals(SpeakerEvidence.UNAVAILABLE,
            p.classify(a, a, modelAvailable = true, calibrated = false))
        assertEquals(SpeakerEvidence.UNCERTAIN,
            p.classify(a, FloatArray(64), modelAvailable = true, calibrated = true))
        assertEquals(SpeakerEvidence.UNCERTAIN,
            p.classify(a, a, modelAvailable = true, calibrated = true))
        // Similarity without a held-out threshold is never MATCHED.
    }
}
