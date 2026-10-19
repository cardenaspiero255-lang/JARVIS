package com.cardenaspiero255.jarvis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke for actual Kotlin/JNI and both SHA-pinned model files.
 * It never opens a microphone or enrolls a person; real accents, false
 * triggers and replay rejection require a different held-out audio suite.
 */
@RunWith(AndroidJUnit4::class)
class SherpaNativeOfflineInstrumentedTest {
    @Test fun kwsLoadsVerifiedModelAndSilenceNeverCreatesWakeCandidate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var status = ""
        val bridge = SherpaNativeWakeBridge.tryOpen(context) { status = it }
        assertNotNull("KWS JNI did not initialize: $status", bridge)
        try {
            val samples = ShortArray(16000)
            assertFalse(bridge!!.consume(samples, samples.size,
                SystemClock.elapsedRealtime()))
        } finally {
            bridge?.close()
        }
    }

    @Test fun speakerExtractorLoadsVerifiedModelAndReportsEmbeddingDimension() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = VerifiedSpeakerModelStore(context).ensureInstalled()
        assertNotNull("Embedded speaker model failed SHA-256 verification", model)
        val extractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = model!!.absolutePath, numThreads = 2, provider = "cpu"
            )
        )
        try {
            assertTrue("Invalid model embedding dimension", extractor.dim() in 64..1024)
        } finally {
            extractor.release()
        }
    }

    @Test fun identityRemainsUnverifiedWithNoEnrollmentAndNoCalibration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = SherpaSpeakerEnrollmentEngine(context)
        assertEquals(LocalVoiceEnrollmentOutcome.DENIED,
            engine.enroll(consentGranted = false, recordings = emptyList()))
        assertEquals(com.cardenaspiero255.jarvis.core.SpeakerEvidence.UNAVAILABLE,
            engine.identityEvidenceWithoutCalibration(null))
    }
}
