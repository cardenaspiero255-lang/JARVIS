package com.cardenaspiero255.jarvis.core

/** UI wording for the actual runtime connection and stop conditions. */
object HandsStatus {
    fun present(serviceConnected: Boolean, emergencyStopped: Boolean): String =
        when {
            !serviceConnected -> "Accesibilidad no conectada"
            emergencyStopped -> "HANDS detenido"
            else -> "HANDS habilitado: Inicio y Atrás por pulsación"
        }
}
