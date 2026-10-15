package com.cardenaspiero255.jarvis.core

/** User-selected advice only. This does not change Android performance settings. */
enum class GamingPreference { BALANCED, COMPETITIVE, VISUAL_QUALITY }
enum class GameReadinessLevel { READY, CAUTION, POSTPONE, UNKNOWN }

data class GameReadinessReport(
    val level: GameReadinessLevel,
    val preference: GamingPreference,
    val observations: List<String>
)

/**
 * Conservative one-shot evaluation of Android public battery/thermal/memory data.
 * No data retention, capability to launch games or background monitoring.
 */
object GameReadinessAdvisor {
    fun assess(
        health: DeviceHealthSnapshot,
        preference: GamingPreference
    ): GameReadinessReport {
        val thermal = health.thermalStatus
        val battery = health.batteryPercent?.takeIf { it in 0..100 }
        val memoryValid = health.availableMemoryBytes != null &&
            health.totalMemoryBytes != null &&
            health.totalMemoryBytes > 0 &&
            health.availableMemoryBytes >= 0 &&
            health.availableMemoryBytes <= health.totalMemoryBytes

        if (thermal in 4..6) {
            return GameReadinessReport(GameReadinessLevel.POSTPONE, preference,
                listOf("Estado térmico crítico: conviene posponer el juego y dejar enfriar el teléfono."))
        }

        val warnings = mutableListOf<String>()
        if (thermal == 3) warnings +=
            "El estado térmico es alto; considera reducir la carga gráfica para mejorar la estabilidad."
        if (health.lowMemory == true) warnings +=
            "Android reporta memoria baja: considera cerrar aplicaciones manualmente."
        if (battery != null && battery < 15 && health.charging != true) warnings +=
            "La batería está baja: prepara una carga antes de una partida larga."

        val uncertain = (thermal == null || thermal !in 0..6 || battery == null ||
            !memoryValid || health.lowMemory == null)
        val level = when {
            warnings.isNotEmpty() -> GameReadinessLevel.CAUTION
            uncertain -> GameReadinessLevel.UNKNOWN
            else -> GameReadinessLevel.READY
        }
        val advice = when (level) {
            GameReadinessLevel.READY -> listOf(
                "La lectura puntual no muestra avisos importantes; no garantiza los FPS."
            )
            GameReadinessLevel.CAUTION -> warnings
            GameReadinessLevel.UNKNOWN -> listOf(
                "Hay mediciones insuficientes; no se puede confirmar el estado para jugar."
            )
            GameReadinessLevel.POSTPONE -> error("handled above")
        }
        return GameReadinessReport(level, preference, advice)
    }

    fun format(report: GameReadinessReport): String {
        val heading = when (report.level) {
            GameReadinessLevel.READY -> "LISTO CON RESERVAS"
            GameReadinessLevel.CAUTION -> "PRECAUCIÓN"
            GameReadinessLevel.POSTPONE -> "RECOMENDACIÓN: POSPONER"
            GameReadinessLevel.UNKNOWN -> "DATOS INSUFICIENTES"
        }
        val profile = when (report.preference) {
            GamingPreference.BALANCED -> "equilibrado"
            GamingPreference.COMPETITIVE -> "competitivo"
            GamingPreference.VISUAL_QUALITY -> "calidad visual"
        }
        val optional = when (report.preference) {
            GamingPreference.BALANCED -> "Prioriza el equilibrio entre calidad y batería."
            GamingPreference.COMPETITIVE ->
                "Perfil competitivo: prioriza la estabilidad de FPS, sin garantizar una tasa."
            GamingPreference.VISUAL_QUALITY ->
                "Perfil calidad visual: reduce efectos si aparecen calor o bajadas de rendimiento."
        }
        return "$heading — perfil $profile. " +
            report.observations.joinToString(" ") + " " + optional +
            " Sin certificación: sugerencia local, sin cambios automáticos al teléfono."
    }
}
