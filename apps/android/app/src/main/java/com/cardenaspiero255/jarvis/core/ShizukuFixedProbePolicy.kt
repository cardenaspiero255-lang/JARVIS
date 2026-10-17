package com.cardenaspiero255.jarvis.core

/**
 * Reviewed, fixed probes. Neither voice text nor a remote caller can supply a command.
 * Read-only service operations are executed under Shizuku's UID (ADB shell only here).
 */
enum class ShizukuFixedProbe(val code: Int, val command: List<String>, val label: String) {
    ANDROID_RELEASE(1, listOf("/system/bin/getprop", "ro.build.version.release"),
        "Versión del sistema"),
    ANDROID_SYSTEM_PACKAGE(2, listOf("/system/bin/pm", "path", "android"),
        "Ruta del paquete de Android"),
    ZYGOTE_SERVICE(3, listOf("/system/bin/getprop", "init.svc.zygote"),
        "Servicio Zygote"),
    DISPLAY_SERVICE(4, listOf("/system/bin/getprop", "init.svc.surfaceflinger"),
        "Servicio SurfaceFlinger"),
    SETTINGS_PACKAGE(5, listOf("/system/bin/pm", "path", "com.android.settings"),
        "Paquete de ajustes"),
    WINDOW_ANIMATION_SCALE(6,
        listOf("/system/bin/settings", "get", "global", "window_animation_scale"),
        "Escala de animación (consulta)"),
    SYSTEM_SERVER_PID(7, listOf("/system/bin/pidof", "system_server"),
        "Identificador de proceso system_server");

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

    private val serviceState = Regex("^(?:running|stopped|restarting)$")
    private val scale = Regex("^(?:[0-9]{1,2})(?:\\.[0-9]{1,3})?$")
    private val processIds = Regex("^[1-9][0-9]{0,5}(?: [1-9][0-9]{0,5}){0,5}$")

    fun isValid(probe: ShizukuFixedProbe, result: String): Boolean {
        if (result.isBlank() || result.length > 256 || result != result.trim()) return false
        return when (probe) {
            ShizukuFixedProbe.ANDROID_RELEASE -> release.matches(result)
            ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE,
            ShizukuFixedProbe.SETTINGS_PACKAGE -> systemPackage.matches(result)
            ShizukuFixedProbe.ZYGOTE_SERVICE,
            ShizukuFixedProbe.DISPLAY_SERVICE -> serviceState.matches(result)
            ShizukuFixedProbe.WINDOW_ANIMATION_SCALE -> scale.matches(result)
            ShizukuFixedProbe.SYSTEM_SERVER_PID -> processIds.matches(result)
        }
    }

    fun complete(uid: Int, releaseValue: String, systemPackagePath: String): Boolean =
        isAllowedUid(uid) && isValid(ShizukuFixedProbe.ANDROID_RELEASE, releaseValue) &&
            isValid(ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE, systemPackagePath)
}
