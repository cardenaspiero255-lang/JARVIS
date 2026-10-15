package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Bounded offline conversational continuity: retains only a known topic enum,
 * never a microphone transcript, tool instruction, location, personal detail,
 * image, or model output. It is not a general language model.
 */
class LocalTopicDialogue {
    private enum class Topic { AI, ANDROID, CODE, VISION, GAMEHUB, VOICE, MEMORY, PRIVACY }
    private var topic: Topic? = null

    fun answer(question: String): String? {
        val phrase = normalize(question) ?: return null
        when (phrase) {
            "cuentame mas", "explicame mas", "explicalo mejor",
            "y como funciona", "y como funciona eso" -> return detail(topic)
            "de que estabamos hablando", "de que hablabamos" ->
                return summary(topic)
            "olvida el tema", "cambiemos de tema" -> {
                topic = null
                return TOPIC_CLEARED
            }
        }

        val recognized = topicForQuestion(phrase) ?: return null
        topic = recognized
        return intro(recognized)
    }

    /** Called for separately validated FAQ replies; never parses an action. */
    fun observeKnownFaq(question: String) {
        val phrase = normalize(question) ?: return
        topic = when (phrase) {
            "como funciona la vision", "jarvis como funciona la vision" -> Topic.VISION
            "como abro gamehub", "jarvis como abro gamehub" -> Topic.GAMEHUB
            "como funciona la memoria", "privacidad", "jarvis privacidad",
            "memoria", "jarvis memoria" ->
                if (phrase.contains("privacidad")) Topic.PRIVACY else Topic.MEMORY
            "como funciona la privacidad", "que pasa con mi privacidad" -> Topic.PRIVACY
            else -> topic
        }
    }

    /** Recover only a whitelisted topic from user-owned decrypted typed history. */
    fun restoreTopicHint(question: String) {
        val phrase = normalize(question) ?: return
        val selected = topicForQuestion(phrase)
        if (selected != null) topic = selected else observeKnownFaq(question)
    }

    /** Metadata only: an enum name, not a user message or voice transcript. */
    fun contextToken(): String? = topic?.name

    /** Invalid or tampered tokens cannot create a topic or an action. */
    fun restoreContextToken(value: String) {
        topic = if (value.length <= 24 && Regex("""[A-Z_]+""").matches(value)) {
            Topic.values().firstOrNull { it.name == value }
        } else null
    }

    fun clear() { topic = null }

    private fun topicForQuestion(phrase: String): Topic? = when (phrase) {
        "que es la inteligencia artificial", "que es ia",
        "que es una inteligencia artificial", "explicame la inteligencia artificial" -> Topic.AI
        "que es android", "explicame android" -> Topic.ANDROID
        "que es programar", "que es la programacion", "para que sirve programar" -> Topic.CODE
        "que es omega vision", "que es la vision de jarvis" -> Topic.VISION
        "que es gamehub ultra", "para que sirve gamehub ultra" -> Topic.GAMEHUB
        "como funciona la voz de jarvis", "como habla jarvis" -> Topic.VOICE
        "que es la memoria privada", "como guarda memoria jarvis" -> Topic.MEMORY
        "como protege datos jarvis", "como protege mis datos jarvis" -> Topic.PRIVACY
        else -> null
    }

    private fun normalize(input: String): String? {
        if (input.isBlank() || input.length > 160 ||
            input.any { Character.isISOControl(it) }) return null
        val normalized = Normalizer.normalize(input.lowercase(Locale.ROOT).trim(),
            Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[¿?¡!.,:;]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        return normalized.takeIf { Regex("""[a-z0-9 ]+""").matches(it) }
    }

    private fun intro(current: Topic): String = when (current) {
        Topic.AI -> AI_INTRO
        Topic.ANDROID -> ANDROID_INTRO
        Topic.CODE -> CODE_INTRO
        Topic.VISION -> VISION_INTRO
        Topic.GAMEHUB -> GAMEHUB_INTRO
        Topic.VOICE -> VOICE_INTRO
        Topic.MEMORY -> MEMORY_INTRO
        Topic.PRIVACY -> PRIVACY_INTRO
    }

    private fun detail(current: Topic?): String = when (current) {
        Topic.AI -> AI_DETAILS
        Topic.ANDROID -> ANDROID_DETAILS
        Topic.CODE -> CODE_DETAILS
        Topic.VISION -> VISION_DETAILS
        Topic.GAMEHUB -> GAMEHUB_DETAILS
        Topic.VOICE -> VOICE_DETAILS
        Topic.MEMORY -> MEMORY_DETAILS
        Topic.PRIVACY -> PRIVACY_DETAILS
        null -> NO_TOPIC
    }

    private fun summary(current: Topic?): String = when (current) {
        Topic.AI -> AI_SUMMARY
        Topic.ANDROID -> ANDROID_SUMMARY
        Topic.CODE -> CODE_SUMMARY
        Topic.VISION -> VISION_SUMMARY
        Topic.GAMEHUB -> GAMEHUB_SUMMARY
        Topic.VOICE -> VOICE_SUMMARY
        Topic.MEMORY -> MEMORY_SUMMARY
        Topic.PRIVACY -> PRIVACY_SUMMARY
        null -> NO_TOPIC
    }

    companion object {
        const val AI_INTRO =
            "La inteligencia artificial utiliza modelos y datos para reconocer patrones y generar resultados. No implica que un sistema comprenda todo."
        const val AI_DETAILS =
            "Un modelo se entrena con ejemplos y después procesa nuevas entradas. Es importante verificar sus respuestas, porque también puede equivocarse."
        const val AI_SUMMARY = "Estábamos hablando de qué es la inteligencia artificial."
        const val ANDROID_INTRO =
            "Android es un sistema operativo para teléfonos y otros dispositivos. Gestiona aplicaciones, recursos y permisos."
        const val ANDROID_DETAILS =
            "Android limita lo que puede hacer cada aplicación. El micrófono, la accesibilidad y la captura de pantalla requieren autorizaciones distintas."
        const val ANDROID_SUMMARY = "Estábamos hablando del sistema operativo Android."
        const val CODE_INTRO =
            "Programar consiste en escribir instrucciones que una computadora ejecuta. Python y Kotlin son dos lenguajes de programación."
        const val CODE_DETAILS =
            "El código se divide en funciones y módulos. Las pruebas ayudan a detectar regresiones, pero también hace falta comprobar el uso real."
        const val CODE_SUMMARY = "Estábamos hablando de programación."
        const val VISION_INTRO =
            "Omega Vision puede reconocer texto de imágenes elegidas. La observación temporal de pantalla está en pruebas y requiere permiso de Android."
        const val VISION_DETAILS =
            "La visión temporal usa una autorización por sesión y una notificación visible. JARVIS no debe capturar pantallas protegidas ni pulsar botones sin permiso."
        const val VISION_SUMMARY = "Estábamos hablando de Omega Vision."
        const val GAMEHUB_INTRO =
            "GameHub Ultra es una aplicación independiente que JARVIS puede abrir con tu confirmación. El control directo de su motor aún no está conectado."
        const val GAMEHUB_DETAILS =
            "Para trabajar juntos de verdad, JARVIS y GameHub necesitan una conexión autenticada, permisos limitados y pruebas de compatibilidad."
        const val GAMEHUB_SUMMARY = "Estábamos hablando de GameHub Ultra."
        const val VOICE_INTRO =
            "JARVIS puede reconocer una orden cuando pulsas HABLAR y responder con la voz local de Android. La escucha permanente no está activada."
        const val VOICE_DETAILS =
            "El reconocimiento y la síntesis son procesos distintos. El reconocimiento interpreta tu voz y el sintetizador convierte una respuesta autorizada en audio."
        const val VOICE_SUMMARY = "Estábamos hablando de cómo funciona la voz de JARVIS."
        const val MEMORY_INTRO =
            "JARVIS conserva contexto breve durante esta sesión. La memoria cifrada en el dispositivo es opcional y necesita activación explícita."
        const val MEMORY_DETAILS =
            "El contexto de voz guarda solo un tema conocido, no transcripciones. El historial escrito puede cifrarse únicamente cuando activas esa opción."
        const val MEMORY_SUMMARY = "Estábamos hablando de la memoria privada de JARVIS."
        const val PRIVACY_INTRO =
            "La privacidad de JARVIS depende de permisos separados y una parada inmediata. No puede ocultar las alertas de captura obligatorias de Android."
        const val PRIVACY_DETAILS =
            "El bloqueo de sesión detiene la voz y otros módulos. La memoria cifrada se borra solo mediante una acción de borrado explícita."
        const val PRIVACY_SUMMARY = "Estábamos hablando de la privacidad de JARVIS."
        const val NO_TOPIC = "Primero dime un tema concreto y después puedo ampliarlo."
        const val TOPIC_CLEARED =
            "Ya no usaré ese tema para continuar la conversación. Esto no borra el historial escrito."

        val SAFE_SPOKEN_REPLIES: Set<String> = setOf(
            AI_INTRO, AI_DETAILS, AI_SUMMARY,
            ANDROID_INTRO, ANDROID_DETAILS, ANDROID_SUMMARY,
            CODE_INTRO, CODE_DETAILS, CODE_SUMMARY,
            VISION_INTRO, VISION_DETAILS, VISION_SUMMARY,
            GAMEHUB_INTRO, GAMEHUB_DETAILS, GAMEHUB_SUMMARY,
            VOICE_INTRO, VOICE_DETAILS, VOICE_SUMMARY,
            MEMORY_INTRO, MEMORY_DETAILS, MEMORY_SUMMARY,
            PRIVACY_INTRO, PRIVACY_DETAILS, PRIVACY_SUMMARY,
            NO_TOPIC, TOPIC_CLEARED
        )
    }
}
