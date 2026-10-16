package com.cardenaspiero255.jarvis.core

/** A service dispatch is NOT evidence that Termux executed a command. */
enum class TermuxDispatchStatus {
    NOT_ATTEMPTED, REQUEST_SENT, MISSING_PERMISSION, SERVICE_UNAVAILABLE,
    SYSTEM_DENIED, ANDROID_RESTRICTED, UNKNOWN_FAILURE
}

object TermuxDispatchExplanation {
    fun describe(status: TermuxDispatchStatus): String = when (status) {
        TermuxDispatchStatus.NOT_ATTEMPTED ->
            "Todavía no se ha solicitado una operación Termux en esta sesión."
        TermuxDispatchStatus.REQUEST_SENT ->
            "Petición enviada a Termux. El resultado sigue pendiente; consulta el callback verificado."
        TermuxDispatchStatus.MISSING_PERMISSION ->
            "JARVIS no tiene RUN_COMMAND autorizado. Abre Ajustes > Aplicaciones > " +
                "JARVIS OS V3 PREVIEW > Permisos > Permisos adicionales y autoriza " +
                "Ejecutar comandos en el entorno de Termux."
        TermuxDispatchStatus.SERVICE_UNAVAILABLE ->
            "Android no encontró el servicio RUN_COMMAND de Termux. Comprueba que Termux " +
                "esté instalado y actualizado; abre Termux antes de repetir la prueba."
        TermuxDispatchStatus.SYSTEM_DENIED ->
            "Android o Termux rechazó el acceso al servicio. Verifica RUN_COMMAND y " +
                "allow-external-apps=true; vuelve a abrir Termux."
        TermuxDispatchStatus.ANDROID_RESTRICTED ->
            "Android no permitió iniciar el servicio de Termux en este estado. Abre Termux " +
                "en primer plano y vuelve a JARVIS para repetir la prueba."
        TermuxDispatchStatus.UNKNOWN_FAILURE ->
            "No se pudo iniciar la prueba de Termux. Abre Termux, revisa su versión y permisos, " +
                "y vuelve a intentarlo; no se ha verificado ninguna ejecución."
    }

    fun shouldOfferAppSettings(status: TermuxDispatchStatus) =
        status == TermuxDispatchStatus.MISSING_PERMISSION ||
            status == TermuxDispatchStatus.SYSTEM_DENIED
}
