package com.cardenaspiero255.jarvis.core

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Verifies bytes while streaming into a private temporary file and atomically
 * publishes them only after exact size + pinned SHA-256 match. No second 491MB
 * copy is required. An existing verified model is never overwritten on failure.
 */
object OfficialQwenDownload {
    private val HEX_256 = Regex("[0-9a-fA-F]{64}")

    fun install(
        source: InputStream,
        destination: File,
        expectedBytes: Long,
        expectedSha256: String,
        isCancelled: () -> Boolean,
        onBytes: (Long) -> Unit
    ): Boolean {
        var pending: File? = null
        return try {
            source.use { input ->
                val dir = destination.parentFile ?: return false
                if (isCancelled() || expectedBytes <= 0L ||
                    expectedBytes > LocalLlmPolicy.MAX_BYTES ||
                    !HEX_256.matches(expectedSha256)
                ) return false
                if (!dir.isDirectory && !dir.mkdirs()) return false
                val temp = File.createTempFile("jarvis-official-qwen-", ".part", dir)
                pending = temp
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                FileOutputStream(temp).buffered().use { output ->
                    val block = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) return false
                        val count = input.read(block)
                        if (count < 0) break
                        if (count == 0) continue
                        if (total > expectedBytes - count) return false
                        total += count
                        output.write(block, 0, count)
                        digest.update(block, 0, count)
                        onBytes(total)
                    }
                    output.flush()
                }
                if (isCancelled() || total != expectedBytes) return false
                val expectedHash = ByteArray(32) { i ->
                    expectedSha256.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                if (!MessageDigest.isEqual(digest.digest(), expectedHash)) return false
                if (isCancelled()) return false
                // Both files reside in the same app-private directory / filesystem.
                Files.move(temp.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                pending = null
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            pending?.delete()
        }
    }
}
