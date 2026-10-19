package com.cardenaspiero255.jarvis

import android.content.Context
import android.net.Uri
import com.cardenaspiero255.jarvis.core.LocalGgufPolicy
import java.io.File
import java.security.MessageDigest

/**
 * Imports a user-selected GGUF into app-private storage. No Internet/network
 * permission, automatic download, unknown URI persistence or model execution.
 * SHA-256 is persisted and revalidated before use (on a worker).
 */
class LocalGgufModelStore(private val context: Context) {
    private companion object {
        // Activity recreation creates NEW store objects. Synchronizing only
        // 'this' would let another Activity delete a model during native use.
        // One process-wide lock covers verification, inference lease and file
        // mutation, without ever blocking the UI-only hasModel hint.
        val processModelLock = Any()
    }
    private val prefs get() = context.getSharedPreferences("jarvis_gguf_v1", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "private_gguf")
    private val file get() = File(directory, "selected.gguf")

    // UI-only inexpensive availability hint: do NOT block the main thread
    // waiting for withVerifiedModel's potentially minute-long native lease.
    // Actual use is always independently SHA-verified under the store monitor.
    fun hasModel(): Boolean = file.isFile && prefs.contains("sha256")

    /** Caller must run on a worker, never the UI thread. Returns a private SHA. */
    @Synchronized fun import(uri: Uri): String = synchronized(processModelLock) {
        require(uri.scheme == "content") { "Selecciona un archivo mediante Android." }
        check(directory.isDirectory || directory.mkdirs()) { "Sin espacio privado." }
        val tmp = File(directory, "selected.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val header = ByteArray(8)
            var total = 0L
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("No se pudo abrir el modelo.")
            stream.use { input ->
                tmp.outputStream().buffered().use { output ->
                    while (true) {
                        val buffer = ByteArray(64 * 1024)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        check(total + count <= LocalGgufPolicy.MAX_BYTES) {
                            "Modelo demasiado grande (límite 1,6 GB)."
                        }
                        val captured = (8 - total).toInt().coerceAtLeast(0).coerceAtMost(count)
                        if (captured > 0) System.arraycopy(buffer, 0, header, total.toInt(), captured)
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        total += count
                    }
                    output.flush()
                }
            }
            require(LocalGgufPolicy.validLength(total) &&
                LocalGgufPolicy.validHeader(header)) { "No es un GGUF compatible." }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            check(tmp.renameTo(file)) { "No se pudo reemplazar el modelo." }
            check(prefs.edit().putString("sha256", sha).putLong("bytes", total).commit()) {
                deleteModel()
                "No se pudo registrar la integridad del modelo."
            }
            sha
        } finally {
            tmp.delete()
        }
    }

    /**
     * Lease a model from verification through the complete synchronous native
     * inference. A competing import/delete waits on the SAME monitor, preventing
     * replacing the filename while llama.cpp is mapping/decoding its bytes.
     * Call only from a worker; the block must return after native model_free.
     */
    @Synchronized fun <T> withVerifiedModel(block: (File?) -> T): T =
        synchronized(processModelLock) { block(verifiedFile()) }

    /**
     * A single immutably verified identity for native warm-model reuse.
     * The SHA is read while holding the same monitor as import/delete; native
     * inference must finish *before* the callback returns.
     * A missing/tampered file yields null for both values.
     */
    @Synchronized fun <T> withVerifiedModelRevision(
        block: (File?, String?) -> T
    ): T = synchronized(processModelLock) {
        val verified = verifiedFile()
        val revision = if (verified != null) prefs.getString("sha256", null) else null
        block(verified, revision)
    }

    /** Check hash on every inference. Never trust name/extension alone. */
    @Synchronized fun verifiedFile(): File? = synchronized(processModelLock) {
        val expected = prefs.getString("sha256", null) ?: return@synchronized null
        if (!file.isFile || !LocalGgufPolicy.validLength(file.length()))
            return@synchronized null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val data = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(data)
                if (count < 0) break
                digest.update(data, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (expected == actual) file else null
    }

    @Synchronized fun deleteModel(): Boolean = synchronized(processModelLock) {
        val removed = !file.exists() || file.delete()
        prefs.edit().clear().commit()
        removed
    }
}
