package com.cardenaspiero255.jarvis

import android.content.Context
import java.security.KeyStore

/**
 * Best-effort migration from old cloud pairing to local-only JARVIS.
 * Deletes only the retired gateway's ciphertext and AndroidKeyStore alias.
 * Never reads the token, logs private data or affects private chat memory.
 */
object RetiredCloudCredentialCleaner {
    fun purge(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences("omega_ai_gateway_v1", Context.MODE_PRIVATE)
        val cleared = try { prefs.edit().clear().commit() } catch (_: Exception) { false }
        val keyRemoved = try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias("jarvis_omega_ai_gateway_token_v1"))
                store.deleteEntry("jarvis_omega_ai_gateway_token_v1")
            true
        } catch (_: Exception) { false }
        return cleared && keyRemoved
    }
}
