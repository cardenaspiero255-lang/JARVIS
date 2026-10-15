package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNotificationGateTest {
    @Test fun android13AndAboveRequiresExplicitNotificationGrant() {
        assertFalse(ScreenNotificationGate.canBegin(33, notificationsGranted = false))
        assertFalse(ScreenNotificationGate.canBegin(34, notificationsGranted = false))
        assertFalse(ScreenNotificationGate.canBegin(36, notificationsGranted = false))
        assertTrue(ScreenNotificationGate.canBegin(34, notificationsGranted = true))
    }

    @Test fun olderAndroidDoesNotRequireRuntimeNotificationGrant() {
        assertTrue(ScreenNotificationGate.canBegin(26, notificationsGranted = false))
        assertTrue(ScreenNotificationGate.canBegin(32, notificationsGranted = false))
    }
}
