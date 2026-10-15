package com.cardenaspiero255.jarvis.core

/** Derived solely from Android's one-shot health snapshot; never arbitrary ASR/OCR text. */
class DeviceHealthSpeechPolicy {
    fun textForSpeech(
        snapshot: DeviceHealthSnapshot,
        explicitTap: Boolean,
        ready: Boolean,
        stopped: Boolean
    ): String? {
        if (!explicitTap || !ready || stopped) return null
        val percent = snapshot.batteryPercent?.takeIf { it in 0..100 }
        val battery = if (percent == null) "Batería no disponible" else {
            val state = when (snapshot.charging) {
                true -> ", cargando"
                false -> ", sin cargar"
                null -> ""
            }
            "Batería al $percent por ciento$state"
        }
        val thermal = when (snapshot.thermalStatus) {
            0 -> "normal"
            1 -> "ligero"
            2 -> "moderado"
            3 -> "alto"
            4 -> "crítico"
            5 -> "emergencia"
            6 -> "apagado próximo"
            else -> "no disponible"
        }
        val memory = if (snapshot.availableMemoryBytes != null &&
                snapshot.totalMemoryBytes != null &&
                snapshot.totalMemoryBytes > 0 &&
                snapshot.availableMemoryBytes >= 0 &&
                snapshot.availableMemoryBytes <= snapshot.totalMemoryBytes) {
            "RAM disponible: ${snapshot.availableMemoryBytes / 1_048_576L} MiB"
        } else "RAM no disponible"
        return listOfNotNull(
            battery,
            "estado térmico $thermal",
            memory,
            if (snapshot.lowMemory == true) "Android reporta memoria baja" else null
        ).joinToString(". ", postfix = ".")
    }
}
