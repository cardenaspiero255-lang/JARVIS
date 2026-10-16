package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/** A launch target must originate from Android PackageManager, never model text. */
data class JarvisHomeApp(
    val packageName: String,
    val activityClassName: String,
    val label: String
)

/** Pure, bounded filtering; does not request installed-app privileges. */
object JarvisHomeCatalog {
    private fun normalized(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    fun visible(
        apps: List<JarvisHomeApp>,
        query: String,
        ownPackage: String,
        limit: Int = 60
    ): List<JarvisHomeApp> {
        if (limit <= 0) return emptyList()
        val needle = normalized(query.trim().take(64))
        return apps.asSequence()
            .filter { it.packageName.isNotBlank() && it.activityClassName.isNotBlank() }
            .filter { it.packageName != ownPackage && it.label.isNotBlank() }
            .distinctBy { it.packageName to it.activityClassName }
            .filter {
                needle.isEmpty() || normalized(it.label).contains(needle) ||
                    normalized(it.packageName).contains(needle)
            }
            .sortedWith(
                compareBy<JarvisHomeApp> { normalized(it.label) }
                    .thenBy { it.packageName }
                    .thenBy { it.activityClassName }
            )
            .take(limit.coerceAtMost(200))
            .toList()
    }
}
