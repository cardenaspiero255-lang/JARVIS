package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/** Pinned, licensed offline baseline. No network, inference or API keys here. */
object LocalLlmPolicy {
    const val MODEL_NAME = "Qwen2.5-0.5B-Instruct-Q4_K_M"
    const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    const val SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    const val MAX_BYTES = 1_000_000_000L
    const val MAX_INPUT_CHARS = 280

    fun isEligibleLength(length: Long): Boolean = length in 1..MAX_BYTES
    fun isValidPrompt(input: String): Boolean =
        input.length <= MAX_INPUT_CHARS && input.isNotBlank()

    /** Never regress deterministic arithmetic, repeat or explicit app status. */
    fun shouldUseGenerative(input: String): Boolean {
        if (!isValidPrompt(input)) return false
        val normalized = Normalizer.normalize(
            input.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
        if (normalized in setOf(
                "repite", "repitelo", "buenas", "estado", "jarvis estado", "estado jarvis"
            )) return false
        if (normalized.contains("cuanto es") || normalized.startsWith("calcula ")) return false
        if (Regex(".*\\d+.*[x×+*/-]\\s*\\d+.*").matches(normalized)) return false
        return true
    }

    fun cleanAnswer(answer: String): String? {
        val cleaned = answer.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ").trim().take(300)
        return cleaned.ifBlank { null }
    }
}
