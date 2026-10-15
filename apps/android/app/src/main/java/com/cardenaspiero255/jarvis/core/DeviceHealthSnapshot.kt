package com.cardenaspiero255.jarvis.core

/** Read-only snapshot. This class holds no persistent history or telemetry. */
data class DeviceHealthSnapshot(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val thermalStatus: Int?,
    val availableMemoryBytes: Long?,
    val totalMemoryBytes: Long?,
    val lowMemory: Boolean?
)

/** Local, bounded presentation. No battery/thermal optimization claims. */
object DeviceHealthFormatter {
    fun format(snapshot: DeviceHealthSnapshot): String {
        val percent = snapshot.batteryPercent?.takeIf { it in 0..100 }
        val battery = if (percent == null) "Batería: no disponible" else {
            val charge = when (snapshot.charging) {
                true -> "cargando"
                false -> "sin cargar"
                null -> "carga desconocida"
            }
            "Batería: $percent% ($charge)"
        }
        val thermalLabel = when (snapshot.thermalStatus) {
            0 -> "normal"
            1 -> "ligero"
            2 -> "moderado"
            3 -> "alto"
            4 -> "crítico"
            5 -> "emergencia"
            6 -> "apagado próximo"
            else -> "no disponible"
        }
        val thermal = "Estado térmico: $thermalLabel"
        val available = snapshot.availableMemoryBytes
        val total = snapshot.totalMemoryBytes
        val memory = if (available != null && total != null &&
                total > 0 && available >= 0 && available <= total) {
            "RAM disponible: ${available / MIB} MiB de ${total / MIB} MiB"
        } else "RAM disponible: no disponible"
        val parts = mutableListOf(battery, thermal, memory)
        if (snapshot.thermalStatus in 3..6) {
            parts += "Advertencia térmica: considera reducir la carga del dispositivo"
        }
        if (snapshot.lowMemory == true) {
            parts += "Advertencia: Android reporta poca memoria"
        }
        return parts.joinToString(". ", postfix = ".")
    }

    private const val MIB = 1_048_576L
}
