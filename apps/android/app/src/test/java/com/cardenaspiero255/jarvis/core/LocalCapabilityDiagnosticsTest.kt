package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalCapabilityDiagnosticsTest {
    @Test fun reportsRealSignalsWithoutClaimingCertifiedOperation() {
        val result = LocalCapabilityDiagnostics.format(LocalCapabilitySnapshot(
            androidApi = 34, gameHubLaunchable = true,
            handsConnected = true, handsStopped = false,
            offlineSpeechReady = true, speechRecognizerAvailable = true,
            encryptedMemoryUnlocked = false
        ))
        assertTrue(result.contains("Android API: 34"))
        assertTrue(result.contains("GameHub Ultra: lanzador detectado"))
        assertTrue(result.contains("Hands: conectado, autorizado en esta sesión"))
        assertTrue(result.contains("Voz TTS local: motor preparado"))
        assertTrue(result.contains("Memoria privada: cerrada"))
        assertTrue(result.contains("SIN CERTIFICACIÓN"))
    }

    @Test fun absentServicesCannotBePresentedAsWorking() {
        val result = LocalCapabilityDiagnostics.format(LocalCapabilitySnapshot(
            androidApi = 26, gameHubLaunchable = false,
            handsConnected = false, handsStopped = true,
            offlineSpeechReady = false, speechRecognizerAvailable = false,
            encryptedMemoryUnlocked = false
        ))
        assertTrue(result.contains("GameHub Ultra: no se detectó lanzador"))
        assertTrue(result.contains("Hands: desconectado"))
        assertTrue(result.contains("Voz TTS local: no preparada"))
        assertTrue(result.contains("Reconocimiento Android: no detectado"))
        assertFalse(result.contains("Pruebas físicas superadas"))
    }

    @Test fun reportCannotIncludeUserConversationOrSecrets() {
        val result = LocalCapabilityDiagnostics.format(LocalCapabilitySnapshot(
            androidApi = -1, gameHubLaunchable = true,
            handsConnected = true, handsStopped = true,
            offlineSpeechReady = false, speechRecognizerAvailable = true,
            encryptedMemoryUnlocked = true
        ))
        assertTrue(result.contains("Android API: desconocida"))
        assertTrue(result.contains("Memoria privada: abierta en esta sesión"))
        assertFalse(result.contains("contraseña"))
        assertFalse(result.contains("token"))
    }
}
