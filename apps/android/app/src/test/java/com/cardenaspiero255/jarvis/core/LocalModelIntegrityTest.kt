package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class LocalModelIntegrityTest {
    @Test fun verifiesExactBytesAndRejectsTampering() {
        val file = File.createTempFile("jarvis-model-", ".gguf")
        try {
            file.writeBytes("valid model bytes".toByteArray())
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertTrue(LocalModelIntegrity.verify(file, digest))
            file.appendText("tampered")
            assertFalse(LocalModelIntegrity.verify(file, digest))
        } finally {
            file.delete()
        }
    }

    @Test fun failsClosedOnMissingFilesAndMalformedHashes() {
        val missing = File(System.getProperty("java.io.tmpdir"), "jarvis-missing-model-verify.gguf")
        assertFalse(LocalModelIntegrity.verify(missing, "0".repeat(64)))
        val file = File.createTempFile("jarvis-model-", ".gguf")
        try {
            file.writeText("content")
            assertFalse(LocalModelIntegrity.verify(file, "not-a-hash"))
            assertFalse(LocalModelIntegrity.verify(file, ""))
        } finally {
            file.delete()
        }
    }
}
