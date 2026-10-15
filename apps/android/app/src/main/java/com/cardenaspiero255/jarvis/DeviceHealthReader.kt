package com.cardenaspiero255.jarvis

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.cardenaspiero255.jarvis.core.DeviceHealthSnapshot

/**
 * One-shot Android system health query, only called from a visible button.
 * No permissions, listeners, background polling, persistence or uploads.
 */
class DeviceHealthReader(private val activity: Activity) {
    fun read(): DeviceHealthSnapshot {
        val battery: Intent? = try {
            activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) {
            null
        }
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0 && level <= scale) {
            (level.toLong() * 100L / scale).toInt()
        } else null
        val charging = when (battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        val thermal: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.currentThermalStatus
            } catch (_: RuntimeException) {
                null
            }
        } else null
        val memory = try {
            val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (manager != null) {
                val info = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(info)
                Triple(info.availMem, info.totalMem, info.lowMemory)
            } else null
        } catch (_: RuntimeException) {
            null
        }
        return DeviceHealthSnapshot(
            batteryPercent = percent, charging = charging, thermalStatus = thermal,
            availableMemoryBytes = memory?.first, totalMemoryBytes = memory?.second,
            lowMemory = memory?.third
        )
    }
}
