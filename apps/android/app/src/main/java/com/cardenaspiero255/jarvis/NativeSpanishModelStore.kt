package com.cardenaspiero255.jarvis

import android.content.Context
import com.cardenaspiero255.jarvis.core.NativeSpanishModelSafety
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Unpacks a hash-pinned offline Vosk Spanish model bundled INSIDE the APK.
 * No INTERNET permission, server, Qwen, recordings or data upload required.
 * Call on a background thread only; extraction can be slow on first launch.
 */
class NativeSpanishModelStore(private val context: Context) {
    private val policy = NativeSpanishModelSafety(NativeSpanishModelSafety.ROOT)

    fun ensureInstalled(
        onStage: (String) -> Unit = {},
        forceRefresh: Boolean = false,
        isActive: () -> Boolean = { true }
    ): File {
        // Cooperative cancellation during the slow first-run copy/unpack stages.
        // A native Vosk Model() constructor cannot safely be forcibly interrupted.
        fun requireActive() {
            if (!isActive() || Thread.currentThread().isInterrupted)
                throw InterruptedException("ASR_NATIVE_LOAD_CANCELLED")
        }
        requireActive()
        val output = File(context.filesDir, "vosk-es-0.42-private")
        val receipt = File(output, ".asset_sha256")
        val model = File(output, "model")
        if (!forceRefresh && receipt.isFile &&
            receipt.readText() == NativeSpanishModelSafety.SHA256 &&
            File(model, "am/final.mdl").isFile &&
            File(model, "conf/model.conf").isFile
        ) {
            onStage("ASR_NATIVE_MODEL_CACHED")
            return model
        }

        requireActive()
        onStage("ASR_NATIVE_ASSET_VERIFY")
        val scratch = File(context.cacheDir, "vosk-spanish-asset-verify.zip")
        val staging = File(context.filesDir, "vosk-es-0.42-staging")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(NativeSpanishModelSafety.ASSET_NAME).use { input ->
                FileOutputStream(scratch).use { outputFile ->
                    val buffer = ByteArray(32 * 1024)
                    var copied = 0L
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes < 0) break
                        requireActive()
                        copied += bytes
                        require(copied <= 55_000_000L) { "Spanish model exceeds archive limit" }
                        digest.update(buffer, 0, bytes)
                        outputFile.write(buffer, 0, bytes)
                    }
                }
            }
            requireActive()
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            require(sha == NativeSpanishModelSafety.SHA256) {
                "Invalid offline Spanish model fingerprint"
            }
            onStage("ASR_NATIVE_EXTRACTING")
            staging.deleteRecursively()
            require(staging.mkdirs()) { "Cannot create private model staging directory" }
            val stageModel = File(staging, "model")
            require(stageModel.mkdirs())
            var count = 0
            var expanded = 0L
            ZipFile(scratch).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    requireActive()
                    val entry = entries.nextElement()
                    count++
                    require(count <= NativeSpanishModelSafety.MAX_ENTRIES) {
                        "Too many model archive entries"
                    }
                    val relative = policy.relative(entry.name)
                        ?: throw IllegalArgumentException("Invalid model archive path")
                    if (relative.isEmpty()) continue
                    val destination = File(stageModel, relative)
                    val rootPath = stageModel.canonicalPath + File.separator
                    require(destination.canonicalPath.startsWith(rootPath)) {
                        "Model archive path escapes sandbox"
                    }
                    if (entry.isDirectory) {
                        require(destination.isDirectory || destination.mkdirs())
                        continue
                    }
                    require(policy.acceptEntrySize(entry.size, expanded)) {
                        "Speech archive exceeds extraction size limit"
                    }
                    require(destination.parentFile?.isDirectory == true ||
                        destination.parentFile?.mkdirs() == true)
                    archive.getInputStream(entry).use { input ->
                        FileOutputStream(destination).use { out ->
                            val buffer = ByteArray(32 * 1024)
                            var fileBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                requireActive()
                                fileBytes += read
                                expanded += read
                                require(policy.acceptEntrySize(fileBytes, 0L) &&
                                    expanded <= NativeSpanishModelSafety.MAX_EXPANDED_BYTES)
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
            require(File(stageModel, "am/final.mdl").isFile &&
                File(stageModel, "conf/model.conf").isFile) {
                "Offline Spanish model missing required recognizer files"
            }
            // Only mark the model ready after all files and hash checks pass.
            requireActive()
            File(staging, ".asset_sha256").writeText(NativeSpanishModelSafety.SHA256)
            output.deleteRecursively()
            require(staging.renameTo(output)) { "Cannot activate offline Spanish model" }
            onStage("ASR_NATIVE_MODEL_READY")
            return File(output, "model")
        } finally {
            scratch.delete()
            if (staging.isDirectory) staging.deleteRecursively()
        }
    }
}
