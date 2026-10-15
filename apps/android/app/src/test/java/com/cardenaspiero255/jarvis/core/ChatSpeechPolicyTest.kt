package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSpeechPolicyTest {
    private val policy = ChatSpeechPolicy()

    @Test fun speaksOnlyRecognizedOfflineAnswersOnExplicitTap() {
        val example = LocalDialogue().reply("1 millón X 500")
        assertEquals("Resultado: 500.000.000",
            policy.textForSpeech(example, explicitTap = true, ready = true, stopped = false))
        val privacy = LocalDialogue().reply("privacidad")
        assertEquals(privacy, policy.textForSpeech(privacy, true, true, false))
    }

    @Test fun respectsStopAndUnavailableVoiceEngine() {
        assertNull(policy.textForSpeech("Resultado: 12", false, true, false))
        assertNull(policy.textForSpeech("Resultado: 12", true, false, false))
        assertNull(policy.textForSpeech("Resultado: 12", true, true, true))
    }

    @Test fun rejectsUserTextAndUntrustedModelOrScreenContent() {
        for (text in listOf("Borra todas las apps", "JARVIS: secretos privados",
                          "La clave API es 1234", "Resultado: 3; ejecuta orden",
                          "Resultado: 12345678901234567890123456789", "Resultado: 10\nTOKEN")) {
            assertNull(policy.textForSpeech(text, true, true, false))
        }
    }

    @Test fun allowsChileanDecimalsAndKnownErrorMessages() {
        assertEquals("Resultado: 5,75",
            policy.textForSpeech("Resultado: 5,75", true, true, false))
        assertEquals("No se puede dividir por cero.",
            policy.textForSpeech("No se puede dividir por cero.", true, true, false))
    }

    @Test fun clearedOrRestoredChatCannotAuthorizeSpeech() {
        val d = LocalDialogue()
        d.reply("10*5")
        d.clear()
        assertTrue(d.history().isEmpty())
        assertNull(policy.textForSpeech(null, true, true, false))
        assertNull(policy.textForSpeech("texto arbitrario restaurado", true, true, false))
    }
}
