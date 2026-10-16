package com.cardenaspiero255.jarvis

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** UI smoke without any optional companion installed or privileged grant. */
@RunWith(AndroidJUnit4::class)
class CompanionCenterInstrumentedTest {
    private fun find(root: View, label: String): TextView? {
        if (root is TextView && root.text.toString() == label) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            find(root.getChildAt(i), label)?.let { return it }
        }
        return null
    }

    @Test fun centerCanOpenAndCancelAutomationWithoutPrivilegedApps() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val activity = instrument.startActivitySync(
            Intent(instrument.targetContext, CompanionCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as CompanionCenterActivity
        try {
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                val root = activity.window.decorView
                assertNotNull(find(root, "CONEXIONES JARVIS OS"))
                assertNotNull(find(root, "SHIZUKU"))
                assertNotNull(find(root, "DHIZUKU"))
                assertNotNull(find(root, "OWNDROID"))
                assertNotNull(find(root, "TERMUX"))
                val stop = find(root, "PARADA DE EMERGENCIA  /  CANCELAR RUTINA")
                assertNotNull(stop)
                assertTrue(stop!!.performClick())
                assertNotNull(find(root, "ELEGIR RUTINA SEGURA"))
            }
        } finally {
            instrument.runOnMainSync { activity.finish() }
            instrument.waitForIdleSync()
        }
    }
}
