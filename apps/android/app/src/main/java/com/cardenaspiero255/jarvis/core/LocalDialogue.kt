package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Narrow on-device Spanish dialogue. It never interprets text as a tool
 * instruction and never claims network research or general LLM reasoning.
 */
class LocalDialogue {
    private val turns = mutableListOf<String>()
    private var lastAnswer: String? = null

    fun reply(input: String): String {
        val sanitized = input.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ").trim().take(280)
        val normalized = Normalizer.normalize(sanitized.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val answer = when {
            sanitized.isBlank() -> "Escribe una pregunta para conversar."
            normalized == "repitelo" || normalized == "repite" ->
                lastAnswer ?: "No hay una respuesta anterior todavía."
            normalized.startsWith("hola") || normalized == "buenas" ->
                "Hola. Soy JARVIS en modo observador. Puedo explicar mis funciones locales."
            normalized.contains("privacidad") || normalized.contains("memoria") ->
                "La conversación es local. Tu historial puede cifrarse en este dispositivo solo si activas la memoria, y puedes borrarlo."
            normalized.contains("estado") ->
                "JARVIS funciona en modo observador. Las acciones sensibles necesitan permiso visible."
            normalized.contains("ayuda") || normalized.contains("que puedes hacer") ->
                "Puedo consultar el estado en modo observador, conversar sobre funciones disponibles, reconocer texto de imágenes elegidas y abrir GameHub por pulsación."
            normalized.contains("quien eres") ->
                "Soy JARVIS OMEGA, un prototipo de asistente Android. No soy una IA general conectada."
            else ->
                "No tengo una conexión a un modelo conversacional general en esta versión. Puedo ayudarte con estado, privacidad y funciones de JARVIS."
        }.take(300)
        turns.add("Tú: $sanitized".take(400))
        turns.add("JARVIS: $answer".take(400))
        while (turns.size > 24) turns.removeAt(0)
        lastAnswer = answer
        return answer
    }

    fun history(): List<String> = turns.toList()

    fun clear() {
        turns.clear()
        lastAnswer = null
    }
}
