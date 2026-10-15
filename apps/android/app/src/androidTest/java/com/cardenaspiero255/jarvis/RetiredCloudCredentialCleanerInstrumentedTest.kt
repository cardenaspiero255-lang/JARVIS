package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetiredCloudCredentialCleanerInstrumentedTest {
    @Test fun removesObsoletePairWithoutErasingPrivateConversation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = context.getSharedPreferences("omega_ai_gateway_v1", 0)
        val privatePrefs = context.getSharedPreferences("omega_private_conversation_v1", 0)
        val previous = privatePrefs.getString("legacy_test_marker",null)
        try {
            assertTrue(old.edit().putString("sealed", "never-a-real-secret")
                .putString("nonce","discard").commit())
            assertTrue(privatePrefs.edit().putString("legacy_test_marker","preserved").commit())
            assertTrue(RetiredCloudCredentialCleaner.purge(context))
            assertFalse(old.contains("sealed"))
            assertFalse(old.contains("nonce"))
            assertTrue(privatePrefs.getString("legacy_test_marker",null)=="preserved")
            assertTrue(RetiredCloudCredentialCleaner.purge(context))
        } finally {
            val edit=privatePrefs.edit()
            if(previous==null) edit.remove("legacy_test_marker")
            else edit.putString("legacy_test_marker",previous)
            edit.commit()
        }
    }
}
