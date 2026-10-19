package com.cardenaspiero255.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REAL Android14 emulator test: start the native foreground mic while Activity
 * is visible, turn the display off with KEYCODE_SLEEP and require fresh PCM
 * progress while PowerManager.isInteractive == false.
 *
 * Hardware-only Funtouch endurance remains outstanding; emulator passing does
 * not prove the user's Vivo stays alive for 30 minutes, or owner authentication.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenAudioContinuityInstrumentedTest {
    private fun within(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (predicate()) return true
            SystemClock.sleep(500L)
        }
        return predicate()
    }

    @Test fun nativeForegroundMicrophoneDeliversPcmAfterEmulatorScreenSleeps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val prefs = context.getSharedPreferences("jarvis_handsfree_diag_v1",
            Context.MODE_PRIVATE)
        val power = context.getSystemService(PowerManager::class.java)
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            context.startForegroundService(Intent(context, HandsFreePreviewService::class.java)
                .setAction(HandsFreePreviewService.ACTION_START_LONG_NATIVE)
                .putExtra(HandsFreePreviewService.EXTRA_TAPPED, true))

            assertTrue("ASR native foreground mic was not ready; last status: " +
                prefs.getString("status", ""),
                within(90_000L) {
                    val s = prefs.getString("status", "").orEmpty()
                    s.startsWith("EXTENDED_LISTENING: micrófono local activo") ||
                        s.startsWith("LOCKSCREEN_PCM_OBSERVED")
                })
            automation.executeShellCommand("input keyevent 223").close() // KEYCODE_SLEEP
            assertTrue("Emulator refused screen sleep",
                within(10_000L) { !power.isInteractive })
            assertTrue("No real PCM progress after screen was turned off; last state: " +
                prefs.getString("lockscreen_result", "none") + "; status: " +
                prefs.getString("status", ""),
                within(30_000L) {
                    !power.isInteractive &&
                        prefs.getString("lockscreen_result", "") ==
                        "SCREEN_OFF_AUDIO_OBSERVED" &&
                        prefs.getLong("lockscreen_off_samples", 0L) >= 16_000L
                })
            assertTrue("Native FGS did not survive sleep", HandsFreePreviewService.isRunning)
        } finally {
            // Restore emulator for later independent tests even if assertions fail.
            automation.executeShellCommand("input keyevent 224").close() // KEYCODE_WAKEUP
            within(10_000L) { power.isInteractive }
            try {
                context.startService(Intent(context, HandsFreePreviewService::class.java)
                    .setAction(HandsFreePreviewService.ACTION_STOP))
            } catch (_: RuntimeException) {
                // Service may have already stopped after an Android restriction.
            }
            activity.runOnUiThread { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
