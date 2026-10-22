package com.cardenaspiero255.jarvis.core

import android.content.Context

/**
 * Model-related QA evidence lives outside the Activity lifecycle: imports
 * can finish after a picker Activity is destroyed and its callback suppressed.
 * Invalidate *before* changing verified weights, under the model store lock.
 * A failed valid-file replacement may conservatively clear old QA results.
 */
object VivoGgufEvidenceResetPlan {
    private const val PREFS = "jarvis_vivo_one_install_diag_v1"

    fun changesForModelMutation(): Map<String, String?> = linkedMapOf(
        "qwen" to "NO_PROBADO",
        "qwen_ms" to null,
        "QWEN" to "NO_PROBADO",
        "BARGE_IN" to "NO_PROBADO",
        "ECHO_REJECTION" to "NO_PROBADO"
    )

    fun invalidate(context: Context): Boolean {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for ((key, value) in changesForModelMutation()) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        // A new model must not inherit a voice answer from old weights.
        val conversation = context.getSharedPreferences(
            "jarvis_handsfree_diag_v1", Context.MODE_PRIVATE).edit()
            .remove("voice_gguf_success_count").remove("voice_gguf_last_latency_ms")
            .remove("barge_in_question_count").remove("tts_started_count")
        return editor.commit() && conversation.commit()
    }
}
