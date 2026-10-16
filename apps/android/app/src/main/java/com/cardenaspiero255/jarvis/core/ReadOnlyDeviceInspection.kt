package com.cardenaspiero255.jarvis.core

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

/** Fixed, foreground-only diagnostic. Never writes Android settings. */
data class DeviceInspection(
    val device: String,
    val api: Int,
    val batteryPercent: Int?,
    val availableRamMb: Long?,
    val lowMemory: Boolean?,
    val animationScale: Float?,
)

object DeviceInspectionFormatter {
    fun describe(data: DeviceInspection): String = listOf(
        "Modelo: ${data.device.take(80)} (Android API ${data.api}).",
        "Batería: ${data.batteryPercent?.let { "$it%" } ?: "no disponible"}.",
        "RAM disponible: ${data.availableRamMb?.let { "$it MB" } ?: "no disponible"}.",
        "Aviso de memoria: ${when (data.lowMemory) { true -> "activo"; false -> "no"; null -> "no disponible" }}.",
        "Escala de animaciones: ${data.animationScale?.let { "${it}x" } ?: "no accesible"}."
    ).joinToString("\n")
}

object ReadOnlyDeviceInspection {
    fun capture(context: Context): DeviceInspection {
        val memory = try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        } catch (_: RuntimeException) { null }
        val battery = try {
            (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        } catch (_: RuntimeException) { null }
        val scale = try {
            Settings.Global.getFloat(context.contentResolver,
                Settings.Global.WINDOW_ANIMATION_SCALE).takeIf { it.isFinite() && it >= 0f }
        } catch (_: RuntimeException) { null }
        return DeviceInspection(
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            api = Build.VERSION.SDK_INT,
            batteryPercent = battery,
            availableRamMb = memory?.availMem?.div(1024L * 1024L),
            lowMemory = memory?.lowMemory,
            animationScale = scale
        )
    }
}
