package com.cardenaspiero255.jarvis.core

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/**
 * Narrow on-device Spanish dialogue. It never interprets text as a tool
 * instruction and never claims network research or general LLM reasoning.
 */
class LocalDialogue {
    private val turns = mutableListOf<String>()
    private var lastAnswer: String? = null
    private var lastNumericResult: BigDecimal? = null

    fun reply(input: String): String {
        // Reject rather than silently truncate an expression or mixed instruction.
        // Rejected inputs are not persisted or eligible for voice readout.
        if (input.length > MAX_INPUT_LENGTH) {
            return "Mensaje demasiado largo: escribe hasta 280 caracteres para JARVIS."
        }
        val sanitized = input.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val normalized = Normalizer.normalize(sanitized.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val math = if (normalized == "repitelo" || normalized == "repite") null
            else OfflineArithmetic.evaluate(sanitized, lastNumericResult)
        val offlineHelp = VoiceFaqRouter.answer(sanitized)
        val answer = when {
            sanitized.isBlank() -> "Escribe una pregunta para conversar."
            normalized == "repitelo" || normalized == "repite" ->
                lastAnswer ?: "No hay una respuesta anterior todavía."
            math != null -> {
                if (math.value != null) lastNumericResult = math.value
                math.answer
            }
            offlineHelp != null -> offlineHelp
            normalized == "buenas" ->
                "Hola. Soy JARVIS en modo observador. Puedo explicar mis funciones locales."
            normalized in setOf("estado", "jarvis estado", "estado jarvis") ->
                "JARVIS funciona en modo observador. Las acciones sensibles necesitan permiso visible."
            else ->
                "No tengo una conexión a un modelo conversacional general en esta versión. Puedo ayudarte con estado, privacidad y funciones de JARVIS."
        }.take(300)
        turns.add("Tú: $sanitized".take(400))
        turns.add("JARVIS: $answer".take(400))
        while (turns.size > 16) turns.removeAt(0)
        lastAnswer = answer
        return answer
    }

    fun history(): List<String> = turns.toList()

    /**
     * Restore only local dialogue rows from authenticated encrypted storage.
     * Stored text is data and never dispatched as an Android instruction.
     */
    fun restore(plainHistory: String) {
        clear()
        for (line in plainHistory.lineSequence().toList().takeLast(48)) {
            val bounded = line.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").take(400)
            if (bounded.startsWith("Tú: ") || bounded.startsWith("JARVIS: ")) {
                turns.add(bounded)
            }
        }
        while (turns.size > 24) turns.removeAt(0)
        lastAnswer = turns.lastOrNull { it.startsWith("JARVIS: ") }
            ?.removePrefix("JARVIS: ")
    }

    fun clear() {
        turns.clear()
        lastAnswer = null
        lastNumericResult = null
    }
    private companion object {
        const val MAX_INPUT_LENGTH = 280
    }

}
