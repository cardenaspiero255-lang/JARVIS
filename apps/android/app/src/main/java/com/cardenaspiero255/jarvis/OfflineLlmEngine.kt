package com.cardenaspiero255.jarvis

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.cardenaspiero255.jarvis.core.LocalLlmPolicy
import com.cardenaspiero255.jarvis.core.LocalModelGate
import com.cardenaspiero255.jarvis.core.LocalModelIntegrity
import com.cardenaspiero255.jarvis.core.VerifiedModelSnapshot
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Explicit opt-in, offline llama.cpp GGUF inference. Operations are serialized
 * on one worker because the native model is not thread-safe. Never executes
 * model output as a device command. No network clients or cloud credentials.
 */
class OfflineLlmEngine(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jarvis-local-llm").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val session = AtomicInteger()
    @Volatile private var closed = false
    private var loaded: LlamaModel? = null
    private val modelDir get() = File(appContext.filesDir, "verified-models")
    private val modelPath get() = File(modelDir, LocalLlmPolicy.SHA256 + ".gguf")

    data class Benchmark(
        val tokens: Int,
        val tokensPerSecond: Float,
        val wallMs: Long,
        val promptEvalMs: Long,
        val generationMs: Long,
        val pssMiB: Int,
        val peakRssMiB: Long?,
        val thermalStatus: Int,
        val batteryPercent: Int
    ) {
        fun summary(): String =
            "LLM local: $tokens tokens, %.1f tokens/s, %d ms, PSS %d MiB, pico RSS %s MiB, térmico %d, batería %d%%".format(
                tokensPerSecond, wallMs, pssMiB, peakRssMiB?.toString() ?: "N/D",
                thermalStatus, batteryPercent
            )
    }

    fun isInstalled(): Boolean = modelPath.isFile &&
        LocalLlmPolicy.isEligibleLength(modelPath.length())

    fun invalidateSession() { session.incrementAndGet() }

    fun importFrom(uri: Uri, callback: (Boolean, String) -> Unit) {
        if (closed) return
        val epoch = session.get()
        worker.execute {
            var staging: File? = null
            try {
                if (!modelDir.isDirectory && !modelDir.mkdirs()) throw IOException("No private directory")
                val tmpFile = File.createTempFile("jarvis-import-", ".part", modelDir)
                staging = tmpFile
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open selected GGUF")
                var total = 0L
                input.buffered().use { stream ->
                    tmpFile.outputStream().buffered().use { output ->
                        val bytes = ByteArray(64 * 1024)
                        while (true) {
                            val count = stream.read(bytes)
                            if (count < 0) break
                            total += count
                            if (!LocalLlmPolicy.isEligibleLength(total)) {
                                throw IOException("Model exceeds size limit")
                            }
                            output.write(bytes, 0, count)
                        }
                    }
                }
                if (!LocalLlmPolicy.isEligibleLength(total)) throw IOException("Empty GGUF")
                // Never replace the target while llama.cpp is using a mapped model.
                unload()
                val verified = VerifiedModelSnapshot.create(
                    tmpFile, modelDir, LocalLlmPolicy.SHA256
                ) ?: throw IOException("SHA-256 mismatch")
                if (!verified.isFile) throw IOException("Model not installed")
                deliver(epoch) { callback(true, "Modelo Qwen verificado e instalado sin conexión.") }
            } catch (_: Exception) {
                deliver(epoch) {
                    callback(false, "No se pudo importar: archivo inválido, sin espacio o SHA-256 distinto. No se cargó el modelo.")
                }
            } finally {
                staging?.delete()
            }
        }
    }

    fun generate(prompt: String, callback: (String?, Benchmark?) -> Unit) {
        if (closed) return
        val epoch = session.get()
        if (!LocalLlmPolicy.isValidPrompt(prompt)) {
            deliver(epoch) { callback(null, null) }
            return
        }
        worker.execute {
            val response: String?
            val bench: Benchmark?
            try {
                val start = SystemClock.elapsedRealtime()
                val info = ActivityManager.MemoryInfo()
                val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                manager.getMemoryInfo(info)
                val availableMiB = info.availMem / (1024L * 1024L)
                val modelBytes = modelPath.length()
                val modelMiB = (modelBytes + 1048575L) / 1048576L
                val thermal = thermalStatus()
                if (!LocalLlmPolicy.isEligibleLength(modelBytes) ||
                    !LocalModelGate.canRun(
                        true,
                        LocalModelIntegrity.verify(modelPath, LocalLlmPolicy.SHA256),
                        modelMiB, 1024L, availableMiB,
                        Build.VERSION.SDK_INT >= 29 &&
                            thermal >= PowerManager.THERMAL_STATUS_SEVERE
                    )
                ) {
                    deliver(epoch) { callback(null, null) }
                    return@execute
                }
                if (loaded == null) {
                    loaded = runBlocking {
                        Llama.loadModel(
                            modelPath.absolutePath,
                            LlamaConfig(contextSize = 1024, threads = 4, gpuLayers = 0)
                        )
                    }
                }
                val result = runBlocking {
                    Llama.complete(
                        loaded!!, prompt,
                        systemPrompt = "Eres JARVIS, un asistente local en español de Chile. Responde de forma breve y precisa. " +
                            "No inventes datos actuales. No afirmes tener internet ni permisos para ejecutar acciones.",
                        maxTokens = 128
                    )
                }
                response = LocalLlmPolicy.cleanAnswer(result.text)
                val memory = Debug.MemoryInfo()
                Debug.getMemoryInfo(memory)
                val battery = (appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                bench = Benchmark(
                    result.tokensGenerated, result.tokensPerSecond,
                    SystemClock.elapsedRealtime() - start,
                    result.promptEvalTimeMs, result.generateTimeMs,
                    memory.totalPss / 1024, peakRssMiB(), thermalStatus(), battery
                )
            } catch (_: Exception) {
                unload()
                deliver(epoch) { callback(null, null) }
                return@execute
            } catch (_: LinkageError) {
                unload()
                deliver(epoch) { callback(null, null) }
                return@execute
            }
            // Metrics only: never log user prompts or model replies.
            Log.i("JARVIS_LLM_BENCH", bench.summary())
            deliver(epoch) { callback(response, bench) }
        }
    }

    private fun thermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= 29)
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        else -1

    /** Linux kernel VmHWM: peak resident set, not Java heap or PSS. */
    private fun peakRssMiB(): Long? = try {
        File("/proc/self/status").useLines { lines ->
            val row = lines.firstOrNull { it.startsWith("VmHWM:") }
            val kib = row?.substringAfter(":")?.trim()?.substringBefore(" ")?.toLongOrNull()
            kib?.div(1024)
        }
    } catch (_: Exception) { null }

    private fun deliver(epoch: Int, action: () -> Unit) {
        main.post { if (!closed && epoch == session.get()) action() }
    }

    /** Keep any running native call alive until it finishes, then release. */
    fun releaseForBackground() {
        invalidateSession()
        if (!closed) worker.execute { unload() }
    }

    private fun unload() {
        loaded?.let { Llama.releaseModel(it) }
        loaded = null
    }

    fun close() {
        if (closed) return
        closed = true
        session.incrementAndGet()
        worker.execute { unload() }
        worker.shutdown()
    }
}
