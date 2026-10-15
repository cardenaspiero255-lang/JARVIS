package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenDeviceHealthTest {
    private val parser = VoiceCommandParser()

    @Test fun explicitBatteryAndThermalQuestionsOnlyReadTheDevice() {
        for (phrase in listOf("JARVIS, cuánta batería tengo",
                              "cuanta bateria tengo", "Jarvis batería",
                              "estado de bateria", "jarvis estado termico",
                              "cuanta ram libre tengo")) {
            assertEquals(VoiceAction.READ_DEVICE_HEALTH, parser.parse(phrase))
        }
    }

    @Test fun unknownRequestsCannotTriggerDeviceActions() {
        for (phrase in listOf("jarvis activa el ahorro de batería",
                              "baja la temperatura del procesador",
                              "jarvis batería y borra mis archivos",
                              "jarvis lee mis mensajes", "cambia la frecuencia de cpu")) {
            assertEquals(VoiceAction.UNSUPPORTED, parser.parse(phrase))
        }
    }

    @Test fun speechRequiresFreshVisibleUserTapAndLocalTts() {
        val sample = DeviceHealthSnapshot(83, true, 3, 1_073_741_824L,
            2_147_483_648L, false)
        val speech = DeviceHealthSpeechPolicy()
        assertNull(speech.textForSpeech(sample, false, true, false))
        assertNull(speech.textForSpeech(sample, true, false, false))
        assertNull(speech.textForSpeech(sample, true, true, true))
        val allowed = speech.textForSpeech(sample, true, true, false)!!
        assertTrue(allowed.contains("83 por ciento"))
        assertTrue(allowed.contains("estado térmico alto"))
        assertTrue(allowed.contains("1024 MiB"))
    }

    @Test fun missingOrInvalidReadingsNeverInventNumbers() {
        val policy = DeviceHealthSpeechPolicy()
        val invalid = DeviceHealthSnapshot(500, null, -1, 2_000L, 100L, null)
        val text = policy.textForSpeech(invalid, true, true, false)!!
        assertTrue(text.contains("Batería no disponible"))
        assertTrue(text.contains("estado térmico no disponible"))
        assertTrue(text.contains("RAM no disponible"))
    }

    @Test fun severeThermalConditionDoesNotClaimCpuControl() {
        val policy = DeviceHealthSpeechPolicy()
        val sample = DeviceHealthSnapshot(60, false, 5, null, null, true)
        val text = policy.textForSpeech(sample, true, true, false)!!
        assertTrue(text.contains("emergencia"))
        assertTrue(text.contains("memoria baja"))
        assertTrue(!text.contains("optimización aplicada"))
        assertTrue(text.length < 320)
    }
}
