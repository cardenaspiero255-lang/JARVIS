package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGatewayPolicyTest {
    @Test fun acceptsOnlyExplicitHttpsBackendWithoutRedirectableQueryOrCredentials() {
        assertTrue(AiGatewayPolicy.endpointAllowed("https://jarvis.example.org/v1/chat"))
        assertFalse(AiGatewayPolicy.endpointAllowed("http://jarvis.example.org/v1/chat"))
        assertFalse(AiGatewayPolicy.endpointAllowed("https://jarvis.example.org@evil.org/v1/chat"))
        assertFalse(AiGatewayPolicy.endpointAllowed("https://jarvis.example.org/v1/chat?token=secret"))
        assertFalse(AiGatewayPolicy.endpointAllowed("https://jarvis.example.org/v1/chat#secret"))
        assertFalse(AiGatewayPolicy.endpointAllowed("https://127.0.0.1/v1/chat"))
        assertFalse(AiGatewayPolicy.endpointAllowed("https://localhost/v1/chat"))
        assertFalse(AiGatewayPolicy.endpointAllowed("https://jarvis.example.org/other"))
    }

    @Test fun accountPairingTokenMustBeLongAndNotAnOpenAiApiKey() {
        assertFalse(AiGatewayPolicy.tokenAllowed("short"))
        assertFalse(AiGatewayPolicy.tokenAllowed("sk-proj-" + "x".repeat(80)))
        assertFalse(AiGatewayPolicy.tokenAllowed("abc\ndef".repeat(8)))
        assertTrue(AiGatewayPolicy.tokenAllowed("separate-private-app-token-12345678901234567890"))
    }

    @Test fun onlyBoundedTextCanBeSpokenAfterAnExplicitRequest() {
        assertEquals("Hola, usuario.", AiGatewayPolicy.spokenModelText("Hola, usuario."))
        assertEquals("Un texto", AiGatewayPolicy.spokenModelText("**Un** texto"))
        assertNull(AiGatewayPolicy.spokenModelText(" "))
        assertNull(AiGatewayPolicy.spokenModelText(null))
        assertTrue(AiGatewayPolicy.spokenModelText("a".repeat(7000))!!.length <= 1200)
    }

    @Test fun voiceCloudRequiresPriorPairingAndSeparateVoiceConsent() {
        assertFalse(AiGatewayPolicy.allowCloudRequest(false, false, true))
        assertTrue(AiGatewayPolicy.allowCloudRequest(true, false, false))
        assertFalse(AiGatewayPolicy.allowCloudRequest(true, false, true))
        assertTrue(AiGatewayPolicy.allowCloudRequest(true, true, true))
    }
}
