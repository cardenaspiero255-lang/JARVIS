package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class VerifiedModelSnapshotTest {
    @Test fun copiesAndVerifiesSnapshotBeforeUse() {
        val dir = createTempDir(prefix = "jarvis-model-test-")
        try {
            val original = File(dir, "external.gguf").apply { writeText("GGUF model fixture") }
            val hash = MessageDigest.getInstance("SHA-256").digest(original.readBytes())
                .joinToString("") { "%02x".format(it) }
            val snapshot = VerifiedModelSnapshot.create(original, File(dir, "private"), hash)
            assertNotNull(snapshot)
            original.writeText("tampered after snapshot")
            assertEquals("GGUF model fixture", snapshot!!.readText())
            assertTrue(LocalModelIntegrity.verify(snapshot, hash))
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesBadDigestWithoutLeavingModel() {
        val dir = createTempDir(prefix = "jarvis-model-test-")
        try {
            val original = File(dir, "external.gguf").apply { writeText("invalid") }
            assertNull(VerifiedModelSnapshot.create(original, File(dir, "private"), "0".repeat(64)))
            assertTrue(File(dir, "private").listFiles()?.isEmpty() != false)
        } finally { dir.deleteRecursively() }
    }
}
