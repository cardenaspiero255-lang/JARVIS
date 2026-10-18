package com.cardenaspiero255.jarvis.core

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

/**
 * A supply-chain gate for an OPTIONAL sherpa-onnx English KWS mobile model.
 *
 * This never installs, downloads or runs a model. The KWS JNI adapter remains
 * unimplemented; current Vosk preview remains the working fallback.
 *
 * Reference release: k2-fsa/sherpa-onnx, tag kws-models, English GigaSpeech
 * Zipformer 3.3M mobile. SHA-256 values from an externally published
 * byte-identical mirror must be audited against upstream before enabling.
 */
enum class KwsReadiness {
    NO_VERIFIED_MODEL,
    INVALID_FINGERPRINT,
    READY_FOR_NATIVE_ADAPTER
}

class OfflineKwsModelGate {
    fun verify(directory: File): KwsReadiness {
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) {
            return KwsReadiness.NO_VERIFIED_MODEL
        }
        val assets = requiredAssets
        for ((filename, expected) in assets) {
            val file = File(directory, filename)
            if (!file.isFile || Files.isSymbolicLink(file.toPath())) {
                return KwsReadiness.NO_VERIFIED_MODEL
            }
            if (file.length() == 0L || file.length() > MAX_FILE_BYTES) {
                return KwsReadiness.INVALID_FINGERPRINT
            }
            val fingerprint = try {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                return KwsReadiness.INVALID_FINGERPRINT
            }
            if (!fingerprint.equals(expected, ignoreCase = true)) {
                return KwsReadiness.INVALID_FINGERPRINT
            }
        }
        return KwsReadiness.READY_FOR_NATIVE_ADAPTER
    }

    companion object {
        private const val MAX_FILE_BYTES = 18_000_000L

        // Hashes correspond to the five individual ONNX/BPE/token files of
        // the upstream English mobile archive. No unpinned executable loaded.
        val requiredAssets: Map<String, String> = linkedMapOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" to
                "7d5a25fbc14b423074fe0c58e1be388ccf3f1a815c649980232a0c47f81875e4",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx" to
                "f61ebd3eed3773a44d088d53dfae92dbb6aec4839f4dcaee2d402414741663a3",
            "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx" to
                "eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c",
            "tokens.txt" to
                "fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53",
            "bpe.model" to
                "c8a2a0129c4ab8e463164c142f82d25649661b122c8cd0b7aab5c9e80b90ad24"
        )
    }
}

/**
 * A native keyword result is only a wake CANDIDATE, never voice identity.
 * Caller must pass evidence from an initialized, validated native engine and
 * an authorized foreground mic session. This deliberately does not start audio.
 */
enum class KwsDecision { IGNORE, CANDIDATE }

class KwsCandidateGate {
    private var lastWake: Long? = null

    fun evaluate(
        elapsedRealtimeMs: Long,
        keyword: String,
        nativeDetected: Boolean,
        modelVerified: Boolean,
        sessionGranted: Boolean
    ): KwsDecision {
        if (elapsedRealtimeMs < 0L || !nativeDetected || !modelVerified || !sessionGranted ||
            keyword.lowercase(Locale.ROOT) != "jarvis"
        ) return KwsDecision.IGNORE
        val previous = lastWake
        if (previous != null && elapsedRealtimeMs >= previous &&
            elapsedRealtimeMs - previous < 1200L
        ) return KwsDecision.IGNORE
        if (previous != null && elapsedRealtimeMs < previous) return KwsDecision.IGNORE
        lastWake = elapsedRealtimeMs
        return KwsDecision.CANDIDATE
    }

    fun reset() { lastWake = null }
}
