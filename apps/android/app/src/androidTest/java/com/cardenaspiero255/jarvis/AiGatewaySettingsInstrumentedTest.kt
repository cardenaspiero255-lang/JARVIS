package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.jarvis.core.AiGatewayPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies a one-time pairing survives reopening the app, and its bearer token
 * never appears in Android SharedPreferences in plaintext.
 */
@RunWith(AndroidJUnit4::class)
class AiGatewaySettingsInstrumentedTest {
    private val validToken = "private-device-token-for-test-12345678901234567890"

    @Test fun firstPairingIsEncryptedAndPersistsWithoutReenteringIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AiGatewaySettings(context)
        settings.revokeByTap()
        assertNull(settings.get())
        assertFalse(settings.hasStoredPairing())
        assertFalse(settings.configureByTap("short", true))
        assertTrue(settings.configureByTap(validToken, false))
        val secondInstance = AiGatewaySettings(context)
        assertTrue(secondInstance.hasStoredPairing())
        val restored = secondInstance.get()
        assertEquals(AiGatewayPolicy.DEFAULT_ENDPOINT, restored?.endpoint)
        assertEquals(validToken, restored?.token)
        assertFalse(restored?.allowVoice ?: true)
        val data = context.getSharedPreferences("omega_ai_gateway_v1", 0)
        assertFalse(data.all.values.joinToString().contains(validToken))
        assertFalse(data.all.values.joinToString().contains("sk-proj-"))
        assertTrue(secondInstance.revokeByTap())
        assertNull(AiGatewaySettings(context).get())
    }

    @Test fun voiceRequiresSeparateOptInAndCorruptedPairIsDistinguishable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AiGatewaySettings(context)
        settings.revokeByTap()
        assertTrue(settings.configureByTap(validToken, true))
        assertTrue(AiGatewaySettings(context).get()?.allowVoice == true)
        context.getSharedPreferences("omega_ai_gateway_v1", 0)
            .edit().putString("sealed", "AAAA").commit()
        val broken = AiGatewaySettings(context)
        assertTrue(broken.hasStoredPairing())
        assertNull(broken.get())
        assertTrue(broken.revokeByTap())
    }
}
