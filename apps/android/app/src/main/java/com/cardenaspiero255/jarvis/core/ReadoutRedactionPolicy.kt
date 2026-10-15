package com.cardenaspiero255.jarvis.core

/** UI-only redaction; no private value is passed into or retained by this policy. */
enum class SensitiveReadout { CAPABILITIES, DEVICE_HEALTH, GAME_READINESS }

object ReadoutRedactionPolicy {
    fun redacted(kind: SensitiveReadout): String = when (kind) {
        SensitiveReadout.CAPABILITIES -> "Diagnóstico oculto. Consulta de nuevo."
        SensitiveReadout.DEVICE_HEALTH -> "Lecturas de salud ocultas. Consulta de nuevo."
        SensitiveReadout.GAME_READINESS -> "Evaluación de juego oculta. Evalúa de nuevo."
    }
}
