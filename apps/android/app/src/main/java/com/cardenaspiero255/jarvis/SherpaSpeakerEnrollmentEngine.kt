package com.cardenaspiero255.jarvis

import android.content.Context
import android.os.Looper
import com.cardenaspiero255.jarvis.core.SpeakerEnrollmentPolicy
import com.cardenaspiero255.jarvis.core.SpeakerEnrollmentSample
import com.cardenaspiero255.jarvis.core.SpeakerEvidence
import com.cardenaspiero255.jarvis.core.VoiceMatchDecisionPolicy
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.util.concurrent.atomic.AtomicBoolean

data class LocalVoiceEnrollmentRecording(
    val recordingId: String,
    val samples16kPcm: ShortArray
)
enum class LocalVoiceEnrollmentOutcome { SAVED, DENIED, BAD_SAMPLES, NATIVE_UNAVAILABLE }
/** Real model-derived consistency only. Never an identity decision. */
data class LocalVoiceEnrollmentDiagnostics(val minimumPairwiseCosine: Double)

/**
 * Connects real sherpa-onnx speaker embeddings to the existing AES-GCM
 * AndroidSpeakerTemplateVault. Enrollment is an explicitly opted-in operation,
 * NOT a voice login or liveness guarantee; neither PCM nor transcripts persist.
 */
class SherpaSpeakerEnrollmentEngine(private val context: Context) {
    private val busy = AtomicBoolean(false)

    fun enroll(
        consentGranted: Boolean,
        recordings: List<LocalVoiceEnrollmentRecording>,
        isActive: () -> Boolean = { true },
        onDiagnostics: (LocalVoiceEnrollmentDiagnostics) -> Unit = {}
    ): LocalVoiceEnrollmentOutcome {
        if (!consentGranted || !isActive()) return LocalVoiceEnrollmentOutcome.DENIED
        // Native ONNX and first-install SHA verification must never block UI.
        if (Looper.myLooper() == Looper.getMainLooper())
            return LocalVoiceEnrollmentOutcome.DENIED
        if (recordings.size != 3 || recordings.map { it.recordingId }.distinct().size != 3 ||
            recordings.any {
                it.recordingId.isBlank() ||
                it.samples16kPcm.size !in 24_000..160_000
            }) return LocalVoiceEnrollmentOutcome.BAD_SAMPLES
        if (!busy.compareAndSet(false, true))
            return LocalVoiceEnrollmentOutcome.DENIED

        var extractor: SpeakerEmbeddingExtractor? = null
        val embeddings = mutableListOf<SpeakerEnrollmentSample>()
        return try {
            val file = VerifiedSpeakerModelStore(context).ensureInstalled()
                ?: return LocalVoiceEnrollmentOutcome.NATIVE_UNAVAILABLE
            extractor = SpeakerEmbeddingExtractor(
                config = SpeakerEmbeddingExtractorConfig(
                    model = file.absolutePath, numThreads = 2,
                    debug = false, provider = "cpu"
                )
            )
            val native = extractor
            for (recording in recordings) {
                if (Thread.currentThread().isInterrupted || !isActive())
                    return LocalVoiceEnrollmentOutcome.DENIED
                val stream = native.createStream()
                try {
                    val audio = FloatArray(recording.samples16kPcm.size) {
                        recording.samples16kPcm[it] / 32768f
                    }
                    try {
                        stream.acceptWaveform(audio, 16_000)
                        stream.inputFinished()
                    } finally {
                        audio.fill(0f)
                    }
                    if (!native.isReady(stream))
                        return LocalVoiceEnrollmentOutcome.BAD_SAMPLES
                    val vector = native.compute(stream)
                    if (vector.size != native.dim())
                        return LocalVoiceEnrollmentOutcome.BAD_SAMPLES
                    embeddings += SpeakerEnrollmentSample(
                        recording.recordingId,
                        recording.samples16kPcm.size * 1000L / 16000L,
                        vector
                    )
                } finally {
                    stream.release()
                }
            }
            val template = SpeakerEnrollmentPolicy().create(embeddings)
                ?: return LocalVoiceEnrollmentOutcome.BAD_SAMPLES
            // Compare the THREE authentic embeddings before erasing the
            // temporary vectors. Consistency is not FAR/FRR or liveness.
            val pairs = listOf(0 to 1, 0 to 2, 1 to 2)
            val minimumPairwiseCosine = pairs.minOf { (a, b) ->
                OwnerVoiceCosine.compare(embeddings[a].embedding, embeddings[b].embedding)
                    ?: return LocalVoiceEnrollmentOutcome.BAD_SAMPLES
            }
            if (Thread.currentThread().isInterrupted || !isActive()) {
                template.fill(0f)
                return LocalVoiceEnrollmentOutcome.DENIED
            }
            AndroidSpeakerTemplateVault(context).save(
                VerifiedSpeakerModelStore.MODEL_SHA256, template
            )
            template.fill(0f)
            try {
                onDiagnostics(LocalVoiceEnrollmentDiagnostics(minimumPairwiseCosine))
            } catch (_: Exception) { /* Diagnostics never decide authorization. */ }
            LocalVoiceEnrollmentOutcome.SAVED
        } catch (_: UnsatisfiedLinkError) {
            LocalVoiceEnrollmentOutcome.NATIVE_UNAVAILABLE
        } catch (_: Exception) {
            LocalVoiceEnrollmentOutcome.NATIVE_UNAVAILABLE
        } finally {
            embeddings.forEach { it.embedding.fill(0f) }
            try { extractor?.release() } catch (_: Exception) {}
            busy.set(false)
        }
    }

    /**
     * Voice Match is still UNAVAILABLE until FAR/FRR, impostor and recorded
     * replay tests independently validate thresholds for this precise model.
     * Similarity alone never grants an owner-only capability.
     */
    fun identityEvidenceWithoutCalibration(probe: FloatArray?): SpeakerEvidence {
        val enrolled = AndroidSpeakerTemplateVault(context).load(
            VerifiedSpeakerModelStore.MODEL_SHA256
        )
        return VoiceMatchDecisionPolicy().classify(
            enrolled, probe, modelAvailable = enrolled != null,
            calibrated = false
        ) // Always SpeakerEvidence.UNAVAILABLE; never MATCHED.
    }

    fun revoke(): Unit = AndroidSpeakerTemplateVault(context).revoke()
}
