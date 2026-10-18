package com.cardenaspiero255.jarvis

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

/** Loads only the pinned model bundled inside the signed JARVIS APK. */
class VerifiedSpeakerModelStore(private val context: Context) {
    companion object {
        const val MODEL_SHA256 =
            "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"
        private const val MAX_BYTES = 43_000_000L
        private const val MIN_BYTES = 39_000_000L
    }

    fun ensureInstalled(): File? {
        val model = File(context.noBackupFilesDir, "verified_sherpa_speaker.onnx")
        if (valid(model)) return model
        val stage = File(context.noBackupFilesDir, "verified_sherpa_speaker.stage")
        stage.delete()
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            context.assets.open("verified_sherpa_speaker/model.onnx").use { input ->
                FileOutputStream(stage).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted)
                            throw InterruptedException("Cancelled speaker model copy")
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= MAX_BYTES)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(size >= MIN_BYTES && digest.digest().joinToString("") {
                "%02x".format(it)
            } == MODEL_SHA256) { "Untrusted speaker model" }
            require(!Files.isSymbolicLink(stage.toPath()))
            require(!model.exists() || model.delete())
            require(stage.renameTo(model)) { "Cannot install speaker model" }
            model
        } catch (_: Exception) {
            null
        } finally {
            stage.delete()
        }
    }

    private fun valid(file: File): Boolean {
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) ||
            file.length() !in MIN_BYTES..MAX_BYTES) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256
        } catch (_: Exception) { false }
    }
}
