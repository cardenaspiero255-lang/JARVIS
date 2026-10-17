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
import com.cardenaspiero255.jarvis.core.LocalQwenStatus
import com.cardenaspiero255.jarvis.core.LocalModelIntegrity
import com.cardenaspiero255.jarvis.core.VerifiedModelSnapshot
import com.cardenaspiero255.jarvis.core.OfficialQwenDownload
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
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
        modelPath.length() == LocalLlmPolicy.MODEL_BYTES

    /**
     * Asynchronous, read-only integrity and resource check. Never report Qwen
     * as ready merely because the basic deterministic chat answered a question.
     */
    fun checkLocalStatus(callback: (Boolean, String) -> Unit) {
        if (closed) return
        val epoch = session.get()
        worker.execute {
            val code = try {
                when {
                    !modelPath.isFile -> LocalQwenStatus.Code.ABSENT
                    modelPath.length() != LocalLlmPolicy.MODEL_BYTES ->
                        LocalQwenStatus.Code.INCOMPLETE
                    !LocalModelIntegrity.verify(modelPath, LocalLlmPolicy.SHA256) ->
                        LocalQwenStatus.Code.SHA_MISMATCH
                    else -> {
                        val thermalEmergency = Build.VERSION.SDK_INT >= 29 &&
                            thermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE
                        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val info = ActivityManager.MemoryInfo()
                        manager.getMemoryInfo(info)
                        val modelMiB = (LocalLlmPolicy.MODEL_BYTES + 1048575L) / 1048576L
                        when {
                            thermalEmergency -> LocalQwenStatus.Code.THERMAL
                            !LocalModelGate.canRun(
                                true, true, modelMiB, 1024L,
                                info.availMem / (1024L * 1024L), false
                            ) -> LocalQwenStatus.Code.LOW_MEMORY
                            else -> LocalQwenStatus.Code.READY
                        }
                    }
                }
            } catch (_: Exception) {
                LocalQwenStatus.Code.ERROR
            }
            deliver(epoch) {
                callback(code == LocalQwenStatus.Code.READY, LocalQwenStatus.describe(code))
            }
        }
    }

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

    /**
     * Downloads ONLY pinned official Qwen weights, on a user-confirmed action.
     * Conversation input and model replies never leave the device. Streamed
     * weights are authenticated and published atomically into private storage.
     * The worker and callbacks are lifecycle-aware; no background downloader.
     */
    fun installOfficial(
        onProgress: (Int, Long) -> Unit,
        callback: (Boolean, String) -> Unit
    ) {
        if (closed) return
        val epoch = session.get()
        worker.execute {
            var connection: HttpsURLConnection? = null
            var stage = "PREPARACIÓN"
            var downloadedBytes = 0L
            var httpStatus: Int? = null
            try {
                if (isInstalled() &&
                    LocalModelIntegrity.verify(modelPath, LocalLlmPolicy.SHA256)) {
                    deliver(epoch) { callback(true, "Qwen oficial ya está instalado y verificado.") }
                    return@execute
                }
                stage = "ALMACENAMIENTO"
                if (!modelDir.isDirectory && !modelDir.mkdirs()) {
                    throw IOException("Cannot create private model directory")
                }
                // Recover disk space after a previous process kill during import.
                modelDir.listFiles()?.filter {
                    it.name.startsWith("jarvis-official-qwen-") &&
                        it.name.endsWith(".part")
                }?.forEach { it.delete() }
                val reserve = 64L * 1024L * 1024L
                if (modelDir.usableSpace < LocalLlmPolicy.MODEL_BYTES + reserve) {
                    deliver(epoch) {
                        callback(false, "Espacio insuficiente: libera al menos 550 MB y vuelve a intentar.")
                    }
                    return@execute
                }
                stage = "CONEXIÓN"
                val endpoint = URL(LocalLlmPolicy.MODEL_DOWNLOAD_URL)
                if (endpoint.protocol != "https" || endpoint.host != "huggingface.co") {
                    throw IOException("Untrusted Qwen source")
                }
                connection = endpoint.openConnection() as HttpsURLConnection
                val https = connection!!
                https.connectTimeout = 20_000
                https.readTimeout = 60_000
                https.instanceFollowRedirects = true
                https.useCaches = false
                https.setRequestProperty("Accept", "application/octet-stream")
                httpStatus = https.responseCode
                if (httpStatus != 200 || https.url.protocol != "https") {
                    stage = "HTTP"
                    throw IOException("Qwen download unavailable over HTTPS")
                }
                if (https.contentLengthLong > 0 &&
                    https.contentLengthLong != LocalLlmPolicy.MODEL_BYTES) {
                    stage = "TAMAÑO"
                    throw IOException("Unexpected GGUF content size")
                }
                // Do not alter a mapped model while llama.cpp uses it.
                unload()
                stage = "DESCARGA"
                var lastReported = -1
                val ok = OfficialQwenDownload.install(
                    https.inputStream,
                    modelPath,
                    LocalLlmPolicy.MODEL_BYTES,
                    LocalLlmPolicy.SHA256,
                    isCancelled = { closed || epoch != session.get() },
                    onBytes = { count ->
                        downloadedBytes = count
                        val percentage = ((count * 100) / LocalLlmPolicy.MODEL_BYTES).toInt()
                        if (percentage >= lastReported + 1) {
                            lastReported = percentage
                            deliver(epoch) { onProgress(percentage, count) }
                        }
                    }
                )
                stage = "VERIFICACIÓN"
                if (!ok || !LocalModelIntegrity.verify(modelPath, LocalLlmPolicy.SHA256)) {
                    throw IOException("Incomplete or unverified GGUF")
                }
                deliver(epoch) {
                    callback(true, "Qwen oficial (491 MB) instalado y verificado. Ya puedes conversar sin internet.")
                }
            } catch (error: Exception) {
                val code = when {
                    stage == "HTTP" -> "QWEN_HTTP_${httpStatus ?: 0}"
                    stage == "TAMAÑO" -> "QWEN_SIZE"
                    error is java.net.UnknownHostException -> "QWEN_DNS"
                    error is java.net.SocketTimeoutException -> "QWEN_TIMEOUT"
                    error is javax.net.ssl.SSLException -> "QWEN_TLS"
                    stage == "ALMACENAMIENTO" -> "QWEN_STORAGE"
                    stage == "VERIFICACIÓN" -> "QWEN_SHA256"
                    else -> "QWEN_DOWNLOAD"
                }
                // Only error category and download count: never log prompts or URLs.
                Log.w("JARVIS_QWEN_INSTALL", "$code stage=$stage bytes=$downloadedBytes")
                val advice = when {
                    code.startsWith("QWEN_HTTP") ->
                        "El servidor rechazó la descarga (HTTP ${httpStatus ?: "desconocido"}). Reintenta con Wi-Fi o usa IMPORTAR MODELO."
                    code == "QWEN_SIZE" ->
                        "El servidor entregó un tamaño inesperado. No se instaló el modelo."
                    code == "QWEN_DNS" -> "Sin conexión al servidor de Qwen. Comprueba Wi-Fi y DNS."
                    code == "QWEN_TIMEOUT" -> "La conexión tardó demasiado. Comprueba Wi-Fi y reintenta."
                    code == "QWEN_TLS" -> "Falló la conexión HTTPS segura. Comprueba fecha/hora y conexión."
                    code == "QWEN_STORAGE" -> "No se pudo preparar el almacenamiento privado. Revisa espacio libre."
                    code == "QWEN_SHA256" -> "Archivo incompleto o SHA-256 no válido. No se instaló Qwen."
                    else -> "Falló la descarga tras ${downloadedBytes / 1_000_000L} MB. Reintenta con Wi-Fi."
                }
                deliver(epoch) {
                    callback(false, "$code: $advice")
                }
            } finally {
                connection?.disconnect()
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
