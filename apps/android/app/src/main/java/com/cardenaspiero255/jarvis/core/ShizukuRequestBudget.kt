package com.cardenaspiero255.jarvis.core

/**
 * Bound the entire Shizuku UserService request, not just an individual shell probe.
 *
 * The seven advanced probes run sequentially. A 12-second request deadline could
 * expire before the seventh 4-second command finished. Allow 4 seconds/process,
 * 1 second/probe for Binder and result validation, and 7 seconds for cold startup.
 * This is a maximum wait, not a deliberate delay. The client still returns early
 * when commands finish or when its user cancels.
 */
object ShizukuRequestBudget {
    const val PROCESS_WAIT_MS = 4_000L
    const val PER_PROBE_ALLOWANCE_MS = PROCESS_WAIT_MS + 1_000L
    const val STARTUP_ALLOWANCE_MS = 7_000L
    private const val BASE_PROBES = 2

    fun probeCount(advanced: Boolean): Int =
        if (advanced) ShizukuFixedProbe.values().size else BASE_PROBES

    fun timeoutMs(advanced: Boolean): Long =
        STARTUP_ALLOWANCE_MS + probeCount(advanced) * PER_PROBE_ALLOWANCE_MS

    fun withinDeadline(advanced: Boolean, elapsedMs: Long): Boolean =
        elapsedMs >= 0L && elapsedMs <= timeoutMs(advanced)
}
