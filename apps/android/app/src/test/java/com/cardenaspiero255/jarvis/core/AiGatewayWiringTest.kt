package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fast JVM gate for the built-in HTTPS target and refusal of unsafe URLs. */
class AiGatewayWiringTest {
    @Test fun connectsOnlyToPublicVercelGatewayAndNotAnEmbeddedSecret() {
        val endpoint = AiGatewayPolicy.DEFAULT_ENDPOINT
        assertEquals("https://jarvis-ai-gateway.vercel.app/v1/chat",endpoint)
        assertTrue(AiGatewayPolicy.officialEndpointAllowed(endpoint))
        assertFalse(AiGatewayPolicy.officialEndpointAllowed("http://jarvis-ai-gateway.vercel.app/v1/chat"))
        assertFalse(AiGatewayPolicy.officialEndpointAllowed(
            "https://jarvis-ai-gateway.vercel.app/v1/chat?token=unsafe"))
        assertFalse(AiGatewayPolicy.officialEndpointAllowed("https://attacker.example/v1/chat"))
    }

    @Test fun typedChatCanBePairedWhileVoiceRemainsOffWithoutSeparateConsent() {
        assertTrue(AiGatewayPolicy.allowCloudRequest(true,false,false))
        assertFalse(AiGatewayPolicy.allowCloudRequest(true,false,true))
        assertFalse(AiGatewayPolicy.allowCloudRequest(false,true,false))
    }
}
