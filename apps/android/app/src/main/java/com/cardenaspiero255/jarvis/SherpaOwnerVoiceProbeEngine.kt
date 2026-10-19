package com.cardenaspiero255.jarvis

import android.content.Context
import android.os.Looper
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlin.math.sqrt

/** Diagnostic only: scores an independent utterance against a private owner
 * enrollment template. Cosine similarity is NOT live identity, anti-replay,
 * or authorization. No user-facing threshold and no MATCHED evidence.
 */
enum class OwnerVoiceProbeStatus {
    NO_ENROLLMENT, BAD_AUDIO, NATIVE_UNAVAILABLE, SIMILARITY_UNCALIBRATED
}
data class OwnerVoiceProbeResult(
    val status: OwnerVoiceProbeStatus,
    val cosineSimilarity: Double? = null
)

/** Pure math can be unit tested without JNI or personal voice samples. */
object OwnerVoiceCosine {
    fun compare(enrolled: FloatArray, embedding: FloatArray): Double? {
        if (enrolled.size !in 64..1024 || enrolled.size != embedding.size ||
            enrolled.any { !it.isFinite() } || embedding.any { !it.isFinite() }
        ) return null
        var dot = 0.0
        var first = 0.0
        var second = 0.0
        for (i in enrolled.indices) {
            val a = enrolled[i].toDouble()
            val b = embedding[i].toDouble()
            dot += a * b
            first += a * a
            second += b * b
        }
        if (first < 1e-12 || second < 1e-12 || !first.isFinite() ||
            !second.isFinite()) return null
        val cosine = dot / sqrt(first * second)
        return if (cosine.isFinite()) cosine.coerceIn(-1.0, 1.0) else null
    }
}

class SherpaOwnerVoiceProbeEngine(private val context: Context) {
    fun probe(
        samples16kPcm: ShortArray,
        isActive: () -> Boolean = { true }
    ): OwnerVoiceProbeResult {
        if (Looper.myLooper() == Looper.getMainLooper() || !isActive() ||
            samples16kPcm.size !in 40_000..160_000) {
            return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.BAD_AUDIO)
        }
        val template = AndroidSpeakerTemplateVault(context).load(
            VerifiedSpeakerModelStore.MODEL_SHA256
        ) ?: return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.NO_ENROLLMENT)

        var extractor: SpeakerEmbeddingExtractor? = null
        try {
            if (!isActive()) return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.BAD_AUDIO)
            val modelFile = VerifiedSpeakerModelStore(context).ensureInstalled()
                ?: return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.NATIVE_UNAVAILABLE)
            extractor = SpeakerEmbeddingExtractor(
                config = SpeakerEmbeddingExtractorConfig(
                    model = modelFile.absolutePath,
                    numThreads = 2, debug = false, provider = "cpu"
                )
            )
            val engine = extractor
            val stream = engine.createStream()
            try {
                val input = FloatArray(samples16kPcm.size) {
                    samples16kPcm[it] / 32768f
                }
                try {
                    stream.acceptWaveform(input, 16_000)
                    stream.inputFinished()
                } finally {
                    input.fill(0f)
                }
                if (!engine.isReady(stream) || !isActive())
                    return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.BAD_AUDIO)
                val embedding = engine.compute(stream)
                try {
                    if (embedding.size != engine.dim() || !isActive())
                        return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.BAD_AUDIO)
                    val score = OwnerVoiceCosine.compare(template, embedding)
                        ?: return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.BAD_AUDIO)
                    return OwnerVoiceProbeResult(
                        OwnerVoiceProbeStatus.SIMILARITY_UNCALIBRATED, score
                    )
                } finally {
                    embedding.fill(0f)
                }
            } finally {
                stream.release()
            }
        } catch (_: Exception) {
            return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.NATIVE_UNAVAILABLE)
        } catch (_: UnsatisfiedLinkError) {
            return OwnerVoiceProbeResult(OwnerVoiceProbeStatus.NATIVE_UNAVAILABLE)
        } finally {
            template.fill(0f)
            try { extractor?.release() } catch (_: Exception) {}
        }
    }
}
