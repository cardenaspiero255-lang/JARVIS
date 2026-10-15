package com.cardenaspiero255.jarvis.core

/**
 * User-selectable TTS character; not a recording or imitation of any actor.
 * Only offline voices installed and exposed by Android are eligible.
 */
enum class VoiceStyle {
    SPANISH,
    BRITISH_ASSISTANT
}

data class InstalledVoice(
    val name: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
)

/** Pure Kotlin policy, also exercised by JVM tests. */
object VoiceStyleSelector {
    fun candidates(style: VoiceStyle, installed: List<InstalledVoice>): List<InstalledVoice> {
        val candidates = installed.filter { voice ->
            !voice.requiresNetwork && voice.name.isNotBlank() &&
                when (style) {
                    VoiceStyle.SPANISH -> voice.localeTag.startsWith("es-", ignoreCase = true) ||
                        voice.localeTag.equals("es", ignoreCase = true)
                    VoiceStyle.BRITISH_ASSISTANT ->
                        voice.localeTag.equals("en-GB", ignoreCase = true)
                }
        }
        return candidates.sortedWith(
            compareBy<InstalledVoice> {
                when (style) {
                    VoiceStyle.SPANISH ->
                        if (it.localeTag.equals("es-CL", ignoreCase = true)) 0 else 1
                    VoiceStyle.BRITISH_ASSISTANT -> 0
                }
            }.thenBy { it.name.lowercase() }
        )
    }

    fun select(style: VoiceStyle, installed: List<InstalledVoice>): InstalledVoice? =
        candidates(style, installed).firstOrNull()

    fun pitch(style: VoiceStyle): Float = when (style) {
        VoiceStyle.SPANISH -> 1.00f
        VoiceStyle.BRITISH_ASSISTANT -> 0.92f
    }

    fun rate(style: VoiceStyle): Float = when (style) {
        VoiceStyle.SPANISH -> 0.96f
        VoiceStyle.BRITISH_ASSISTANT -> 0.91f
    }
}
