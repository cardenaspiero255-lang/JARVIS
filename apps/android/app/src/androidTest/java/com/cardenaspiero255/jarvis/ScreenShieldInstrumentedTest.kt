package com.cardenaspiero255.jarvis

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Execute with :app:connectedDebugAndroidTest on Android device/emulator.
 *
 * CI currently only compiles this test APK. Asserting the FLAG_SECURE state
 * does NOT verify pixel-level protection in recent-app screenshots on OEMs.
 */
@RunWith(AndroidJUnit4::class)
class ScreenShieldInstrumentedTest {
    private fun secureFlag(activity: MainActivity): Boolean =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun findButton(root: View, label: String): Button? {
        if (root is Button && root.text.toString() == label) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findButton(root.getChildAt(index), label)
                if (found != null) return found
            }
        }
        return null
    }

    private fun withJarvis(test: (MainActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            test(activity)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun startupKeepsAndroidScreenshotShieldEnabled() = withJarvis { activity ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            assertTrue("MainActivity must be secure on first display", secureFlag(activity))
        }
    }

    @Test fun userCanTemporarilyAllowScreenshotsAndReprotectImmediately() = withJarvis { activity ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val root = activity.window.decorView
            val debug = findButton(root, "PERMITIR CAPTURAS 60 S — DEPURACIÓN")
            val protect = findButton(root, "PROTEGER PANTALLA AHORA")
            assertNotNull("Visible debug consent button", debug)
            assertNotNull("Visible revoke button", protect)

            assertTrue(secureFlag(activity))
            debug!!.performClick()
            assertFalse("A visible user tap should allow debug captures", secureFlag(activity))
            protect!!.performClick()
            assertTrue("Protect now must reenable FLAG_SECURE", secureFlag(activity))
        }
    }

    @Test fun privacyLockRevokesActiveDebugCapturePermission() = withJarvis { activity ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val root = activity.window.decorView
            val debug = findButton(root, "PERMITIR CAPTURAS 60 S — DEPURACIÓN")
            val lock = findButton(root, "BLOQUEAR SESIÓN — PRIVACIDAD")
            assertNotNull(debug)
            assertNotNull(lock)
            debug!!.performClick()
            assertFalse(secureFlag(activity))
            lock!!.performClick()
            assertTrue("Privacy lock must restore FLAG_SECURE", secureFlag(activity))
        }
    }
}
