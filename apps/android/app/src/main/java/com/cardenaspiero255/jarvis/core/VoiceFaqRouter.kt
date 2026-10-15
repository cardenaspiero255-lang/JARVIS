package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Narrow read-only FAQ for explicitly captured speech. No transcript storage,
 * no Android actions, no inference from arbitrary text or prompt instructions.
 */
object VoiceFaqRouter {
    const val VISION =
        "Omega Vision reconoce texto de imágenes elegidas por ti. La visión continua aún no está incluida en esta versión."
    const val GAMEHUB =
        "Para abrir GameHub Ultra, pulsa el botón ABRIR GAMEHUB ULTRA. Por voz, confirma su apertura en la pantalla."
    const val STOP =
        "Para detener las funciones locales, pulsa BLOQUEAR SESIÓN — PRIVACIDAD. No borra el historial cifrado."

    private const val GREETING =
        "Hola. Soy JARVIS en modo observador. Puedo explicar mis funciones locales."
    private const val HELP =
        "Puedo consultar el estado en modo observador, conversar sobre funciones disponibles, reconocer texto de imágenes elegidas y abrir GameHub por pulsación."
    private const val PRIVACY =
        "La conversación es local. Tu historial puede cifrarse en este dispositivo solo si activas la memoria, y puedes borrarlo."
    private const val IDENTITY =
        "Soy JARVIS OMEGA, un prototipo de asistente Android. No soy una IA general conectada."

    fun answer(transcript: String?): String? {
        if (transcript == null || transcript.isBlank() || transcript.length > 160 ||
            transcript.any { Character.isISOControl(it) }) return null
        val ascii = Normalizer.normalize(
            transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD
        ).replace(Regex("""\p{M}+"""), "")
        if (!Regex("""[a-z0-9¿?.,! ]+""").matches(ascii)) return null
        val phrase = ascii.replace(Regex("""[¿?.,!]+"""), " ")
            .replace(Regex(""" +"""), " ").trim()
        return when (phrase) {
            "hola", "hola jarvis", "jarvis hola" -> GREETING
            "ayuda", "jarvis ayuda", "que puedes hacer",
            "jarvis que puedes hacer", "que puedes hacer jarvis" -> HELP
            "privacidad", "jarvis privacidad", "como funciona la privacidad",
            "que pasa con mi privacidad" -> PRIVACY
            "quien eres", "jarvis quien eres", "quien eres jarvis" -> IDENTITY
            "como funciona la vision", "jarvis como funciona la vision" -> VISION
            "como abro gamehub", "jarvis como abro gamehub" -> GAMEHUB
            "como detengo jarvis", "jarvis como te detengo" -> STOP
            else -> null
        }
    }
}
