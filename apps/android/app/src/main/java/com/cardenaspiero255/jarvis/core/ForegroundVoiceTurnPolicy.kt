package com.cardenaspiero255.jarvis.core

/**
 * Fail closed for privileged or automation-originated voice turns.
 * A transcript can request a confirmation but cannot authorize an action.
 */
object ForegroundVoiceTurnPolicy {
    fun mustSuspend(action: VoiceAction, automationPreview: Boolean): Boolean =
        automationPreview || when (action) {
            VoiceAction.OPEN_ACCESSIBILITY_SETTINGS,
            VoiceAction.OPEN_GAMEHUB_ULTRA,
            VoiceAction.STOP_SESSION -> true
            else -> false
        }
}
