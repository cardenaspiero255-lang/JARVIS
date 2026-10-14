package com.cardenaspiero255.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.cardenaspiero255.jarvis.core.HandsContext
import com.cardenaspiero255.jarvis.core.HandsDecision
import com.cardenaspiero255.jarvis.core.HandsOperation
import com.cardenaspiero255.jarvis.core.HandsPolicy

/**
 * Smallest usable Omega Hands service: user-triggered HOME only.
 *
 * It never reads accessibility content, records events, performs gestures,
 * sends text, or accepts background requests from external apps.
 * Android must explicitly enable this service; JARVIS must separately be
 * armed by the owner after each fresh install (persistent stop defaults true).
 */
class OmegaHandsService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No capture, event buffering, text analysis or telemetry by default.
    }

    override fun onInterrupt() {
        // A service interruption never permits an action.
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        attached = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (attached === this) attached = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (attached === this) attached = null
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "omega_hands"
        private const val STOPPED = "emergency_stopped"

        @Volatile private var attached: OmegaHandsService? = null

        fun isConnected(): Boolean = attached != null

        fun isStopped(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(STOPPED, true)

        /** Only visible, explicit UI controls in JARVIS call this method. */
        fun setEmergencyStop(context: Context, stopped: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(STOPPED, stopped).apply()
        }

        fun goHome(context: Context, explicitTap: Boolean): Boolean {
            val service = attached
            val decision = HandsPolicy().evaluate(
                HandsOperation.GO_HOME,
                HandsContext(
                    serviceConnected = service != null,
                    emergencyStopped = isStopped(context),
                    explicitTap = explicitTap,
                )
            )
            if (decision != HandsDecision.ALLOW_HOME || service == null) return false
            return try {
                service.performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }
    }
}
