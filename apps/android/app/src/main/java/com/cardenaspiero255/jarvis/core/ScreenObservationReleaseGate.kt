package com.cardenaspiero255.jarvis.core

/**
 * Fail-closed release gate. MediaProjection has unit and emulator coverage
 * but still lacks on-device consent/revocation and CPU/RAM verification.
 *
 * Never enable from remote config, voice, UI text, or a model reply.
 * A future release must deliberately change code, tests and manifest,
 * then pass independent physical acceptance checks.
 */
object ScreenObservationReleaseGate {
    const val ENABLED = false
    const val UNAVAILABLE_MESSAGE =
        "La visión continua está desactivada hasta pasar pruebas físicas de privacidad. " +
        "Puedes analizar imágenes elegidas sin activar esta función."
}
