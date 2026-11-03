package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class NativeAudioSourceFailoverPolicyTest {
    @Test fun prefersVoiceCommunicationIfCaptureStarts() {
        val attempts = mutableListOf<NativeAudioSourceChoice>()
        val selected = NativeAudioSourceFailoverPolicy.open(true) { source ->
            attempts.add(source)
            "capture"
        }
        assertEquals(NativeAudioSourceChoice.VOICE_COMMUNICATION, selected?.first)
        assertEquals(listOf(NativeAudioSourceChoice.VOICE_COMMUNICATION), attempts)
    }

    @Test fun failedAecSourceFallsBackToMicWithoutGrantingBargeIn() {
        val attempts = mutableListOf<NativeAudioSourceChoice>()
        val selected = NativeAudioSourceFailoverPolicy.open(true) { source ->
            attempts.add(source)
            if (source == NativeAudioSourceChoice.VOICE_COMMUNICATION) null
            else "mic-recording"
        }
        assertEquals(NativeAudioSourceChoice.MICROPHONE, selected?.first)
        assertEquals("mic-recording", selected?.second)
        assertEquals(listOf(NativeAudioSourceChoice.VOICE_COMMUNICATION,
            NativeAudioSourceChoice.MICROPHONE), attempts)
    }

    @Test fun constructorFailureFallsBackButMicOnlyModeNeverRequestsAec() {
        val recovered = NativeAudioSourceFailoverPolicy.open(true) { source ->
            if (source == NativeAudioSourceChoice.VOICE_COMMUNICATION)
                throw IllegalStateException("unavailable capture source")
            "mic"
        }
        assertEquals(NativeAudioSourceChoice.MICROPHONE, recovered?.first)
        val attempts = mutableListOf<NativeAudioSourceChoice>()
        NativeAudioSourceFailoverPolicy.open(false) { source ->
            attempts.add(source)
            "mic"
        }
        assertEquals(listOf(NativeAudioSourceChoice.MICROPHONE), attempts)
    }

    @Test fun permissionRevocationFailsClosedWithNoRetry() {
        val attempts = mutableListOf<NativeAudioSourceChoice>()
        try {
            NativeAudioSourceFailoverPolicy.open(true) { source ->
                attempts.add(source)
                throw SecurityException("microphone revoked")
            }
            fail("permission revocation must propagate")
        } catch (_: SecurityException) {
            assertEquals(listOf(NativeAudioSourceChoice.VOICE_COMMUNICATION), attempts)
        }
    }

    @Test fun bothRoutesUnavailableReturnsFailure() {
        val result = NativeAudioSourceFailoverPolicy.open(true) { _: NativeAudioSourceChoice -> null }
        assertNull(result)
    }
}
