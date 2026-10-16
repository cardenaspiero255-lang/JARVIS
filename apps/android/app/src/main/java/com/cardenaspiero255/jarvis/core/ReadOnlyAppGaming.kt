package com.cardenaspiero255.jarvis.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LaunchableApp(val title: String, val packageName: String)

object ReadOnlyAppInventory {
    /** Only launcher-visible apps; no QUERY_ALL_PACKAGES or cross-user enumeration. */
    @Suppress("DEPRECATION")
    fun list(context: Context, limit: Int = 30): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        val matches = try {
            if (Build.VERSION.SDK_INT >= 33)
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            else pm.queryIntentActivities(intent, 0)
        } catch (_: RuntimeException) { emptyList() }
        return matches.mapNotNull { item ->
            val pkg = item.activityInfo?.packageName ?: return@mapNotNull null
            val label = try { item.loadLabel(pm).toString() }
                        catch (_: RuntimeException) { pkg }
            LaunchableApp(label.take(50), pkg)
        }.distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .take(limit.coerceIn(1, 50))
    }
}

enum class GamingAdvice { LOW_MEMORY, CHECK_RESOURCES, READY_FOR_MANUAL_LAUNCH }

object GamingReadinessPolicy {
    /** Guidance only; no process termination, refresh changes or fake FPS claims. */
    fun classify(availableRamMb: Long?, lowMemory: Boolean?): GamingAdvice = when {
        lowMemory == true -> GamingAdvice.LOW_MEMORY
        availableRamMb == null || lowMemory == null -> GamingAdvice.CHECK_RESOURCES
        availableRamMb < 768L -> GamingAdvice.LOW_MEMORY
        else -> GamingAdvice.READY_FOR_MANUAL_LAUNCH
    }

    fun message(advice: GamingAdvice): String = when (advice) {
        GamingAdvice.LOW_MEMORY ->
            "Android informa poca memoria disponible. Cierra aplicaciones manualmente si lo deseas; JARVIS no detuvo procesos."
        GamingAdvice.CHECK_RESOURCES ->
            "No se pudo acreditar el estado completo de memoria. Revisa el dispositivo antes de jugar."
        GamingAdvice.READY_FOR_MANUAL_LAUNCH ->
            "Sin alerta de memoria en la lectura actual. Puedes abrir el juego manualmente; no se han aplicado optimizaciones ni medido FPS."
    }
}
