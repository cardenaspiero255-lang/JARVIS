package com.cardenaspiero255.jarvis

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import com.cardenaspiero255.jarvis.core.TermuxResultState

/** Only tests result validation; it does not launch Termux or execute code. */
@RunWith(AndroidJUnit4::class)
class TermuxCallbackInstrumentedTest {
    @Test fun rejectsMalformedDataAndAcceptsMatchingOneShotResponse() {
        val now = SystemClock.elapsedRealtime()
        val first = TermuxResultSession.tracker.begin(now)
        val receiver = TermuxResultReceiver()
        val invalid = Intent(TermuxResultReceiver.ACTION_RESULT)
            .putExtra(TermuxResultReceiver.EXTRA_TOKEN, first)
            .putExtra("result", Bundle().apply { putString("stdout", "uid=1000") })
        receiver.onReceive(androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext, invalid)
        assertEquals(TermuxResultState.FAILED,
            TermuxResultSession.tracker.status(SystemClock.elapsedRealtime()))
        val second = TermuxResultSession.tracker.begin(SystemClock.elapsedRealtime())
        val valid = Intent(TermuxResultReceiver.ACTION_RESULT)
            .putExtra(TermuxResultReceiver.EXTRA_TOKEN, second)
            .putExtra("result", Bundle().apply {
                putInt("exitCode", 0)
                putInt("err", -1)
                putString("stdout", "uid=1000(u0_a0) gid=1000(u0_a0)")
            })
        receiver.onReceive(androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext, valid)
        assertEquals(TermuxResultState.VERIFIED,
            TermuxResultSession.tracker.status(SystemClock.elapsedRealtime()))
    }
}
