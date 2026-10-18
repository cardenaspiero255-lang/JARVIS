package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineKwsModelGateTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun absentAssetsCannotEnableKwsAndMustKeepVoskFallback() {
        assertEquals(KwsReadiness.NO_VERIFIED_MODEL,
            OfflineKwsModelGate().verify(temp.root))
    }

    @Test fun wrongOrTruncatedModelCannotEnableNativeInference() {
        val root = temp.newFolder("fake")
        for (name in OfflineKwsModelGate.requiredAssets.keys) {
            File(root, name).writeText("not an onnx model")
        }
        assertEquals(KwsReadiness.INVALID_FINGERPRINT,
            OfflineKwsModelGate().verify(root))
    }

    @Test fun unsafeModelSymlinkIsRejected() {
        val root = temp.newFolder("links")
        val outside = temp.newFile("other.onnx")
        val asset = OfflineKwsModelGate.requiredAssets.keys.first()
        java.nio.file.Files.createSymbolicLink(File(root, asset).toPath(), outside.toPath())
        assertEquals(KwsReadiness.NO_VERIFIED_MODEL,
            OfflineKwsModelGate().verify(root))
    }

    @Test fun readinessCannotTurnModelIntoPermission() {
        val gate = KwsCandidateGate()
        assertEquals(KwsDecision.IGNORE,
            gate.evaluate(0L, "JARVIS", nativeDetected = false,
                modelVerified = true, sessionGranted = true))
        assertEquals(KwsDecision.IGNORE,
            gate.evaluate(0L, "JARVIS", nativeDetected = true,
                modelVerified = false, sessionGranted = true))
        assertEquals(KwsDecision.IGNORE,
            gate.evaluate(0L, "JARVIS", nativeDetected = true,
                modelVerified = true, sessionGranted = false))
        assertEquals(KwsDecision.CANDIDATE,
            gate.evaluate(1000L, "Jarvis", nativeDetected = true,
                modelVerified = true, sessionGranted = true))
        assertEquals(KwsDecision.IGNORE,
            gate.evaluate(1050L, "Jarvis", nativeDetected = true,
                modelVerified = true, sessionGranted = true))
        assertEquals(KwsDecision.CANDIDATE,
            gate.evaluate(2201L, "Jarvis", nativeDetected = true,
                modelVerified = true, sessionGranted = true))
    }

    @Test fun spoofedOrNonExactKeywordCannotEscalate() {
        val gate = KwsCandidateGate()
        for (word in listOf("jarvis para", "no jarvis", "jarvis!", "", "jarvisjarvis")) {
            assertEquals(KwsDecision.IGNORE, gate.evaluate(
                5000L, word, nativeDetected = true,
                modelVerified = true, sessionGranted = true))
        }
    }
}
