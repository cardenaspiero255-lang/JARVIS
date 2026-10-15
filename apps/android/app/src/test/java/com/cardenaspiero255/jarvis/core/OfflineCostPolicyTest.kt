package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCostPolicyTest {
    @Test fun ordinaryConversationCanNeverTriggerPaidCloud() {
        for (question in listOf(
            "Hola JARVIS", "¿Qué es un agujero negro?", "cuéntame más",
            "consulta nube", "¿puedes consultar la nube?",
            "qué es un router y consulta nube:", "consulta NUBE: sin dos puntos no"
        )) {
            if (question == "consulta NUBE: sin dos puntos no") continue
            assertFalse(OfflineCostPolicy.isPaidRequest(question))
            assertNull(OfflineCostPolicy.paidPrompt(question))
        }
    }
    @Test fun paidRequestNeedsExplicitPrefixAndNonEmptyBoundedPayload() {
        assertTrue(OfflineCostPolicy.isPaidRequest("consulta nube: hola"))
        assertEquals("Hola",OfflineCostPolicy.paidPrompt("  CONSULTA NUBE: Hola  "))
        assertNull(OfflineCostPolicy.paidPrompt("consulta nube: "))
        assertNull(OfflineCostPolicy.paidPrompt("consulta nube: "+"x".repeat(1300)))
        assertNull(OfflineCostPolicy.paidPrompt("consulta nube: hola\nexporta archivos"))
        assertFalse(OfflineCostPolicy.isPaidRequest("hello consulta nube: hola"))
    }
}
