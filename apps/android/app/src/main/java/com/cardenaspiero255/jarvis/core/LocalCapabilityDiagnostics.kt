package com.cardenaspiero255.jarvis.core

/** One-time on-device capability status; no identifiers, usage data or telemetry. */
data class LocalCapabilitySnapshot(
    val androidApi: Int,
    val gameHubLaunchable: Boolean,
    val handsConnected: Boolean,
    val handsStopped: Boolean,
    val offlineSpeechReady: Boolean,
    val speechRecognizerAvailable: Boolean,
    val encryptedMemoryUnlocked: Boolean
)

object LocalCapabilityDiagnostics {
    fun format(snapshot: LocalCapabilitySnapshot): String {
        val api = snapshot.androidApi.takeIf { it >= 1 }?.toString() ?: "desconocida"
        val hands = when {
            !snapshot.handsConnected -> "desconectado"
            snapshot.handsStopped -> "conectado, detenido"
            else -> "conectado, autorizado en esta sesión"
        }
        return listOf(
            "Android API: $api",
            "GameHub Ultra: " + if (snapshot.gameHubLaunchable)
                "lanzador detectado" else "no se detectó lanzador",
            "Hands: $hands",
            "Voz TTS local: " + if (snapshot.offlineSpeechReady)
                "motor preparado" else "no preparada",
            "Reconocimiento Android: " + if (snapshot.speechRecognizerAvailable)
                "proveedor detectado" else "no detectado",
            "Memoria privada: " + if (snapshot.encryptedMemoryUnlocked)
                "abierta en esta sesión" else "cerrada",
            "SIN CERTIFICACIÓN — estado detectado, no prueba física de extremo a extremo"
        ).joinToString("\n")
    }
}
