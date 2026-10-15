package com.cardenaspiero255.jarvis.core

import java.io.File
import java.io.IOException

/**
 * Creates a private verified snapshot, independent of subsequent changes to
 * the source. The runtime must open this returned path, never the source.
 * The caller must serialize snapshot creation and inference on the same path.
 */
object VerifiedModelSnapshot {
    fun create(source: File, privateDirectory: File, expectedSha256: String): File? {
        if (!source.isFile || !source.canRead() ||
            !Regex("[0-9a-fA-F]{64}").matches(expectedSha256)) return null
        var temp: File? = null
        return try {
            if (!privateDirectory.isDirectory && !privateDirectory.mkdirs()) return null
            temp = File.createTempFile("jarvis-verified-", ".tmp", privateDirectory)
            source.inputStream().buffered().use { input ->
                temp!!.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (!LocalModelIntegrity.verify(temp!!, expectedSha256)) {
                temp!!.delete()
                null
            } else {
                val target = File(privateDirectory, expectedSha256.lowercase() + ".gguf")
                if (target.exists() && !target.delete()) throw IOException("Cannot replace snapshot")
                if (!temp!!.renameTo(target)) throw IOException("Cannot publish snapshot")
                target.setReadable(false, false)
                target.setReadable(true, true)
                target.setWritable(false, false)
                if (!LocalModelIntegrity.verify(target, expectedSha256)) {
                    target.delete()
                    null
                } else target
            }
        } catch (_: Exception) {
            temp?.delete()
            null
        }
    }
}
