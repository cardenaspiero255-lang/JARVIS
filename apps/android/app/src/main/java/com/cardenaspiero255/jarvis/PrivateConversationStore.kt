package com.cardenaspiero255.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.cardenaspiero255.jarvis.core.ConversationMemoryPolicy
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local opt-in encrypted history. Never accepts data from OCR or remote apps.
 *
 * AndroidKeyStore AES/GCM keys are non-exportable. Hardware backing depends
 * on the device. No cloud sync, automatic backup, or plaintext storage.
 */
class PrivateConversationStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("omega_private_conversation_v1", Context.MODE_PRIVATE)
    private val policy = ConversationMemoryPolicy()

    fun enableByTap(): Boolean = policy.enable(explicitUserTap = true)

    fun isEnabled(): Boolean = policy.canReadOrWrite()

    fun stop() { policy.stop() }

    fun save(plainHistory: String): Boolean {
        if (!policy.canReadOrWrite()) return false
        val bytes = plainHistory.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_PLAINTEXT_BYTES) throw IllegalArgumentException("history too large")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(bytes)
        // Both values are needed together: use one transactional preferences commit.
        return prefs.edit()
            .putString("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
    }

    fun load(): String? {
        if (!policy.canReadOrWrite()) return null
        val nonce = prefs.getString("nonce", null)
        val encrypted = prefs.getString("encrypted", null)
        if (nonce == null && encrypted == null) return null
        if (nonce == null || encrypted == null ||
            nonce.length > 80 || encrypted.length > MAX_PLAINTEXT_BYTES * 2
        ) throw IllegalStateException("incomplete encrypted history")
        return try {
            val iv = Base64.decode(nonce, Base64.NO_WRAP)
            val ciphertext = Base64.decode(encrypted, Base64.NO_WRAP)
            if (iv.size != 12) throw IllegalStateException("invalid encrypted history")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("encrypted conversation unavailable", e)
        }
    }

    /** Erase data and revoke this process's read/write consent. */
    fun eraseByTap(): Boolean {
        policy.stop()
        val erased = prefs.edit().clear().commit()
        if (!erased) return false
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.deleteEntry(KEY_ALIAS)
        return true
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "jarvis_omega_private_chat_v1"
        private const val MAX_PLAINTEXT_BYTES = 8192
    }
}
