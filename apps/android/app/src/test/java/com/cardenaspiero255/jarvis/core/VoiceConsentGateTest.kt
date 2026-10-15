package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConsentGateTest {
    @Test fun voiceActionCannotExecuteBeforeConfirmation() {
        val gate = VoiceConsentGate()
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100))
        assertTrue(gate.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100))
        assertTrue(gate.hasPending(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 101))
    }

    @Test fun confirmationIsOneUseAndTiedToTheRequestedAction() {
        val gate = VoiceConsentGate()
        gate.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100)
        assertTrue(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 102))
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 103))
    }

    @Test fun requestExpiresAtThirtySecondsAndClearsItself() {
        val gate = VoiceConsentGate()
        gate.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100)
        assertTrue(gate.hasPending(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 30099))
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 30100))
        assertFalse(gate.hasPending(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 30101))
    }

    @Test fun losingForegroundRevokesVoiceRequest() {
        val gate = VoiceConsentGate()
        gate.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100)
        gate.cancel()
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 101))
    }

    @Test fun timeMovingBackwardsAndInvalidRequestsFailClosed() {
        val gate = VoiceConsentGate()
        assertFalse(gate.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, -1))
        assertFalse(gate.hasPending(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 0))
        assertTrue(gate.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100))
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 99))
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 101))
    }

    @Test fun voiceConsentIsNotPersistedAcrossProcesses() {
        val first = VoiceConsentGate()
        first.request(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 100)
        val restarted = VoiceConsentGate()
        assertFalse(restarted.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 101))
    }
    @Test fun gameHubVoiceRequestNeedsItsOwnButtonWithinThirtySeconds() {
        val gate = VoiceConsentGate()
        assertFalse(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 100))
        assertTrue(gate.request(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 100))
        assertTrue(gate.hasPending(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 29999))
        assertTrue(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 29999))
        assertFalse(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 30000))
    }

    @Test fun crossActionConfirmationRevokesTheRequest() {
        val gate = VoiceConsentGate()
        gate.request(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 100)
        assertFalse(gate.consume(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, 101))
        assertFalse(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 102))
    }

    @Test fun gameHubRequestExpiresWhenBackgroundedOrClockMovesBack() {
        val gate = VoiceConsentGate()
        gate.request(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 100)
        assertFalse(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 99))
        gate.request(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 100)
        gate.cancel()
        assertFalse(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 101))
        gate.request(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 100)
        assertFalse(gate.consume(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, 30100))
    }

}
