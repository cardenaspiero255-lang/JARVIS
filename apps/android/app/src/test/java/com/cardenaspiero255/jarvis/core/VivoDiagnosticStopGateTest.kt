package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoDiagnosticStopGateTest {
    private val gate = VivoDiagnosticStopGate()

    @Test fun stoppingAudioMustNotOverwriteUnrelatedQwenStatus() {
        assertEquals("GGUF_NO_IMPORTADO",
            gate.qwenStatusAfterStop(false, "GGUF_NO_IMPORTADO"))
        assertEquals("TEXTO_NO_VACIO_NO_CERTIFICADO",
            gate.qwenStatusAfterStop(false, "TEXTO_NO_VACIO_NO_CERTIFICADO"))
        assertEquals("NO_PROBADO", gate.qwenStatusAfterStop(false, "NO_PROBADO"))
    }

    @Test fun activeQwenMustBecomeCancelled() {
        assertEquals("CANCELADO_POR_USUARIO",
            gate.qwenStatusAfterStop(true, "EN_PROCESO"))
    }

    @Test fun aStaleStopReceiptOrStillActiveRecorderIsNotAnAcknowledgment() {
        assertFalse(gate.confirmStop(101L, 100L, false, false))
        assertFalse(gate.confirmStop(101L, 101L, true, false))
        assertFalse(gate.confirmStop(101L, 101L, false, true))
        assertFalse(gate.confirmStop(101L, -1L, false, false))
        assertFalse(gate.confirmStop(0L, 0L, false, false))
        assertTrue(gate.confirmStop(101L, 101L, false, false))
    }
}
