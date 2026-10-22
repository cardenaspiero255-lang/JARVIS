package com.cardenaspiero255.jarvis.core

/**
 * Diagnostic receipts are attached to ONE explicit voice-conversation session.
 * Never let model completions from a previous STOP/session corroborate a new one.
 * This is privacy-safe: only timings/counters, no prompts, output or audio.
 */
object VivoGgufSessionReceiptPolicy {
    fun resetKeys(): List<String> = listOf(
        "voice_gguf_success_count",
        "voice_gguf_last_latency_ms",
        "voice_gguf_load_ms",
        "voice_gguf_prefill_ms",
        "voice_gguf_decode_ms",
        "voice_gguf_decoded_tokens",
        "voice_gguf_cpu_threads",
        "voice_gguf_cache_hit",
        "tts_started_count",
        "barge_in_question_count"
    )
}
