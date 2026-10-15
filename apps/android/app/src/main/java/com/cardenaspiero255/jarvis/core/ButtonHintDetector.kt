package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

data class OcrLine(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class ButtonHint(
    val label: String,
    val centerX: Int,
    val centerY: Int,
)

/**
 * Conservative OCR *suggestions* for button-looking labels. A match is not
 * proof of clickability and never grants an AccessibilityService operation.
 */
class ButtonHintDetector {
    val canTap: Boolean get() = false

    private val exactLabels = setOf(
        "JUGAR", "JUGAR AHORA", "ACEPTAR", "CANCELAR", "CONTINUAR",
        "ENVIAR", "GUARDAR", "VOLVER", "CERRAR", "SIGUIENTE",
        "INICIAR", "ABRIR", "NUEVA PARTIDA"
    )

    fun detect(lines: List<OcrLine>, frameWidth: Int, frameHeight: Int): List<ButtonHint> {
        if (!VisionImagePolicy().acceptDimensions(frameWidth, frameHeight)) return emptyList()
        val candidates = mutableListOf<ButtonHint>()
        for (line in lines.take(300)) {
            if (line.width < 35 || line.height < 16 || line.x < 0 || line.y < 0 ||
                line.width > frameWidth || line.height > frameHeight ||
                line.x.toLong() + line.width > frameWidth ||
                line.y.toLong() + line.height > frameHeight
            ) continue
            val normalized = Normalizer.normalize(line.text.uppercase(Locale.ROOT),
                Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\s+"), " ").trim()
            if (normalized !in exactLabels) continue
            candidates.add(ButtonHint(normalized,
                line.x + line.width / 2,
                line.y + line.height / 2))
            if (candidates.size == 6) break
        }
        return candidates.toList()
    }

    fun describe(hints: List<ButtonHint>): String =
        if (hints.isEmpty()) "No se identificaron posibles botones por su texto."
        else "Detecté posibles botones (sin pulsarlos): " +
            hints.take(6).joinToString("; ") {
                "${it.label} (${it.centerX}, ${it.centerY})"
            }
}
