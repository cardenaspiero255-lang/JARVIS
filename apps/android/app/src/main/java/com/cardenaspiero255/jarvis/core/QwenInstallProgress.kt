package com.cardenaspiero255.jarvis.core

/**
 * Real byte-counted progress; reaching 100% never implies a verified model.
 * The installer must complete the fixed SHA-256 check before calling verified().
 */
object QwenInstallProgress {
    enum class Phase { CONNECTING, DOWNLOADING, VERIFYING, VERIFIED }

    data class Snapshot(val phase: Phase, val percent: Int, val message: String)

    fun connecting() = Snapshot(
        Phase.CONNECTING, 0,
        "Qwen: conectando con Hugging Face por HTTPS... 0 de 491 MB."
    )

    fun fromBytes(downloadedBytes: Long): Snapshot {
        val expected = LocalLlmPolicy.MODEL_BYTES
        val safe = downloadedBytes.coerceIn(0L, expected)
        val percent = (safe * 100L / expected).toInt().coerceIn(0, 100)
        val downloadedMB = safe / 1_000_000L
        return if (safe == expected) {
            Snapshot(
                Phase.VERIFYING, 100,
                "Qwen: descargados 491 de 491 MB (100 %). Verificando SHA-256; todavía NO instalado."
            )
        } else {
            Snapshot(
                Phase.DOWNLOADING, percent,
                "Qwen: descargando $downloadedMB de 491 MB ($percent %). Mantén JARVIS abierto."
            )
        }
    }

    fun verified() = Snapshot(
        Phase.VERIFIED, 100,
        "Qwen instalado: 491 MB y SHA-256 verificado. Ejecuta las 5 pruebas para validar ARM64."
    )
}
