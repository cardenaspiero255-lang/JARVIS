package com.cardenaspiero255.jarvis.core

/** Only on-device responses from the bounded local chat are eligible for TTS. */
class ChatSpeechPolicy {
    private val spokenNumbers = Regex("""^Resultado: -?\d{1,3}(?:\.\d{3})*(?:,\d{1,10})?$""")
    private val fixed = setOf(
        VoiceFaqRouter.VISION,
        VoiceFaqRouter.GAMEHUB,
        VoiceFaqRouter.STOP,
        // Only deterministic Android-local messages produced after verified
        // recognition; never read out the raw ASR transcript or screen OCR.
        "JARVIS operativo en modo observador. Integraciones remotas y automatización aún no habilitadas.",
        "Solicitud recibida. Pulsa CONFIRMAR AJUSTES antes de 30 segundos.",
        "GameHub solicitado. Pulsa CONFIRMAR APERTURA antes de 30 segundos. La voz no abre apps por sí sola.",
        "Orden no disponible. No se realizó ninguna acción.",
        "Escribe una pregunta para conversar.",
        "No hay una respuesta anterior todavía.",
        "No hay un resultado anterior para continuar.",
        "No pude interpretar esa expresión matemática.",
        "La expresión es demasiado larga para el cálculo local.",
        "Resultado fuera del límite del cálculo local.",
        "No se puede dividir por cero.",
        "Hola. Soy JARVIS en modo observador. Puedo explicar mis funciones locales.",
        "La conversación es local. Tu historial puede cifrarse en este dispositivo solo si activas la memoria, y puedes borrarlo.",
        "JARVIS funciona en modo observador. Las acciones sensibles necesitan permiso visible.",
        "Puedo consultar el estado en modo observador, conversar sobre funciones disponibles, reconocer texto de imágenes elegidas y abrir GameHub por pulsación.",
        "Soy JARVIS OMEGA, un prototipo de asistente Android. No soy una IA general conectada.",
        "No tengo una conexión a un modelo conversacional general en esta versión. Puedo ayudarte con estado, privacidad y funciones de JARVIS."
    )

    /** Invalid, restored or third-party content is denied rather than spoken. */
    fun textForSpeech(answer: String?, explicitTap: Boolean, ready: Boolean, stopped: Boolean): String? {
        if (!explicitTap || !ready || stopped || answer == null || answer.length > 300) return null
        return answer.takeIf { it in fixed || spokenNumbers.matches(it) }
    }
}
