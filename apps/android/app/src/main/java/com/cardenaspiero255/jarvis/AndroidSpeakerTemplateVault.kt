package com.cardenaspiero255.jarvis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Private optional speaker-embedding vault.
 * No raw PCM, ASR transcripts, server calls or biometric decisions are stored.
 * Only a model-bound normalized float template may be persisted.
 */
class AndroidSpeakerTemplateVault(private val context: Context) {
    private val file: File get() = File(context.noBackupFilesDir, "jarvis-owner-embedding-v1.bin")
    private val alias = "jarvis-owner-embedding-aes-v1"

    @Synchronized
    fun save(modelSha256: String, template: FloatArray) {
        require(VALID_SHA.matches(modelSha256)) { "Unpinned speaker embedding model" }
        require(validTemplate(template)) { "Invalid speaker template" }
        val payload = ByteBuffer.allocate(4 + template.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(template.size).apply { template.forEach { putFloat(it) } }.array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(modelSha256.toByteArray(Charsets.US_ASCII))
        val ciphertext = cipher.doFinal(payload)
        val iv = cipher.iv
        require(iv.size == 12) { "Unsupported GCM IV" }
        val serialized = ByteBuffer.allocate(4 + 12 + ciphertext.size)
            .put(byteArrayOf(0x4a, 0x56, 0x4d, 0x31))
            .put(iv)
            .put(ciphertext)
            .array()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(serialized)
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        } finally {
            payload.fill(0)
        }
    }

    @Synchronized
    fun load(modelSha256: String): FloatArray? {
        if (!VALID_SHA.matches(modelSha256) || !file.isFile ||
            file.length() !in 33L..MAX_SERIALIZED_BYTES) return null
        return try {
            val bytes = AtomicFile(file).openRead().use { it.readBytes() }
            if (bytes.size < 33 || !bytes.sliceArray(0..3)
                    .contentEquals(byteArrayOf(0x4a, 0x56, 0x4d, 0x31))) return null
            val iv = bytes.copyOfRange(4, 16)
            val ciphertext = bytes.copyOfRange(16, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, readKey() ?: return null,
                GCMParameterSpec(128, iv))
            cipher.updateAAD(modelSha256.toByteArray(Charsets.US_ASCII))
            val plaintext = cipher.doFinal(ciphertext)
            try {
                if (plaintext.size < 4) return null
                val bb = ByteBuffer.wrap(plaintext).order(ByteOrder.BIG_ENDIAN)
                val n = bb.int
                if (n !in 64..1024 || plaintext.size != 4 + n * 4) return null
                val result = FloatArray(n) { bb.float }
                if (!validTemplate(result)) return null
                result
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            // Wrong model/version, invalid ciphertext, rotated key or corruption.
            null
        }
    }

    @Synchronized
    fun revoke() {
        AtomicFile(file).delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun validTemplate(values: FloatArray): Boolean {
        if (values.size !in 64..1024 || values.any { !it.isFinite() }) return false
        val sumSquares = values.sumOf { it.toDouble() * it.toDouble() }
        return sumSquares.isFinite() && sumSquares in 0.95..1.05
    }

    private fun readKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        readKey()?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private companion object {
        val VALID_SHA = Regex("^[0-9a-f]{64}$")
        const val MAX_SERIALIZED_BYTES = 16384L
    }
}
