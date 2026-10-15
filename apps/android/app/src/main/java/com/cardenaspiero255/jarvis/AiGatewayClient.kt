package com.cardenaspiero255.jarvis

import android.os.Handler
import android.os.Looper
import com.cardenaspiero255.jarvis.core.AiGatewayPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection

/**
 * One foreground, user-initiated request at a time. Local modules never call
 * this client; text returned by the model is NEVER treated as an Android action.
 * Conversation history stays in RAM only, and disappears on stop/privacy lock.
 */
class AiGatewayClient(private val settings: AiGatewaySettings) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    private val history = ArrayDeque<Pair<String, String>>()
    @Volatile private var pending: HttpsURLConnection? = null
    @Volatile private var closed = false
    /** Safe, user-facing failure only; never includes URL, token or raw server body. */
    private class GatewayFailure(val safeMessage: String) : Exception()


    fun isConfigured(): Boolean = settings.get() != null
    fun voiceEnabled(): Boolean = settings.get()?.allowVoice == true

    fun request(message: String, fromVoice: Boolean,
                callback: (String?, String?) -> Unit): Boolean {
        val pairing = settings.get()
        if (closed || !AiGatewayPolicy.allowCloudRequest(
                pairing != null, pairing?.allowVoice == true, fromVoice) ||
            pairing == null || message.isBlank() || message.length > 1200) return false
        // Reading/replaying this conversation requires an explicit request.
        val prior = synchronized(history) { history.toList().takeLast(4) }
        val ticket = generation.incrementAndGet()
        pending?.disconnect()
        worker.execute {
            var conn: HttpsURLConnection? = null
            var answer: String? = null
            var error: String? = null
            try {
                if (closed || generation.get() != ticket) return@execute
                // HTTPS + platform certificate validation; no redirect downgrade.
                if (!AiGatewayPolicy.endpointAllowed(pairing.endpoint)) return@execute
                conn = URL(pairing.endpoint).openConnection() as HttpsURLConnection
                pending = conn
                conn.instanceFollowRedirects = false
                conn.requestMethod = "POST"
                conn.connectTimeout = 8_000
                conn.readTimeout = 28_000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer " + pairing.token)
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                val input = JSONArray()
                for ((role,text) in prior) {
                    input.put(JSONObject().put("role",role).put("content",text))
                }
                val body = JSONObject().put("message",message).put("history",input)
                    .toString().toByteArray(StandardCharsets.UTF_8)
                if (body.size > 12_000) throw IllegalStateException("request too long")
                conn.outputStream.use { it.write(body) }
                val responseCode = conn.responseCode
                if (responseCode == 401 || responseCode == 403)
                    throw GatewayFailure("El servidor rechazó el token de JARVIS. " +
                        "Escribe conectar ia para emparejarlo de nuevo.")
                if (responseCode == 429)
                    throw GatewayFailure("El servidor limitó las solicitudes. " +
                        "Espera un momento antes de volver a intentarlo.")
                if (responseCode == 502)
                    throw GatewayFailure("El servidor recibió un error del modelo de IA. " +
                        "Revisa el acceso, saldo y disponibilidad de la API de OpenAI.")
                if (responseCode == 503)
                    throw GatewayFailure("El servidor de IA no tiene sus secretos configurados. " +
                        "Revisa las variables de entorno en Vercel.")
                if (responseCode != 200)
                    throw GatewayFailure("La API de JARVIS devolvió un error de conexión. " +
                        "Comprueba el servidor en Vercel.")
                val bytes = ByteArrayOutputStream()
                conn.inputStream.use { stream ->
                    val buffer = ByteArray(2048)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (bytes.size() + n > 9_000) throw IllegalStateException("response too long")
                        bytes.write(buffer, 0, n)
                    }
                }
                val response=JSONObject(bytes.toString(StandardCharsets.UTF_8.name()))
                val raw = response.optString("text")
                if (response.optString("model") != "gpt-6-astra" ||
                    raw.isBlank() || raw.length > 2400)
                    throw IllegalStateException("invalid model response")
                answer = raw
            } catch (safe: GatewayFailure) {
                error = safe.safeMessage
            } catch (_: Exception) {
                // Never show user input, the gateway token or upstream diagnostic.
                error = "No se pudo conectar con la IA. Comprueba el servidor y vuelve a intentarlo."
            } finally {
                conn?.disconnect()
                if (pending === conn) pending = null
            }
            val safeAnswer = answer
            val safeError = error
            main.post {
                if (!closed && generation.get() == ticket) {
                    if (safeAnswer != null) synchronized(history) {
                        history.addLast("user" to message)
                        history.addLast("assistant" to safeAnswer.take(1200))
                        while (history.size > 8) history.removeFirst()
                    }
                    callback(safeAnswer,safeError)
                }
            }
        }
        return true
    }

    fun cancelAndClear() {
        generation.incrementAndGet()
        pending?.disconnect()
        pending=null
        synchronized(history) { history.clear() }
    }

    fun close() {
        closed=true
        cancelAndClear()
        worker.shutdownNow()
    }
}
