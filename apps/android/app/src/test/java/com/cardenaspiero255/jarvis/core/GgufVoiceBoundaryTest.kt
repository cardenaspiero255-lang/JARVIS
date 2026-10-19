package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Explicitly records the current VOICE capability gap, and checks safety. */
class GgufVoiceBoundaryTest {
    @Test fun spokenGeneralKnowledgeDoesNotCreateAutonomousLlmAction() {
        val recognized = VoiceCommandParser().parse("¿Qué es una estrella de neutrones?")
        assertEquals(VoiceAction.UNSUPPORTED, recognized)
        assertFalse(LocalGgufPolicy.canIssueDeviceActions)
        // Native model generation is a separate user-tapped chat operation.
    }

    @Test fun generatedFreeFormTextIsNotAutomaticallyAuthorizedForSpeech() {
        val policy = ChatSpeechPolicy()
        assertNull(policy.textForSpeech(
            "He cambiado los ajustes del teléfono automáticamente.",
            explicitTap = false, ready = true, stopped = false))
        assertNull(policy.textForSpeech(
            "He cambiado los ajustes del teléfono automáticamente.",
            explicitTap = true, ready = true, stopped = false))
        assertNull(policy.textForSpeech(
            "Resultado del modelo: confirma la clave secreta",
            explicitTap = true, ready = true, stopped = false))
        assertNull(policy.textForSpeech(
            "Estoy leyendo contenido privado de otras aplicaciones",
            explicitTap = true, ready = true, stopped = false))
    }

    @Test fun speechStopAndReadoutPermissionsRemainSeparateFromModelText() {
        val policy = ChatSpeechPolicy()
        assertNull(policy.textForSpeech("Resultado: 12",
            explicitTap = false, ready = true, stopped = false))
        assertNull(policy.textForSpeech("Resultado: 12",
            explicitTap = true, ready = true, stopped = true))
        assertEquals(VoiceAction.STOP_SESSION,
            VoiceCommandParser().parse("Jarvis para"))
    }
}
