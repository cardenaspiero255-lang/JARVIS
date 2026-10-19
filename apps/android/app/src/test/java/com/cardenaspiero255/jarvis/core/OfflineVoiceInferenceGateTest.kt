package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Regression: "Jarvis, para" must remain available while GGUF is decoding. */
class OfflineVoiceInferenceGateTest {
    @Test fun stopIsRecognizedDuringInferenceWithoutAcceptingAnotherQuestion() {
        val gate = OfflineVoiceInferenceGate()
        assertTrue(gate.beginInference())
        assertFalse(gate.beginInference())
        assertTrue(gate.inferenceActive())
        assertTrue(gate.isEmergencyStop("Jarvis, para"))
        assertTrue(gate.isEmergencyStop("jarvis detente"))
        assertFalse(gate.isEmergencyStop("Jarvis, qué es una CPU"))
        assertFalse(gate.isEmergencyStop("jarvis abre ajustes"))
        gate.finishInference()
        assertFalse(gate.inferenceActive())
        assertFalse(gate.isEmergencyStop("jarvis para"))
        assertTrue(gate.beginInference())
    }

    @Test fun voiceStopDoesNotRequireSpeakerEnrollmentOrASecondWake() {
        val gate = OfflineVoiceInferenceGate()
        assertTrue(gate.beginInference())
        assertFalse(gate.isEmergencyStop("para"))
        assertTrue(gate.isEmergencyStop("JARVIS, PARA"))
        assertFalse(gate.isEmergencyStop("Jarvis, por qué hay estrellas"))
    }
}
