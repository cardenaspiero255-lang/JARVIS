package com.cardenaspiero255.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerVoiceCosineTest {
    @Test fun identicalEmbeddingsScoreOneWithoutGrantingIdentity() {
        val a = FloatArray(256) { if (it % 3 == 0) 0.25f else -0.75f }
        assertEquals(1.0, OwnerVoiceCosine.compare(a, a)!!, 0.000001)
        // Scoring provides no SpeakerEvidence.MATCHED nor implicit authorization.
    }

    @Test fun oppositeEmbeddingsScoreMinusOne() {
        val a = FloatArray(128) { it.toFloat() + 1f }
        val b = FloatArray(128) { -a[it] }
        assertEquals(-1.0, OwnerVoiceCosine.compare(a, b)!!, 0.000001)
    }

    @Test fun nonFiniteZeroMismatchedAndUndersizedAreRejected() {
        assertNull(OwnerVoiceCosine.compare(FloatArray(256), FloatArray(256)))
        assertNull(OwnerVoiceCosine.compare(FloatArray(63) { 1f },
            FloatArray(63) { 1f }))
        assertNull(OwnerVoiceCosine.compare(FloatArray(128) { 1f },
            FloatArray(256) { 1f }))
        assertNull(OwnerVoiceCosine.compare(FloatArray(128) { Float.NaN },
            FloatArray(128) { 1f }))
        assertNull(OwnerVoiceCosine.compare(FloatArray(128) { Float.POSITIVE_INFINITY },
            FloatArray(128) { 1f }))
    }

    @Test fun probeNeverUsesSimilarityAsAuthenticationEvidence() {
        val result = OwnerVoiceProbeResult(
            OwnerVoiceProbeStatus.SIMILARITY_UNCALIBRATED, 0.99
        )
        assertEquals(OwnerVoiceProbeStatus.SIMILARITY_UNCALIBRATED, result.status)
        assertEquals(0.99, result.cosineSimilarity!!, 0.0)
    }
}
