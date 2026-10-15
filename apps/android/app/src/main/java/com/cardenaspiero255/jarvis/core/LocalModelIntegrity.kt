package com.cardenaspiero255.jarvis.core

import java.io.File
import java.security.MessageDigest

/** Checks a user-provided GGUF against an independently trusted SHA-256 digest. */
object LocalModelIntegrity {
    private val SHA256_HEX = Regex("[0-9a-fA-F]{64}")

    fun verify(model: File, expectedSha256: String): Boolean {
        if (!SHA256_HEX.matches(expectedSha256) || !model.isFile || !model.canRead()) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            val expected = ByteArray(32) { i ->
                expectedSha256.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            MessageDigest.isEqual(digest.digest(), expected)
        } catch (_: Exception) {
            false
        }
    }
}
