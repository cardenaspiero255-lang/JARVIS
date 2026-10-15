package com.cardenaspiero255.jarvis.core

/** Admission control for optional on-device inference; no cloud fallback. */
object LocalModelGate {
    private const val RESERVED_MIB = 1024L

    fun canRun(
        modelPresent: Boolean,
        checksumVerified: Boolean,
        modelSizeMiB: Long,
        runtimeOverheadMiB: Long,
        availableMiB: Long,
        thermalEmergency: Boolean
    ): Boolean {
        if (!modelPresent || !checksumVerified || thermalEmergency) return false
        if (modelSizeMiB <= 0 || runtimeOverheadMiB <= 0 || availableMiB <= 0) return false
        if (modelSizeMiB > Long.MAX_VALUE - runtimeOverheadMiB - RESERVED_MIB) return false
        return availableMiB > modelSizeMiB + runtimeOverheadMiB + RESERVED_MIB
    }
}
