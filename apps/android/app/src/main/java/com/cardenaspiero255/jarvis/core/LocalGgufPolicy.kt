package com.cardenaspiero255.jarvis.core

/**
 * Bound an optional local model so GGUF-generated TEXT can never become
 * authority to execute device commands or make paid/network requests.
 */
object LocalGgufPolicy {
    const val MAX_BYTES = 1_600_000_000L
    const val MAX_NEW_TOKENS = 96
    const val MAX_CONTEXT_TOKENS = 1024
    const val MAX_PROMPT_LENGTH = 1000
    const val canIssueDeviceActions = false

    fun validLength(bytes: Long) = bytes in 8..MAX_BYTES

    fun validHeader(header: ByteArray): Boolean =
        header.size >= 8 && header[0] == 'G'.code.toByte() &&
            header[1] == 'G'.code.toByte() &&
            header[2] == 'U'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            (header[4] == 2.toByte() || header[4] == 3.toByte()) &&
            header[5] == 0.toByte() &&
            header[6] == 0.toByte() && header[7] == 0.toByte()

    fun validPrompt(prompt: String): Boolean {
        if (prompt.isBlank() || prompt.length > MAX_PROMPT_LENGTH ||
            prompt.any { Character.isISOControl(it) || Character.getType(it) ==
                Character.FORMAT.toInt() }) return false
        // No language-model response is executable; reject obvious operational
        // instructions at the separate read-only LLM entrypoint.
        val normalized = java.text.Normalizer.normalize(
            prompt.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return !Regex("(^|[ ,])(?:ejecuta|borra|elimina|instala|autoriza|desactiva|abre ajustes)([ ,.]|$)")
            .containsMatchIn(normalized)
    }
}
