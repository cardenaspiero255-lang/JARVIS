package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Local, deterministic understanding of selected Spanish morning routines.
 *
 * This parser produces intentions, NEVER device actions or network requests.
 * Only exact phrases are accepted: hypothetical, negated or embellished words
 * must not be interpreted as user authorization.
 */
enum class MorningVoiceIntent {
    TIME_ONLY,
    TIME_AND_GAMEHUB_REQUEST,
    GAMEHUB_REQUEST
}

class MorningVoiceRouter {
    fun parse(transcript: String): MorningVoiceIntent? {
        if (transcript.isBlank() || transcript.length > 220) return null
        val normalized = Normalizer.normalize(
            transcript.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val phrase = normalized.removePrefix("jarvis ").trim()
        return when (phrase) {
            "dime la hora", "que hora es" -> MorningVoiceIntent.TIME_ONLY
            "dime la hora y dile a chatgpt que siga trabajando en gamehub ultra",
            "dime la hora y dile a chat gpt que siga trabajando en gamehub ultra" ->
                MorningVoiceIntent.TIME_AND_GAMEHUB_REQUEST
            "dile a chatgpt que siga trabajando en gamehub ultra",
            "dile a chat gpt que siga trabajando en gamehub ultra" ->
                MorningVoiceIntent.GAMEHUB_REQUEST
            else -> null
        }
    }
}

/** Uses device timezone, never an internet clock or a hard-coded location. */
object LocalDeviceClock {
    private val format = DateTimeFormatter.ofPattern("H:mm", Locale("es", "CL"))

    fun speak(clock: Clock = Clock.systemDefaultZone()): String =
        "Son las ${LocalTime.now(clock).format(format)}."
}

/** A request is not a dispatch. Never claim work reached ChatGPT without a receipt. */
object MorningVoiceResponse {
    fun forIntent(intent: MorningVoiceIntent, timeText: String): String {
        val handoff =
            "Recibí la solicitud de continuar GameHub Ultra en ChatGPT, " +
                "pero no la envié: aún falta configurar una conexión autorizada."
        return when (intent) {
            MorningVoiceIntent.TIME_ONLY -> timeText
            MorningVoiceIntent.TIME_AND_GAMEHUB_REQUEST -> "$timeText $handoff"
            MorningVoiceIntent.GAMEHUB_REQUEST -> handoff
        }
    }
}
