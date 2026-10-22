package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioExitPolicyTest {
    @Test fun preservesNegativeReadPermissionCauseAcrossBothListeningModes() {
        val error = NativeAudioReadFailurePolicy.classify(-3, permissionGranted = false)
        for (continuous in listOf(false, true)) {
            val fallback = if (continuous) "ASR_NATIVE_SESSION_WINDOW_ENDED"
                else "ASR_NATIVE_NO_MIC_SAMPLES"
            assertEquals("ASR_NATIVE_MIC_PERMISSION_DENIED_AT_READ",
                NativeAudioExitPolicy.resolveFailure(error, fallback))
            assertFalse(NativeAudioExitPolicy.mayDeliverRecognizedSpeech(error))
        }
    }

    @Test fun hardwareErrorsAreNotReplacedByMissingWakeOrTimeout() {
        val error = NativeAudioReadFailurePolicy.classify(-6, permissionGranted = true)
        assertEquals("ASR_NATIVE_AUDIO_READ_ERROR",
            NativeAudioExitPolicy.resolveFailure(error, "ASR_NATIVE_NO_WAKE"))
        assertFalse(NativeAudioExitPolicy.mayDeliverRecognizedSpeech(error))
    }

    @Test fun successAndNormalExpiryKeepNormalBehavior() {
        assertEquals("ASR_NATIVE_SESSION_WINDOW_ENDED",
            NativeAudioExitPolicy.resolveFailure(null, "ASR_NATIVE_SESSION_WINDOW_ENDED"))
        assertTrue(NativeAudioExitPolicy.mayDeliverRecognizedSpeech(null))
    }
}
