package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.cardenaspiero255.jarvis.core.CompanionEvidence
import com.cardenaspiero255.jarvis.core.SafeAutomationGateway
import com.cardenaspiero255.jarvis.core.SafeAutomationStep
import com.rosan.dhizuku.api.Dhizuku
import rikka.shizuku.Shizuku

/**
 * Small, documented adapters. All calls originate in visible owner-controlled UI.
 * Neither Device Owner activation nor an arbitrary shell/external-LLM command exists.
 */
internal class CompanionActionAdapters(private val activity: Activity) :
    SafeAutomationGateway {

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun dhizukuEvidence(installed: Boolean): CompanionEvidence {
        if (!installed) return CompanionEvidence(false)
        return try {
            val connected = Dhizuku.init(activity)
            val authorized = connected && Dhizuku.isPermissionGranted()
            CompanionEvidence(true, connected, authorized)
        } catch (_: Exception) {
            CompanionEvidence(true)
        }
    }

    /** Only inspect/grant the Dhizuku SDK permission; NEVER configure Device Owner. */
    fun requestDhizukuPermission(onResult: () -> Unit): Boolean {
        return try {
            if (!Dhizuku.init(activity)) return false
            if (Dhizuku.isPermissionGranted()) {
                onResult()
                return true
            }
            Dhizuku.requestPermission { _ ->
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) onResult()
                }
            }
            true
        } catch (_: Exception) { false }
    }

    override fun perform(step: SafeAutomationStep): Boolean = when (step) {
        SafeAutomationStep.CHECK_SHIZUKU -> shizukuReady()
        SafeAutomationStep.CHECK_DHIZUKU -> dhizukuEvidence(true).let {
            it.transportReady && it.authorized
        }
        SafeAutomationStep.OPEN_SHIZUKU -> openCompanion("moe.shizuku.privileged.api")
        SafeAutomationStep.OPEN_DHIZUKU -> openCompanion("com.rosan.dhizuku")
        SafeAutomationStep.OPEN_TERMUX -> openCompanion("com.termux")
        SafeAutomationStep.OPEN_OWNDROID -> openCompanion("com.bintianqi.owndroid")
        SafeAutomationStep.OPEN_ANDROID_SETTINGS -> try {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            true
        } catch (_: Exception) { false }
    }

    private fun openCompanion(packageName: String): Boolean {
        return try {
            val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            activity.startActivity(intent)
            true
        } catch (_: Exception) { false }
    }

    /** Fixed, read-only `id` command; no interpolated paths, parameters or stdin. */
    fun requestTermuxIdentityCheck(): Boolean {
        if (activity.checkSelfPermission("com.termux.permission.RUN_COMMAND") !=
            PackageManager.PERMISSION_GRANTED) return false
        return try {
            val request = Intent("com.termux.RUN_COMMAND")
                .setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/id")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", emptyArray<String>())
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            activity.startService(request) != null
        } catch (_: Exception) { false }
        // Dispatch != execution proof. Termux must also enable allow-external-apps.
    }

    /**
     * User-supplied key lives only in the dialog stack. A broadcast gives no
     * acknowledgement: caller must NEVER tell the user the device was locked.
     */
    fun sendExplicitOwnDroidLock(key: String): Boolean {
        if (key.isBlank() || key.length > 256) return false
        return try {
            activity.sendBroadcast(
                Intent("com.bintianqi.owndroid.action.LOCK")
                    .setComponent(ComponentName(
                        "com.bintianqi.owndroid",
                        "com.bintianqi.owndroid.ApiReceiver"
                    ))
                    .putExtra("key", key)
            )
            true
        } catch (_: Exception) { false }
    }
}
