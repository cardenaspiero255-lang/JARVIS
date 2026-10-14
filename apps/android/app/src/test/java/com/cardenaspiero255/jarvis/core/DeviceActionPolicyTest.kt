package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceActionPolicyTest {
    private val policy = DeviceActionPolicy()

    @Test fun readOnlyStatusNeedsNoAndroidPermission() {
        assertTrue(policy.canExecute(DeviceOperation.READ_LOCAL_STATUS,
            explicitUserGesture = false))
    }

    @Test fun openingSettingsRequiresAnExplicitGesture() {
        assertFalse(policy.canExecute(DeviceOperation.OPEN_ACCESSIBILITY_SETTINGS,
            explicitUserGesture = false))
        assertTrue(policy.canExecute(DeviceOperation.OPEN_ACCESSIBILITY_SETTINGS,
            explicitUserGesture = true))
    }

    @Test fun dangerousAndUnimplementedOperationsAreDeniedEvenWithGesture() {
        for (operation in listOf(DeviceOperation.PRESS_SCREEN,
                                DeviceOperation.SEND_MESSAGE,
                                DeviceOperation.GITHUB_MERGE,
                                DeviceOperation.LAUNCH_OTHER_APP)) {
            assertFalse(policy.canExecute(operation, explicitUserGesture = true))
        }
    }
}
