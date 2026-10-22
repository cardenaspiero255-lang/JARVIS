package com.cardenaspiero255.jarvis.core

/**
 * A non-empty local answer is measured text, NOT semantic accuracy or
 * verification of who spoke. Model presence is only a UI hint; the inference
 * engine still validates a private SHA-256 immediately before generation.
 */
object VivoGgufProbePolicy {
    fun classify(modelHint: Boolean, response: String, elapsedMs: Long): String {
        if (!modelHint) return "GGUF_NO_IMPORTADO"
        if (elapsedMs <= 0L || OfflineVoiceAnswerPolicy.forTts(response) == null)
            return "ERROR_INFERENCIA"
        return "TEXTO_NO_VACIO_NO_CERTIFICADO"
    }
}
