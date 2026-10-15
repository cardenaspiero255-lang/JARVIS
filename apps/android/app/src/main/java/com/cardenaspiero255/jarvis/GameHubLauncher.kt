package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import com.cardenaspiero255.jarvis.core.GameHubLaunchPolicy

/**
 * User-tapped app switch only. No implicit broadcasts, exported service
 * access, data transfer, privileges, credentials or remote GameHub commands.
 */
class GameHubLauncher(private val activity: Activity) {
    private val policy = GameHubLaunchPolicy()

    fun launchOnTap(): Boolean {
        val intent = try {
            activity.packageManager.getLaunchIntentForPackage(policy.packageName)
        } catch (_: RuntimeException) {
            null
        } ?: return false

        val resolvedPackage = intent.component?.packageName ?: return false
        if (!policy.canLaunch(
                explicitUserTap = true,
                appInstalled = true,
                resolvedPackage = resolvedPackage
            )) return false

        intent.setPackage(policy.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
