package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class VivoGgufSessionReceiptPolicyTest {
    @Test fun startOfEachVoiceSessionInvalidatesAllPriorGenerationEvidence() {
        val keys = VivoGgufSessionReceiptPolicy.resetKeys()
        assertTrue(keys.contains("voice_gguf_success_count"))
        assertTrue(keys.contains("voice_gguf_last_latency_ms"))
        assertTrue(keys.contains("voice_gguf_load_ms"))
        assertTrue(keys.contains("voice_gguf_prefill_ms"))
        assertTrue(keys.contains("voice_gguf_decode_ms"))
        assertTrue(keys.contains("voice_gguf_decoded_tokens"))
        assertTrue(keys.contains("voice_gguf_cpu_threads"))
        assertTrue(keys.contains("voice_gguf_cache_hit"))
        assertTrue(keys.contains("tts_started_count"))
        assertTrue(keys.contains("barge_in_question_count"))
        assertFalse(keys.contains("lockscreen_off_samples"))
        assertFalse(keys.contains("mic_revocation_status"))
    }
}
