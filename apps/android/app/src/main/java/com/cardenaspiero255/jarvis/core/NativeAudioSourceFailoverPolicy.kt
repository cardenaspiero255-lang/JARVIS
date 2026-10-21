package com.cardenaspiero255.jarvis.core

/** Source selection is policy, not proof that hardware AEC actually works.
 * One AudioRecord at a time: the caller releases a failed capture before retry.
 */
enum class NativeAudioSourceChoice { VOICE_COMMUNICATION, MICROPHONE }

object NativeAudioSourceFailoverPolicy {
    fun <T : Any> open(
        aecEligible: Boolean,
        attempt: (NativeAudioSourceChoice) -> T?
    ): Pair<NativeAudioSourceChoice, T>? {
        val sources = if (aecEligible)
            listOf(NativeAudioSourceChoice.VOICE_COMMUNICATION,
                NativeAudioSourceChoice.MICROPHONE)
        else listOf(NativeAudioSourceChoice.MICROPHONE)
        for (source in sources) {
            val opened = try {
                attempt(source)
            } catch (revoked: SecurityException) {
                // Never downgrade or retry an Android permission denial.
                throw revoked
            } catch (_: RuntimeException) {
                null
            }
            if (opened != null) return source to opened
        }
        return null
    }
}
