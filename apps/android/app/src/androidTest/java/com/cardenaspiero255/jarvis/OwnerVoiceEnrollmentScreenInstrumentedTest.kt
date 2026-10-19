package com.cardenaspiero255.jarvis

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No audio capture occurs in this test; confirms explicit opt-in UX on Android. */
@RunWith(AndroidJUnit4::class)
class OwnerVoiceEnrollmentScreenInstrumentedTest {
    @Test fun recordIsDisabledUntilConsentAndExitDoesNotRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, OwnerVoiceEnrollmentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as OwnerVoiceEnrollmentActivity
        try {
            instrumentation.waitForIdleSync()
            val views = mutableListOf<View>()
            fun visit(view: View) {
                views.add(view)
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
            val consent = views.filterIsInstance<CheckBox>().single()
            val record = views.filterIsInstance<Button>().first {
                it.text.toString().startsWith("GRABAR MUESTRA")
            }
            assertFalse(consent.isChecked)
            assertFalse(record.isEnabled)
            activity.runOnUiThread { consent.isChecked = true }
            instrumentation.waitForIdleSync()
            assertTrue(record.isEnabled)
            activity.runOnUiThread { consent.isChecked = false }
            instrumentation.waitForIdleSync()
            assertFalse(record.isEnabled)
        } finally {
            activity.runOnUiThread { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
