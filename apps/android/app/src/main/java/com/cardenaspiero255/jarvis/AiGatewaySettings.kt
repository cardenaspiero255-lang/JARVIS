package com.cardenaspiero255.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.cardenaspiero255.jarvis.core.AiGatewayPolicy
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Only the user can pair the encrypted backend credentials via a visible dialog.
 * The OpenAI API key belongs to the backend, never here. Disabled until paired.
 */
class AiGatewaySettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("omega_ai_gateway_v1", Context.MODE_PRIVATE)

    data class Pairing(
        val endpoint: String,
        val token: String,
        val allowVoice: Boolean
    )

    /** Server URL is built in. Only a separately issued secret is paired once. */
    fun configureByTap(token: String, allowVoice: Boolean): Boolean =
        configureByTap(AiGatewayPolicy.DEFAULT_ENDPOINT, token, allowVoice)

    /** Also validates any old saved pairing created before URL was bundled. */
    fun configureByTap(endpoint: String, token: String, allowVoice: Boolean): Boolean {
        if (!AiGatewayPolicy.officialEndpointAllowed(endpoint) ||
            !AiGatewayPolicy.tokenAllowed(token)) return false
        val data = JSONObject().apply {
            put("endpoint", endpoint)
            put("token", token)
            put("voice", allowVoice)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(data)
        val persisted = prefs.edit()
            .putString("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP))
            .commit()
        return persisted && get() != null
    }

    /** No credentials are returned; used only to distinguish missing/corrupt pairs. */
    fun hasStoredPairing(): Boolean =
        prefs.contains("nonce") || prefs.contains("sealed")

    fun get(): Pairing? {
        val nonce = prefs.getString("nonce", null) ?: return null
        val encrypted = prefs.getString("sealed", null) ?: return null
        if (nonce.length !in 12..80 || encrypted.length !in 40..1800) return null
        return try {
            val iv = Base64.decode(nonce, Base64.NO_WRAP)
            if (iv.size != 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val payload = JSONObject(
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8)
            )
            val endpoint = payload.optString("endpoint")
            val token = payload.optString("token")
            if (!AiGatewayPolicy.officialEndpointAllowed(endpoint) ||
                !AiGatewayPolicy.tokenAllowed(token)) return null
            Pairing(endpoint,token,payload.optBoolean("voice", false))
        } catch (_: Exception) { null }
    }

    fun revokeByTap(): Boolean {
        val wiped = prefs.edit().clear().commit()
        if (!wiped) return false
        return try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(KEY_ALIAS)
            true
        } catch (_: Exception) { false }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS,null)
        if (existing is SecretKey) return existing
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "jarvis_omega_ai_gateway_token_v1"
    }
}
