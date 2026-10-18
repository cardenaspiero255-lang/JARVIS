package com.cardenaspiero255.jarvis.core

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

/**
 * A supply-chain gate for the verified full INT8 GigaSpeech KWS model.
 *
 * This gate only verifies pinned file contents. The JNI bridge is optional;
 * the time-limited Vosk preview remains the fallback if initialization fails.
 *
 * Reference release: k2-fsa/sherpa-onnx, tag kws-models, English GigaSpeech
 * Zipformer 3.3M full archive. These bytes were verified from the official
 * release (SHA-256 f170013b4716e41b62b9bfd809687c207cef798ef9bc6534d524e17af9b6561a)
 * and the full INT8 encoder passed native inference when mobile crashed.
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
        // the upstream English full archive. No unpinned executable loaded.
        val requiredAssets: Map<String, String> = linkedMapOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" to
                "1e721676515bcd42a186979733981213c66c80db680e1cc582dfedf3be76e678",
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
