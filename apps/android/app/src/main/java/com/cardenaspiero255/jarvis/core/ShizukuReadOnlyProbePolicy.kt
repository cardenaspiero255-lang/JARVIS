package com.cardenaspiero255.jarvis.core

/**
 * Classifies a real Shizuku server identity reported through its Binder API.
 * This is a read-only connectivity diagnostic, NOT proof that any privileged
 * system operation or cross-app management action has been performed.
 */
enum class ShizukuReadOnlyStatus { SHELL, ROOT, UNKNOWN_IDENTITY, FAILED }

object ShizukuReadOnlyProbePolicy {
    fun classify(uid: Int, apiVersion: Int): ShizukuReadOnlyStatus = when {
        uid < 0 || apiVersion < 11 -> ShizukuReadOnlyStatus.FAILED
        uid == 2000 -> ShizukuReadOnlyStatus.SHELL
        uid == 0 -> ShizukuReadOnlyStatus.ROOT
        else -> ShizukuReadOnlyStatus.UNKNOWN_IDENTITY
    }
}
