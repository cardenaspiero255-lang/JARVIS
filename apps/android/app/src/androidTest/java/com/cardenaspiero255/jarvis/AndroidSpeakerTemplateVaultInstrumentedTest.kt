package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidSpeakerTemplateVaultInstrumentedTest {
    @Test fun encryptionModelBindingAndRevocationWithoutRecordingAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = AndroidSpeakerTemplateVault(context)
        val model1 = "a".repeat(64)
        val model2 = "b".repeat(64)
        val vector = FloatArray(64) { if (it == 0) 1f else 0f }
        vault.revoke()
        assertNull(vault.load(model1))
        vault.save(model1, vector)
        assertArrayEquals(vector, vault.load(model1)!!, 0.00001f)
        assertNull(vault.load(model2))
        val persisted = File(context.noBackupFilesDir, "jarvis-owner-embedding-v1.bin")
        assertTrue(persisted.isFile)
        // The 4-byte IEEE-754 float encoding must not be present as plaintext.
        val clearFloat = byteArrayOf(0x3f, 0x80.toByte(), 0x00, 0x00)
        val encrypted = persisted.readBytes()
        assertFalse(encrypted.asList().windowed(4).any { it == clearFloat.asList() })
        vault.revoke()
        assertNull(vault.load(model1))
        assertFalse(persisted.exists())
    }
}
