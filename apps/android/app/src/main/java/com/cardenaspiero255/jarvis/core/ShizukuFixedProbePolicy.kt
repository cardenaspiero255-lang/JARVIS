package com.cardenaspiero255.jarvis.core

/**
 * Reviewed, fixed probes. Neither voice text nor a remote caller can supply a command.
 * Read-only service operations are executed under Shizuku's UID (ADB shell only here).
 */
enum class ShizukuFixedProbe(val code: Int, val command: List<String>, val label: String) {
    ANDROID_RELEASE(1, listOf("/system/bin/getprop", "ro.build.version.release"),
        "Versión del sistema"),
    ANDROID_SYSTEM_PACKAGE(2, listOf("/system/bin/pm", "path", "android"),
        "Ruta del paquete de Android");

    companion object {
        fun fromCode(code: Int) = values().firstOrNull { it.code == code }
    }
}

object ShizukuFixedProbePolicy {
    fun isAllowedUid(uid: Int): Boolean = uid == 2000

    private val release = Regex("^[1-9][0-9]?(?:\\.[0-9]{1,2}){0,2}$")
    private val systemPackage = Regex(
        "^package:/(?:system|system_ext|product|vendor)/[A-Za-z0-9._/+\\-]+\\.apk$"
    )

    fun isValid(probe: ShizukuFixedProbe, result: String): Boolean {
        if (result.isBlank() || result.length > 256 || result != result.trim()) return false
        return when (probe) {
            ShizukuFixedProbe.ANDROID_RELEASE -> release.matches(result)
            ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE -> systemPackage.matches(result)
        }
    }

    fun complete(uid: Int, releaseValue: String, systemPackagePath: String): Boolean =
        isAllowedUid(uid) && isValid(ShizukuFixedProbe.ANDROID_RELEASE, releaseValue) &&
            isValid(ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE, systemPackagePath)
}
