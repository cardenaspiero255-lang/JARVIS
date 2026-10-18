package com.cardenaspiero255.jarvis

import android.content.Context
import com.cardenaspiero255.jarvis.core.KwsCandidateGate
import com.cardenaspiero255.jarvis.core.KwsDecision
import com.cardenaspiero255.jarvis.core.KwsReadiness
import com.cardenaspiero255.jarvis.core.OfflineKwsModelGate
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import java.io.File
import java.nio.file.Files

/**
 * Optional real sherpa-onnx 1.13.8 JNI KWS reader. It consumes the SAME PCM
 * already captured by the authorized foreground Vosk session, never starts
 * another microphone, never persists speech, and never authorizes commands.
 *
 * No model is shipped yet: until independently pinned model files and a
 * text2token-produced 'Jarvis' keywords.txt have been provisioned in app-private
 * no-backup storage, [tryOpen] refuses initialization and Vosk still works.
 */
class SherpaNativeWakeBridge private constructor(
    private val spotter: KeywordSpotter,
    private val stream: OnlineStream
) : AutoCloseable {
    private val gate = KwsCandidateGate()
    private var closed = false

    /** Wake is advisory only. Sensitive commands require separate authorization. */
    fun consume(samples: ShortArray, count: Int, elapsedRealtimeMs: Long): Boolean {
        if (closed || count <= 0 || count > samples.size) return false
        val pcm = FloatArray(count) { samples[it] / 32768.0f }
        stream.acceptWaveform(pcm, sampleRate = SAMPLE_RATE)
        var loops = 0
        var candidate = false
        while (loops++ < MAX_DECODE_LOOPS && spotter.isReady(stream)) {
            spotter.decode(stream)
            val keyword = spotter.getResult(stream).keyword
            if (keyword.isNotBlank()) {
                // Reset native stream per upstream recommendation after a result.
                spotter.reset(stream)
                if (gate.evaluate(
                    elapsedRealtimeMs, keyword, nativeDetected = true,
                    modelVerified = true, sessionGranted = true
                ) == KwsDecision.CANDIDATE) candidate = true
            }
        }
        return candidate
    }

    override fun close() {
        if (closed) return
        closed = true
        gate.reset()
        try { stream.release() } finally { spotter.release() }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MAX_DECODE_LOOPS = 12
        private const val MODEL_DIR = "verified_sherpa_kws"

        /**
         * Must only be called on the existing audio worker AFTER explicit
         * microphone consent / FGS creation. Failure means fallback to Vosk.
         */
        fun tryOpen(context: Context, onStatus: (String) -> Unit): SherpaNativeWakeBridge? {
            val dir = File(context.noBackupFilesDir, MODEL_DIR)
            if (OfflineKwsModelGate().verify(dir) !=
                KwsReadiness.READY_FOR_NATIVE_ADAPTER) {
                onStatus("KWS_NATIVE_MODEL_UNAVAILABLE: Vosk sigue activo.")
                return null
            }
            val keywords = File(dir, "keywords.txt")
            // This optional file must be produced with upstream text2token for
            // the model BPE. Never accept an arbitrary external SD-card path.
            if (!keywords.isFile || Files.isSymbolicLink(keywords.toPath()) ||
                keywords.length() !in 1L..4096L) {
                onStatus("KWS_NATIVE_KEYWORDS_UNVERIFIED: Vosk sigue activo.")
                return null
            }
            var native: KeywordSpotter? = null
            try {
                fun model(name: String) = File(dir, name).absolutePath
                val config = KeywordSpotterConfig(
                    featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = model("encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"),
                            decoder = model("decoder-epoch-12-avg-2-chunk-16-left-64.onnx"),
                            joiner = model("joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx")
                        ),
                        tokens = model("tokens.txt"),
                        numThreads = 2,
                        modelType = "zipformer2"
                    ),
                    keywordsFile = keywords.absolutePath,
                    keywordsThreshold = 0.25f
                )
                native = KeywordSpotter(config = config)
                val stream = native.createStream()
                if (stream.ptr == 0L) {
                    stream.release()
                    native.release()
                    onStatus("KWS_NATIVE_STREAM_INVALID: Vosk sigue activo.")
                    return null
                }
                onStatus("KWS_NATIVE_READY: detector local de palabra activo.")
                return SherpaNativeWakeBridge(native, stream)
            } catch (_: UnsatisfiedLinkError) {
                onStatus("KWS_NATIVE_ABI_UNAVAILABLE: Vosk sigue activo.")
            } catch (_: Exception) {
                onStatus("KWS_NATIVE_INIT_FAILED: Vosk sigue activo.")
            }
            try { native?.release() } catch (_: Exception) {}
            return null
        }
    }
}
