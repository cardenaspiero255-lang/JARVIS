package com.cardenaspiero255.jarvis.core

import java.util.Locale

enum class SpanishAvailability { READY, DOWNLOAD_REQUIRED, NOT_SUPPORTED }

/**
 * Android on-device recognizer compatibility policy. ASR and local TTS models
 * are unrelated; do not mistake an installed TTS voice for an ASR model.
 *
 * Android SpeechRecognizer's isOnDeviceRecognitionAvailable() proves only
 * that a service exists, NOT that it supports es-CL. API 33+
 * checkRecognitionSupport() returns actual installed on-device language tags.
 */
class OfflineSpanishSupportPolicy {
    private val preferred = listOf("es-CL", "es-ES", "es-MX", "es-419", "es")

    fun selectInstalled(installed: List<String>, attempted: Set<String> = emptySet()): String? {
        val normalized = installed.mapNotNull(::normalizeSpanish)
            .filterNot { it in attempted }.toSet()
        return preferred.firstOrNull { it in normalized }
            ?: normalized.sorted().firstOrNull()
    }

    fun availability(
        installed: List<String>,
        downloadable: List<String>
    ): SpanishAvailability = when {
        selectInstalled(installed) != null -> SpanishAvailability.READY
        selectInstalled(downloadable) != null -> SpanishAvailability.DOWNLOAD_REQUIRED
        else -> SpanishAvailability.NOT_SUPPORTED
    }

    /** API 31/32 or OEM provider without query support: try a single offline locale. */
    fun unverifiedFallbackLanguage(): String = "es-ES"

    /** Never try cloud or non-Spanish. Limit unknown-provider fallback strictly. */
    fun fallbackAfterUnsupported(current: String, attempted: Set<String>): String? {
        val candidate = listOf("es-ES", "es-MX").firstOrNull { it !in attempted }
        return candidate?.takeIf { it != current }
    }

    fun errorReason(errorCode: Int): String = when (errorCode) {
        12 -> "LANGUAGE_NOT_SUPPORTED"
        13 -> "LANGUAGE_MODEL_NOT_INSTALLED"
        else -> "LANGUAGE_UNKNOWN_ERROR_$errorCode"
    }

    private fun normalizeSpanish(raw: String): String? {
        val normalized = raw.trim().replace('_', '-')
        val sections = normalized.split('-')
        if (sections.isEmpty() || !sections[0].equals("es", ignoreCase = true) ||
            sections.size > 2
        ) return null
        return if (sections.size == 1) "es" else {
            val region = sections[1].uppercase(Locale.ROOT)
            if (region != "419" && !Regex("[A-Z]{2}").matches(region)) null
            else "es-$region"
        }
    }
}
