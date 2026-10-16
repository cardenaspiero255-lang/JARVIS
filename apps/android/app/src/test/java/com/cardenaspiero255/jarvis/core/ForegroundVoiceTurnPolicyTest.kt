package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundVoiceTurnPolicyTest {
    @Test fun stopsContinuousListeningForPrivilegedRequests() {
        assertTrue(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.OPEN_GAMEHUB_ULTRA, false))
        assertTrue(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.OPEN_ACCESSIBILITY_SETTINGS, false))
        assertTrue(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.UNSUPPORTED, true))
    }

    @Test fun emergencyStopNeverRestartsTheMicrophone() {
        assertTrue(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.STOP_SESSION, false))
    }

    @Test fun safeReadOnlyRepliesCanContinue() {
        assertFalse(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.READ_STATUS, false))
        assertFalse(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.READ_DEVICE_HEALTH, false))
        assertFalse(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.READ_GAME_READINESS, false))
        assertFalse(ForegroundVoiceTurnPolicy.mustSuspend(VoiceAction.UNSUPPORTED, false))
    }
}
