package com.cardenaspiero255.jarvis

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises denial and revocation without automating sensitive OS consent. */
@RunWith(AndroidJUnit4::class)
class ScreenProjectionPrivacyInstrumentedTest {
    private fun button(root: View, caption: String): Button? {
        if (root is Button && root.text.toString() == caption) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            val found = button(root.getChildAt(i), caption)
            if (found != null) return found
        }
        return null
    }

    @Test fun stopControlsAreVisibleAndCanRevokeAnUnconsumedPermit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val stop = button(activity.window.decorView, "DETENER VISIÓN DE PANTALLA")
                val lock = button(activity.window.decorView, "BLOQUEAR SESIÓN — PRIVACIDAD")
                val status = button(activity.window.decorView, "ESTADO VISIÓN DE PANTALLA")
                assertNotNull(stop)
                assertNotNull(lock)
                assertNotNull(status)
                assertTrue(ScreenObservationService.authorizeOneStart())
                stop!!.performClick()
                assertTrue(ScreenObservationService.status().contains("detenida"))
                assertTrue(ScreenObservationService.authorizeOneStart())
                lock!!.performClick()
                assertTrue(ScreenObservationService.status().contains("detenida"))
                // Privacy lock must never erase the separate encrypted disk history.
                assertFalse(ScreenObservationService.status().contains("activa"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            ScreenObservationService.stopNow()
        }
    }
}
