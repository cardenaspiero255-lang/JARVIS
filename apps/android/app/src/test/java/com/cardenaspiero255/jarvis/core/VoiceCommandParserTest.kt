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
}
