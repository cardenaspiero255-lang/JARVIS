package com.cardenaspiero255.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.cardenaspiero255.jarvis.core.TermuxResultTracker

/** In-memory only: neither command output nor sensitive device identifiers are saved. */
internal object TermuxResultSession {
    val tracker = TermuxResultTracker()
}

/**
 * Only the explicit, one-shot PendingIntent granted to Termux can reach this
 * non-exported receiver from another package. Missing/malformed output fails closed.
 */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RESULT) return
        val token = intent.getIntExtra(EXTRA_TOKEN, -1)
        if (token < 0) return
        val result: Bundle = try {
            intent.getBundleExtra("result") ?: run {
                TermuxResultSession.tracker.cancel(token)
                return
            }
        } catch (_: RuntimeException) {
            TermuxResultSession.tracker.cancel(token)
            return
        }
        if (!result.containsKey("exitCode") || !result.containsKey("err")) {
            TermuxResultSession.tracker.cancel(token)
            return
        }
        try {
            // Process only a bounded read-only /usr/bin/id proof, never transcript data.
            val exitCode = result.getInt("exitCode", Int.MIN_VALUE)
            val errCode = result.getInt("err", Int.MIN_VALUE)
            val stdout = result.getString("stdout").orEmpty()
            TermuxResultSession.tracker.receive(
                token, exitCode, errCode, stdout, SystemClock.elapsedRealtime()
            )
        } catch (_: RuntimeException) {
            TermuxResultSession.tracker.cancel(token)
        }
    }

    companion object {
        const val ACTION_RESULT = "com.cardenaspiero255.jarvis.TERMUX_DIAGNOSTIC_RESULT"
        const val EXTRA_TOKEN = "com.cardenaspiero255.jarvis.TERMUX_TOKEN"
    }
}
