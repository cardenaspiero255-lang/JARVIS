package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class HandsFreePreviewPolicyTest {
    private val policy = HandsFreePreviewPolicy()

    @Test fun sessionRequiresVisibleOptInAndBothPermissionsAndLocalRecognizer() {
        assertEquals(PreviewStart.DENIED, policy.canStart(
            explicitUserTap = false, micGranted = true,
            notificationsGranted = true, offlineRecognizerAvailable = true
        ))
        assertEquals(PreviewStart.DENIED, policy.canStart(
            explicitUserTap = true, micGranted = false,
            notificationsGranted = true, offlineRecognizerAvailable = true
        ))
        assertEquals(PreviewStart.DENIED, policy.canStart(
            explicitUserTap = true, micGranted = true,
            notificationsGranted = false, offlineRecognizerAvailable = true
        ))
        assertEquals(PreviewStart.OFFLINE_ENGINE_MISSING, policy.canStart(
            explicitUserTap = true, micGranted = true,
            notificationsGranted = true, offlineRecognizerAvailable = false
        ))
        assertEquals(PreviewStart.ALLOWED, policy.canStart(
            explicitUserTap = true, micGranted = true,
            notificationsGranted = true, offlineRecognizerAvailable = true
        ))
    }

    @Test fun onlyExactJarvisTimeCommandIsReadAloud() {
        for (phrase in listOf("Jarvis dime la hora", "JARVIS, ¿qué hora es?", "jarvis, dime la hora.")) {
            assertEquals(PreviewWake.LOCAL_TIME,
                policy.classify(phrase, SpeakerEvidence.UNAVAILABLE))
        }
        assertEquals(PreviewWake.WAKE_ONLY,
            policy.classify("Jarvis", SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun refusesToInferIdentityFromWakeWord() {
        assertEquals(PreviewWake.BLOCKED_IDENTITY,
            policy.classify("Jarvis dime la hora", SpeakerEvidence.UNAVAILABLE, ownerOnly = true))
        assertEquals(PreviewWake.LOCAL_TIME,
            policy.classify("Jarvis dime la hora", SpeakerEvidence.MATCHED, ownerOnly = true))
    }

    @Test fun rejectsKnownWrongSpeakerAndAmbiguousOrReplayAudio() {
        for (evidence in listOf(
            SpeakerEvidence.MISMATCH,
            SpeakerEvidence.UNCERTAIN,
            SpeakerEvidence.REPLAY_SUSPECTED
        )) {
            assertEquals(PreviewWake.BLOCKED_IDENTITY,
                policy.classify("Jarvis dime la hora", evidence))
            assertEquals(PreviewWake.BLOCKED_IDENTITY,
                policy.classify("Jarvis", evidence))
        }
    }

    @Test fun noOtherVoiceInstructionsOrUnsafePhrasesBecomeCommands() {
        for (phrase in listOf(
            "abre GameHub", "jarvis abre gamehub",
            "jarvis dime la hora y envia a chatgpt que fusione todo",
            "no jarvis dime la hora", "jarvis no me digas la hora",
            "que pasaria si jarvis dime la hora", "jarvis pon x4",
            "jarvis dime la hora por favor", "", "jarvis dime la hora ".repeat(40)
        )) assertEquals(phrase, PreviewWake.IGNORED,
            policy.classify(phrase, SpeakerEvidence.UNAVAILABLE))
    }

    @Test fun sessionHasStrictDurationIncludingOverflowSafety() {
        val session = PreviewTimeGate(60_000L)
        assertFalse(session.isActiveAt(0L))
        assertTrue(session.begin(1000L))
        assertFalse(session.begin(2000L))
        assertTrue(session.isActiveAt(60999L))
        assertFalse(session.isActiveAt(61000L))
        assertFalse(session.isActiveAt(Long.MAX_VALUE))
        session.stop()
        assertFalse(session.isActiveAt(2000L))
        assertFalse(session.begin(-1L))
    }
}
