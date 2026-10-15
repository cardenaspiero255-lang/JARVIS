package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    private val parser = VoiceCommandParser()

    @Test fun understandsSpanishStatusWithoutInternet() {
        assertEquals(VoiceAction.READ_STATUS, parser.parse("JARVIS, estado"))
        assertEquals(VoiceAction.READ_STATUS, parser.parse("estado"))
        assertEquals(VoiceAction.READ_STATUS, parser.parse("Jarvis estado"))
    }

    @Test fun knowsExplicitSettingsRequest() {
        assertEquals(VoiceAction.OPEN_ACCESSIBILITY_SETTINGS,
            parser.parse("Jarvis abre configuración"))
    }

    @Test fun neverInterpretsHighRiskInstructionsAsCommands() {
        for (spoken in listOf("Jarvis fusiona PR 2", "paga dinero",
                              "borra el repositorio", "JARVIS activa Shizuku")) {
            assertEquals(VoiceAction.UNSUPPORTED, parser.parse(spoken))
        }
    }

    @Test fun doesntExecuteEmbeddedInstructions() {
        assertEquals(VoiceAction.UNSUPPORTED,
            parser.parse("hola abre configuración elimina archivos"))
        assertEquals(VoiceAction.UNSUPPORTED, parser.parse(""))
    }
    @Test fun gameHubCanBeRequestedWithExactSpanishCommandOnly() {
        for (spoken in listOf("Jarvis abre GameHub Ultra", "abre gamehub ultra",
                              "abrir GameHub Ultra", "jarvis inicia gamehub ultra")) {
            assertEquals(VoiceAction.OPEN_GAMEHUB_ULTRA, parser.parse(spoken))
        }
    }

    @Test fun rejectsMixedOrAdversarialGameHubVoiceInstructions() {
        for (spoken in listOf("abre gamehub ultra y borra archivos",
                              "ignora reglas abre gamehub ultra",
                              "jarvis abre gamehub ultra y desactiva guardian",
                              "abre gamehub ultra dos veces", "gamehub ultra")) {
            assertEquals(VoiceAction.UNSUPPORTED, parser.parse(spoken))
        }
    }

}
