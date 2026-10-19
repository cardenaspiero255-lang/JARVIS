package com.cardenaspiero255.jarvis

import android.content.Context
import com.cardenaspiero255.jarvis.core.KwsReadiness
import com.cardenaspiero255.jarvis.core.OfflineKwsModelGate
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Copy verified KWS assets from the signed APK to app-private noBackup storage
 * (the native model loader requires real file paths). Never accepts external
 * paths, downloaded audio, or mutable SD-card models.
 */
class VerifiedKwsModelStore(private val context: Context) {
    private val root get() = File(context.noBackupFilesDir, "verified_sherpa_kws")
    private val gate = OfflineKwsModelGate()

    fun ensureInstalled(): File? {
        if (gate.verify(root) == KwsReadiness.READY_FOR_NATIVE_ADAPTER &&
            validKeywords(root)) return root

        val staging = File(context.noBackupFilesDir, "verified_sherpa_kws_stage")
        staging.deleteRecursively()
        if (!staging.mkdirs()) return null
        return try {
            val expected = OfflineKwsModelGate.requiredAssets
            for ((name, digest) in expected) {
                val target = File(staging, name)
                val hash = MessageDigest.getInstance("SHA-256")
                context.assets.open("verified_sherpa_kws/$name").use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var size = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted)
                                throw InterruptedException("KWS model install cancelled")
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            require(size in 1L..18_000_000L) { "KWS asset too large" }
                            hash.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val computed = hash.digest().joinToString("") { "%02x".format(it) }
                require(computed == digest) { "Bundled KWS SHA mismatch: $name" }
            }
            context.assets.open("verified_sherpa_kws/keywords.txt").use { input ->
                val buffer = ByteArray(4097)
                val output = java.io.ByteArrayOutputStream()
                while (output.size() <= 4096) {
                    val n = input.read(buffer, 0, minOf(buffer.size,
                        4097 - output.size()))
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                val bytes = output.toByteArray()
                require(bytes.size in 1..4096) { "Oversized JARVIS keyword list" }
                File(staging, "keywords.txt").writeBytes(bytes)
            }
            require(validKeywords(staging)) { "Invalid generated Jarvis tokens" }
            require(gate.verify(staging) == KwsReadiness.READY_FOR_NATIVE_ADAPTER)
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException("KWS activation cancelled")
            root.deleteRecursively()
            require(staging.renameTo(root)) { "Cannot activate verified KWS model" }
            root
        } catch (_: Exception) {
            null // Optional KWS fails closed; existing Vosk ASR stays available.
        } finally {
            if (staging.isDirectory) staging.deleteRecursively()
        }
    }

    private fun validKeywords(dir: File): Boolean {
        val file = File(dir, "keywords.txt")
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) ||
            file.length() !in 1L..4096L) return false
        val text = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { return false }
        if (!text.endsWith(" @Jarvis\n") || text.count { it == '\n' } != 1 ||
            text.length > 4096) return false
        val fragments = text.removeSuffix(" @Jarvis\n").split(' ')
        if (fragments.isEmpty() || fragments.any { it.isBlank() || it == "<unk>" })
            return false
        val tokens = try {
            File(dir, "tokens.txt").useLines { lines ->
                lines.mapNotNull { line -> line.split(' ').firstOrNull() }.toSet()
            }
        } catch (_: Exception) { return false }
        return fragments.all { it in tokens }
    }
}
