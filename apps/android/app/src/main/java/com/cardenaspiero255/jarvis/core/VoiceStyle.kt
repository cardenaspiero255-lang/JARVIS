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
            }.thenBy {
                // Android has no portable API for voice gender; prefer a clearly
                // labelled masculine local timbre when the installed TTS engine
                // exposes one. "female" must not match the substring "male".
                val label = it.name.lowercase()
                when {
                    Regex("""(^|[-_. ])female($|[-_. 0-9])""").containsMatchIn(label) -> 2
                    Regex("""(^|[-_. ])male($|[-_. 0-9])""").containsMatchIn(label) ||
                        "masculino" in label || "masculine" in label -> 0
                    else -> 1
                }
            }.thenBy { it.name.lowercase() }
        )
    }

    fun select(style: VoiceStyle, installed: List<InstalledVoice>): InstalledVoice? =
        candidates(style, installed).firstOrNull()

    fun pitch(style: VoiceStyle): Float = when (style) {
        VoiceStyle.SPANISH -> 0.94f
        VoiceStyle.BRITISH_ASSISTANT -> 0.88f
    }

    fun rate(style: VoiceStyle): Float = when (style) {
        VoiceStyle.SPANISH -> 0.95f
        VoiceStyle.BRITISH_ASSISTANT -> 0.89f
    }
}
