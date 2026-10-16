package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLevelPolicyTest {
    @Test fun voiceLevelClampsUntrustedProviderData() {
        assertEquals(0, SpeechLevelPolicy.progress(Float.NaN))
        assertEquals(0, SpeechLevelPolicy.progress(Float.NEGATIVE_INFINITY))
        assertEquals(0, SpeechLevelPolicy.progress(-20f))
        assertEquals(100, SpeechLevelPolicy.progress(100f))
        assertTrue(SpeechLevelPolicy.progress(6f) > SpeechLevelPolicy.progress(1f))
    }
    @Test fun noAudioUpdatesDoNotMeanMicPermissionDenied() {
        assertEquals("Esperando información del servicio de voz",
            SpeechLevelPolicy.readout(hasRmsCallback = false, hasSpeechStart = false))
        assertEquals("Voz detectada por Android",
            SpeechLevelPolicy.readout(hasRmsCallback = false, hasSpeechStart = true))
        assertEquals("Recibiendo niveles de audio del servicio",
            SpeechLevelPolicy.readout(hasRmsCallback = true, hasSpeechStart = false))
    }
}
