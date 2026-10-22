package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class VivoScreenOffReceiptPolicyTest {
    @Test fun realSamplesArePreservedRegardlessOfStopNotificationOrder() {
        assertEquals(44_000L, VivoScreenOffReceiptPolicy.totalAfterProgress(0L, 44_000L))
        assertEquals(65_000L, VivoScreenOffReceiptPolicy.totalAfterProgress(44_000L, 21_000L))
        assertEquals(65_000L, VivoScreenOffReceiptPolicy.totalAfterProgress(65_000L, -4L))
    }
}
