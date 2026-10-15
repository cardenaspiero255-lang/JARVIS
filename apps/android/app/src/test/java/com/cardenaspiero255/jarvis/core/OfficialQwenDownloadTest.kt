package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class OfficialQwenDownloadTest {
    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test fun verifiedStreamBecomesPrivateModelWithoutDuplicateCopy() {
        val dir = createTempDirectory("jarvis-direct-qwen-").toFile()
        try {
            val bytes = "GGUF fixture content".toByteArray()
            val target = File(dir, "verified.gguf")
            val reports = mutableListOf<Long>()
            val ok = OfficialQwenDownload.install(
                ByteArrayInputStream(bytes), target, bytes.size.toLong(), sha256(bytes),
                isCancelled = { false }, onBytes = { reports.add(it) }
            )
            assertTrue(ok)
            assertArrayEquals(bytes, target.readBytes())
            assertEquals(bytes.size.toLong(), reports.last())
            assertEquals(1, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptedOrTruncatedDownloadsNeverReplaceExistingVerifiedModel() {
        val dir = createTempDirectory("jarvis-direct-qwen-").toFile()
        try {
            val bytes = "expected".toByteArray()
            val target = File(dir, "verified.gguf").apply { writeText("already installed") }
            assertFalse(OfficialQwenDownload.install(
                ByteArrayInputStream("corrupted".toByteArray()), target,
                9L, sha256(bytes), { false }, {}
            ))
            assertFalse(OfficialQwenDownload.install(
                ByteArrayInputStream(bytes), target, bytes.size.toLong() + 1L,
                sha256(bytes), { false }, {}
            ))
            assertEquals("already installed", target.readText())
            assertEquals(1, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationAndOversizeLeaveNoPartialFile() {
        val dir = createTempDirectory("jarvis-direct-qwen-").toFile()
        try {
            val target = File(dir, "verified.gguf")
            val bytes = ByteArray(120_000) { 7 }
            assertFalse(OfficialQwenDownload.install(
                ByteArrayInputStream(bytes), target, bytes.size.toLong(),
                sha256(bytes), { true }, {}
            ))
            assertFalse(OfficialQwenDownload.install(
                ByteArrayInputStream(bytes), target, 100L,
                sha256(bytes), { false }, {}
            ))
            assertFalse(target.exists())
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun pinnedOfficialSourceAndWeightsHaveExactSizeAndHash() {
        assertEquals(491400032L, LocalLlmPolicy.MODEL_BYTES)
        assertEquals(64, LocalLlmPolicy.SHA256.length)
        assertTrue(LocalLlmPolicy.MODEL_DOWNLOAD_URL.startsWith(
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/"
        ))
        assertTrue(LocalLlmPolicy.MODEL_DOWNLOAD_URL.endsWith(
            "/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ))
    }
}
