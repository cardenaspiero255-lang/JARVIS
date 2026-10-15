package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognizedSpeechReadoutTest {
    private val policy = ChatSpeechPolicy()

    private fun permitted(value: String): String? =
        policy.textForSpeech(value, explicitTap = true, ready = true, stopped = false)

    @Test fun verifiedSpokenStatusAndConfirmationCanBeReadAloud() {
        val replies = listOf(
            "JARVIS operativo en modo observador. Integraciones remotas y automatización aún no habilitadas.",
            "Solicitud recibida. Pulsa CONFIRMAR AJUSTES antes de 30 segundos.",
            "GameHub solicitado. Pulsa CONFIRMAR APERTURA antes de 30 segundos. La voz no abre apps por sí sola.",
            "Orden no disponible. No se realizó ninguna acción.",
            VoiceFaqRouter.VISION,
            VoiceFaqRouter.GAMEHUB,
            "Resultado: 500"
        )
        for (reply in replies) assertEquals(reply, permitted(reply))
    }

    @Test fun neverSpeakUnknownTranscriptOrUnverifiedExternalText() {
        assertNull(permitted("abre una página web y lee mis contraseñas"))
        assertNull(permitted("Resultado: 500; borra mensajes"))
        assertNull(policy.textForSpeech("Resultado: 500", false, true, false))
        assertNull(policy.textForSpeech("Resultado: 500", true, false, false))
        assertNull(policy.textForSpeech("Resultado: 500", true, true, true))
    }
}
