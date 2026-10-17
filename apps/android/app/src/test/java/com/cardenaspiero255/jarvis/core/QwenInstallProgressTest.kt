package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class QwenInstallProgressTest {
    @Test fun downloadedBytesGenerateDeterministicProgress() {
        val halfway = QwenInstallProgress.fromBytes(LocalLlmPolicy.MODEL_BYTES / 2)
        assertEquals(50, halfway.percent)
        assertEquals(QwenInstallProgress.Phase.DOWNLOADING, halfway.phase)
        assertTrue(halfway.message.contains("245"))
        assertTrue(halfway.message.contains("491 MB"))
    }

    @Test fun finishingDownloadStillRequiresCryptographicVerification() {
        val complete = QwenInstallProgress.fromBytes(LocalLlmPolicy.MODEL_BYTES)
        assertEquals(100, complete.percent)
        assertEquals(QwenInstallProgress.Phase.VERIFYING, complete.phase)
        assertFalse(complete.message.contains("instalado"))
    }

    @Test fun malformedByteCountsCannotProduceSuccess() {
        val zero = QwenInstallProgress.fromBytes(-5)
        assertEquals(0, zero.percent)
        assertEquals(QwenInstallProgress.Phase.DOWNLOADING, zero.phase)
        val overflow = QwenInstallProgress.fromBytes(LocalLlmPolicy.MODEL_BYTES + 1000)
        assertEquals(100, overflow.percent)
        assertEquals(QwenInstallProgress.Phase.VERIFYING, overflow.phase)
    }

    @Test fun stagesAreVisibleBeforeFirstDownloadedChunk() {
        val starting = QwenInstallProgress.connecting()
        assertEquals(QwenInstallProgress.Phase.CONNECTING, starting.phase)
        assertTrue(starting.message.contains("conectando"))
    }

    @Test fun onlyExplicitVerificationMarksSuccessfulInstallation() {
        val verified = QwenInstallProgress.verified()
        assertEquals(QwenInstallProgress.Phase.VERIFIED, verified.phase)
        assertTrue(verified.message.contains("SHA-256"))
        assertTrue(verified.message.contains("instalado"))
    }
}
