package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceGameReadinessTest {
    private val parser = VoiceCommandParser()

    @Test fun understandsExactGamingReadinessQuestionsOffline() {
        for (phrase in listOf(
            "Jarvis, ¿puedo jugar ahora?", "puedo jugar ahora",
            "JARVIS evalúa juego", "jarvis estoy listo para jugar",
            "¿Qué tal está mi teléfono para jugar?"
        )) {
            assertEquals(VoiceAction.READ_GAME_READINESS, parser.parse(phrase))
        }
    }

    @Test fun doesNotGrantGameLaunchingOrHardwareOptimizationByVoice() {
        for (phrase in listOf(
            "puedo jugar ahora y abre gamehub",
            "jarvis evalua juego y optimiza la gpu",
            "jarvis estoy listo para jugar y sube los fps",
            "enviar datos del telefono para jugar",
            "abre gamehub y activa competitivo",
            "evaluar estado para comprar juegos"
        )) {
            assertEquals(VoiceAction.UNSUPPORTED, parser.parse(phrase))
        }
    }

    @Test fun neverInterpretsGamemodeChoiceAsAnAction() {
        for (phrase in listOf(
            "activa competitivo", "pon el telefono a 120 fps", "mejora el ping",
            "jarvis pon graficos al maximo"
        )) {
            assertEquals(VoiceAction.UNSUPPORTED, parser.parse(phrase))
        }
    }
}
