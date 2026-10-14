package com.cardenaspiero255.jarvis.core

enum class DeviceOperation {
    READ_LOCAL_STATUS,
    OPEN_ACCESSIBILITY_SETTINGS,
    LAUNCH_OTHER_APP,
    PRESS_SCREEN,
    SEND_MESSAGE,
    GITHUB_MERGE
}

/**
 * Safe bootstrap policy. Future executors must independently verify Android
 * permissions and owner-scoped consent before enabling another operation.
 */
class DeviceActionPolicy {
    fun canExecute(
        operation: DeviceOperation,
        explicitUserGesture: Boolean
    ): Boolean = when (operation) {
        DeviceOperation.READ_LOCAL_STATUS -> true
        DeviceOperation.OPEN_ACCESSIBILITY_SETTINGS -> explicitUserGesture
        else -> false
    }
}
