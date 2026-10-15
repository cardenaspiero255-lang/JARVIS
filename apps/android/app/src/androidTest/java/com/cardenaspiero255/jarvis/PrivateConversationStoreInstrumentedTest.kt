package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs ON AN ANDROID DEVICE/EMULATOR, not as JVM-only tests.
 * Verifies actual AndroidKeyStore operation and ciphertext privacy.
 */
@RunWith(AndroidJUnit4::class)
class PrivateConversationStoreInstrumentedTest {
    @Test fun optInEncryptsAndEraseRevokesAllAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PrivateConversationStore(context)
        store.eraseByTap()
        assertFalse(store.isEnabled())
        assertFalse(store.save("un mensaje privado"))
        assertNull(store.load())

        assertTrue(store.enableByTap())
        assertTrue(store.save("un mensaje privado"))
        assertEquals("un mensaje privado", store.load())

        val prefs = context.getSharedPreferences("omega_private_conversation_v1", 0)
        assertNotEquals("un mensaje privado", prefs.getString("encrypted", null))
        assertFalse((prefs.getString("encrypted", "") ?: "").contains("mensaje privado"))

        store.stop()
        assertNull(store.load())
        assertTrue(PrivateConversationStore(context).enableByTap())
        val reopened = PrivateConversationStore(context)
        assertNull(reopened.load()) // New process/session begins without consent.
        reopened.enableByTap()
        assertEquals("un mensaje privado", reopened.load())
        assertTrue(reopened.eraseByTap())
        reopened.enableByTap()
        assertNull(reopened.load())
    }

    @Test fun tamperedCiphertextCannotBeRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PrivateConversationStore(context)
        store.eraseByTap()
        store.enableByTap()
        assertTrue(store.save("integridad GCM"))
        context.getSharedPreferences("omega_private_conversation_v1", 0)
            .edit().putString("encrypted", "AAAA").commit()
        var rejected = false
        try { store.load() } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        store.eraseByTap()
    }
}
