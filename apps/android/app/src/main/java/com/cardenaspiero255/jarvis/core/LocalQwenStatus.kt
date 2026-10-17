package com.cardenaspiero255.jarvis.core

/**
 * Distinguishes verified local weights from the ability to execute inference.
 * Status messages never treat the deterministic fallback as Qwen evidence.
 */
object LocalQwenStatus {
    enum class Code {
        ABSENT, INCOMPLETE, SHA_MISMATCH, LOW_MEMORY, THERMAL, READY,
        INTERRUPTED, ERROR
    }

    fun describe(code: Code): String = when (code) {
        Code.ABSENT ->
            "Qwen NO instalado. Confirma INSTALAR en el diálogo y mantén JARVIS abierto con Wi-Fi."
        Code.INCOMPLETE ->
            "Qwen NO instalado: el archivo está incompleto. Repite la descarga de 491 MB."
        Code.SHA_MISMATCH ->
            "Qwen NO habilitado: SHA-256 incorrecto. Repite la descarga oficial."
        Code.LOW_MEMORY ->
            "Qwen instalado y verificado, pero sin memoria libre suficiente para inferencia. Cierra juegos y vuelve a intentar."
        Code.THERMAL ->
            "Qwen instalado y verificado, pero Android indica temperatura elevada. Deja enfriar el teléfono."
        Code.READY ->
            "Qwen instalado, SHA-256 verificado y recursos suficientes: listo para intentar generar. Comprueba ahora las 5 pruebas."
        Code.INTERRUPTED ->
            "Qwen: descarga interrumpida al salir de JARVIS. Regresa y consulta el estado antes de reintentar."
        Code.ERROR ->
            "Qwen: no se pudo comprobar el modelo. Reintenta CONSULTAR ESTADO; revisa almacenamiento y permisos."
    }
}
