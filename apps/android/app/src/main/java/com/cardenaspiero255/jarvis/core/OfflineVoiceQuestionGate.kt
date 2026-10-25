package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

enum class OfflineVoiceDecision { IGNORED, WAITING, QUESTION, STOP }

data class OfflineVoiceObservation(
    val decision: OfflineVoiceDecision,
    val question: String? = null
)

/**
 * Untrusted Vosk text -> bounded local TEXT question, never an Android action.
 *
 * Requires the separately tapped foreground microphone session. 'Jarvis'
 * is a wake cue, NOT voice-owner authentication or a capability grant.
 * No transcripts, audio, models, or consents persist here.
 */
class OfflineVoiceQuestionGate(private val followupMillis: Long = 8_000L) {
    init { require(followupMillis in 1L..30_000L) }

    private var wakeAt: Long? = null

    fun observe(transcript: String, nowMillis: Long): OfflineVoiceObservation {
        fun ignored() = OfflineVoiceObservation(OfflineVoiceDecision.IGNORED)
        if (nowMillis < 0L || transcript.isBlank() || transcript.length > 280 ||
            transcript.any { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }
        ) {
            wakeAt = null
            return ignored()
        }
        val raw = transcript.trim()
        val normalized = normalized(raw)
        val before = wakeAt
        val pending = before != null && nowMillis >= before &&
            nowMillis - before < followupMillis
        wakeAt = null

        if (normalized == "jarvis para" || normalized == "jarvis detente" ||
            (pending && (normalized == "para" || normalized == "detente"))) {
            return OfflineVoiceObservation(OfflineVoiceDecision.STOP)
        }
        if (normalized == "jarvis") {
            wakeAt = nowMillis
            return OfflineVoiceObservation(OfflineVoiceDecision.WAITING)
        }

        // Only one prompt per exact wake. A stray word outside the eight
        // second window never becomes an LLM query.
        val wakePrefix = Regex("^jarvis[\\s,:;.!?\\-]+", RegexOption.IGNORE_CASE)
        val question = when {
            wakePrefix.containsMatchIn(raw) -> wakePrefix.replaceFirst(raw, "").trim()
            pending -> raw
            else -> return ignored()
        }
        val folded = normalized(question)
        if (folded.isBlank() || question.length > 240 ||
            folded.startsWith("no ") || folded.startsWith("nunca ") ||
            !LocalGgufPolicy.validPrompt(question)) return ignored()

        // Extra defense: requests to change the device are NOT even presented
        // to a generative model as if they were answerable device operations.
        val forbidden = Regex(
            "^(abre|abrir|activa|activar|desactiva|desactivar|instala|instalar|" +
            "ejecuta|ejecutar|borra|borrar|elimina|eliminar|envia|enviar|" +
            "autoriza|autorizar|enciende|encender|apaga|apagar|" +
            "fusiona|fusionar|conecta|conectar|desbloquea|desbloquear)\\b"
        )
        val chain = Regex(
            "\\b(y|para|luego)\\s+(abre|activa|desactiva|instala|ejecuta|" +
            "borra|elimina|envia|autoriza|enciende|apaga|fusiona|conecta)\\b"
        )
        if (forbidden.containsMatchIn(folded) || chain.containsMatchIn(folded)) {
            return ignored()
        }
        return OfflineVoiceObservation(OfflineVoiceDecision.QUESTION, question)
    }

    fun expiredAt(nowMillis: Long): Boolean {
        val started = wakeAt ?: return false
        if (nowMillis < started || nowMillis - started >= followupMillis) {
            wakeAt = null
            return true
        }
        return false
    }

    fun reset() { wakeAt = null }

    private fun normalized(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}

/** Plain text only; in particular NOT a tool output or an executable plan. */
object OfflineVoiceAnswerPolicy {
    const val mayExecuteDeviceActions = false

    // JNI currently returns errors as plain strings, just like generated text.
    // Keep every native/adapter sentinel out of TTS and generation receipts.
    // The source contract checks coverage when those producers change. This
    // conservative bridge is not a typed JNI result or semantic validation.
    private val failurePrefixes = listOf(
        "Modelo GGUF no instalado",
        "Motor nativo de inferencia no disponible",
        "La inferencia local falló",
        "El motor nativo no pudo",
        "No se generó una respuesta local",
        "Solicitud local inválida",
        "No se pudo iniciar la cancelación",
        "Cancelación no disponible",
        "No se pudo cargar el modelo GGUF",
        "Vocabulario GGUF no disponible",
        "La pregunta supera el contexto local máximo",
        "Error de tokenización del modelo",
        "Memoria insuficiente para el contexto GGUF",
        "No se pudo iniciar el generador local",
        "Inferencia local cancelada o fallida",
        "Fallo al decodificar una palabra",
        "La generación local se interrumpió",
        "Parámetros faltantes",
        "No se pudo preparar el mensaje"
    )

    fun forTts(response: String?): String? {
        val original = response?.trim() ?: return null
        if (original.isBlank() ||
            original.any { Character.isISOControl(it) && it != '\n' && it != '\r' && it != '\t' } ||
            original.any { Character.getType(it) == Character.FORMAT.toInt() }
        ) return null

        val clean = original.replace(Regex("\\s+"), " ").trim()
        if (failurePrefixes.any { clean.startsWith(it, ignoreCase = true) }) return null
        if (clean.isBlank()) return null
        return if (clean.length <= 420) clean else clean.take(419).trimEnd() + "…"
    }
}
