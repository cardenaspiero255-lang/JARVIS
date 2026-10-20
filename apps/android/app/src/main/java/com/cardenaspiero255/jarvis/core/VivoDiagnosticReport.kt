package com.cardenaspiero255.jarvis.core

/** No voice, transcripts, names, private model paths, IDs or device serials. */
enum class VivoCheck {
    MICROPHONE, WAKE, LOCKSCREEN, OWNER, QWEN, TTS, STOP
}

enum class VivoReportedResult {
    NO_PROBADO, FUNCIONA, FALLA
}

/**
 * A physical-session report is NEVER a runtime certification by itself.
 * The app measures system status; the owner explicitly records acoustic outcomes.
 */
data class VivoDiagnosticReport(
    val model: String,
    val sdk: Int,
    val batteryPercent: Int?,
    val thermalStatus: Int?,
    val availableRamMib: Long?,
    val pssKib: Int?,
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val ggufImported: Boolean,
    val screenOffSamples: Long,
    val screenOffObserved: Boolean,
    val lockscreenLastExtended: Boolean,
    val lockscreenTimeCommandCount: Int,
    val lockscreenStopCommandCount: Int,
    val qwenRun: String,
    val qwenLatencyMs: Long?,
    val stopRun: String,
    val results: Map<VivoCheck, VivoReportedResult>
) {
    fun toShareText(): String {
        val safeModel = model.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }
            .take(40).ifEmpty { "UNKNOWN" }
        return buildString {
            appendLine("JARVIS OMEGA · DIAGNÓSTICO FÍSICO · NO_CERTIFICADO")
            appendLine("Fuente: lectura local + pruebas USER_REPORTED.")
            appendLine("Dispositivo modelo=${safeModel} Android_API=$sdk (sin serial/IMEI)")
            appendLine("Batería=${batteryPercent ?: "UNKNOWN"}% térmico_codigo=${thermalStatus ?: "UNKNOWN"}")
            appendLine("RAM_libre_MiB=${availableRamMib ?: "UNKNOWN"} PSS_proceso_KiB=${pssKib ?: "UNKNOWN"}")
            appendLine("Permisos_MIC=$microphoneGranted notificaciones=$notificationsGranted")
            appendLine("GGUF_importado_indicio=$ggufImported (hash solo al ejecutar)")
            appendLine("Pantalla_apagada_PCM_muestras=${screenOffSamples.coerceAtLeast(0)} " +
                "observado=$screenOffObserved (NO verifica identidad ni wake)")
            appendLine("Pantalla_apagada_fuente=" + if (lockscreenLastExtended)
                "ULTIMA_SESION_EXTENDIDA" else "NO_PROBADA_EN_ESTE_DIAGNOSTICO")
            appendLine("Comandos_HORA_recibidos_pantalla_apagada=" +
                lockscreenTimeCommandCount.coerceAtLeast(0))
            appendLine("Comandos_STOP_recibidos_pantalla_apagada=" +
                lockscreenStopCommandCount.coerceAtLeast(0))
            appendLine("Comandos recibidos no certifican audio audible ni identidad del hablante.")
            appendLine("Qwen_ejecución=$qwenRun latencia_ms=${qwenLatencyMs ?: "NO_MEDIDA"}")
            appendLine("STOP_ejecución=" + stopRun.filter { it.isLetterOrDigit() || it == '_' }.take(48))
            for (check in VivoCheck.entries) {
                appendLine("USER_REPORTED_${check.name}=${results[check] ?: VivoReportedResult.NO_PROBADO}")
            }
            if (results[VivoCheck.QWEN] == VivoReportedResult.FUNCIONA &&
                (!ggufImported || qwenRun != "TEXTO_NO_VACIO_NO_CERTIFICADO" ||
                    qwenLatencyMs == null || qwenLatencyMs <= 0)) {
                appendLine("CONTRADICCION_QWEN: marca manual sin medición local confirmada.")
            }
            if (results[VivoCheck.OWNER] == VivoReportedResult.FUNCIONA) {
                appendLine("OWNER_NO_CERTIFICADO: prueba positiva sin FAR/FRR/replay.")
            }
            if (results[VivoCheck.LOCKSCREEN] == VivoReportedResult.FUNCIONA &&
                (!lockscreenLastExtended || !screenOffObserved ||
                    (lockscreenTimeCommandCount <= 0 && lockscreenStopCommandCount <= 0))) {
                appendLine("LOCKSCREEN_NO_CORROBORADO: éxito manual sin " +
                    "muestras y orden reconocida en la misma sesión extendida. " +
                    "Revisar si se inició, se bloqueó y se habló durante la escucha.")
            }
            if (screenOffObserved && results[VivoCheck.LOCKSCREEN] == VivoReportedResult.FALLA) {
                appendLine("LOCKSCREEN_PARCIAL: hubo PCM con pantalla apagada, " +
                    "pero no se acreditó respuesta o continuidad funcional.")
            }
            if (results[VivoCheck.STOP] == VivoReportedResult.FUNCIONA &&
                stopRun == "SIN_SESION_ACTIVA") {
                appendLine("STOP_SIN_SESION_ACTIVA: marcar FUNCIONA no prueba ninguna " +
                    "cancelación; iniciar una sesión real antes de ordenar DETENER.")
            } else if (results[VivoCheck.STOP] == VivoReportedResult.FUNCIONA &&
                stopRun != "STOP_SERVICIO_FINALIZADO") {
                appendLine("STOP_NO_CONFIRMADO: falta recibo del servicio para esta prueba.")
            }
            appendLine("NO_CERTIFICADO: no asegura 24/7, autenticación del propietario, " +
                "FAR/FRR ni semántica por pruebas no realizadas.")
            appendLine("Privacidad: sin audio, transcripciones, contenido de respuestas, " +
                "credenciales, identificadores únicos ni rutas de archivos.")
        }
    }
}
