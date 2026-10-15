package com.cardenaspiero255.jarvis.core

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/**
 * Narrow on-device Spanish dialogue. It never interprets text as a tool
 * instruction and never claims network research or general LLM reasoning.
 */
class LocalDialogue {
    private val turns = mutableListOf<String>()
    private var lastAnswer: String? = null
    private var lastNumericResult: BigDecimal? = null
    private val topicDialogue = LocalTopicDialogue()
    private val knowledgeEngine = OfflineKnowledgeEngine()

    fun reply(input: String): String {
        // Reject rather than silently truncate an expression or mixed instruction.
        // Rejected inputs are not persisted or eligible for voice readout.
        if (input.length > MAX_INPUT_LENGTH) {
            return "Mensaje demasiado largo: escribe hasta 280 caracteres para JARVIS."
        }
        val sanitized = input.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val normalized = Normalizer.normalize(sanitized.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val math = if (normalized == "repitelo" || normalized == "repite") null
            else OfflineArithmetic.evaluate(sanitized, lastNumericResult)
        val offlineHelp = VoiceFaqRouter.answer(sanitized)
        if (offlineHelp != null) {
            knowledgeEngine.clear()
            topicDialogue.observeKnownFaq(sanitized)
        }
        val knowledgeHelp = if (math == null && offlineHelp == null)
            knowledgeEngine.answer(sanitized) else null
        if (knowledgeHelp != null && knowledgeEngine.recognizesNewTopic(sanitized))
            topicDialogue.clear()
        val topicHelp = if (math == null && offlineHelp == null && knowledgeHelp == null)
            topicDialogue.answer(sanitized) else null
        if (topicHelp != null) knowledgeEngine.clear()
        val answer = when {
            sanitized.isBlank() -> "Escribe una pregunta para conversar."
            normalized == "repitelo" || normalized == "repite" ->
                lastAnswer ?: "No hay una respuesta anterior todavía."
            math != null -> {
                if (math.value != null) lastNumericResult = math.value
                math.answer
            }
            offlineHelp != null -> offlineHelp
            knowledgeHelp != null -> knowledgeHelp
            topicHelp != null -> topicHelp
            normalized == "buenas" ->
                "Hola. Soy JARVIS en modo observador. Puedo explicar mis funciones locales."
            normalized in setOf("estado", "jarvis estado", "estado jarvis") ->
                "JARVIS funciona en modo observador. Las acciones sensibles necesitan permiso visible."
            else ->
                "Todavía no tengo una respuesta local verificada sobre eso. " +
                    "Puedo explicar ciencia, tecnología y juegos sin internet."
        }.take(300)
        turns.add("Tú: $sanitized".take(400))
        turns.add("JARVIS: $answer".take(400))
        while (turns.size > 16) turns.removeAt(0)
        lastAnswer = answer
        return answer
    }

    /**
     * A locally generated reply is only chat text. It never triggers a device
     * action. Keep private-memory rows bounded and prevent multiline injection.
     */
    fun recordGeneratedReply(input: String, generated: String) {
        if (!LocalLlmPolicy.isValidPrompt(input)) return
        val answer = LocalLlmPolicy.cleanAnswer(generated) ?: return
        fun oneLine(text: String): String = text.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        turns.add("Tú: ${oneLine(input)}".take(400))
        turns.add("JARVIS: $answer".take(400))
        while (turns.size > 16) turns.removeAt(0)
        lastAnswer = answer
    }

    /** Voice stays ephemeral: only a known topic enum is updated, never the transcript. */
    fun replyVoice(input: String): String? {
        if (input.length > 160) return null
        val faq = VoiceFaqRouter.answer(input)
        if (faq != null) {
            knowledgeEngine.clear()
            topicDialogue.observeKnownFaq(input)
            return faq
        }
        val localKnowledge = knowledgeEngine.answer(input)
        if (localKnowledge != null) {
            if (knowledgeEngine.recognizesNewTopic(input)) topicDialogue.clear()
            return localKnowledge
        }
        val localTopic = topicDialogue.answer(input)
        if (localTopic != null) knowledgeEngine.clear()
        return localTopic
    }

    fun history(): List<String> = turns.toList()

    /**
     * Intended ONLY for the existing user-enabled AES-GCM store. The microphone
     * transcript is never included: speech contributes at most a known topic ID.
     * Keep ciphertext source below the store's 8192-byte plaintext limit.
     */
    fun snapshotForStorage(): String {
        val lines = turns.takeLast(24).toMutableList()
        val context = topicDialogue.contextToken()
        if (context != null) lines.add("Contexto: $context")
        val knowledgeContext = knowledgeEngine.contextToken()
        if (knowledgeContext != null) lines.add("Conocimiento: $knowledgeContext")
        while (lines.isNotEmpty() &&
            lines.joinToString("\n").toByteArray(Charsets.UTF_8).size > 7500) {
            lines.removeAt(0)
        }
        return lines.joinToString("\n")
    }

    /**
     * Restore only local dialogue rows from authenticated encrypted storage.
     * Stored text is data and never dispatched as an Android instruction.
     */
    fun restore(plainHistory: String) {
        clear()
        for (line in plainHistory.lineSequence().toList().takeLast(48)) {
            val bounded = line.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").take(400)
            if (bounded.startsWith("Contexto: ")) {
                topicDialogue.restoreContextToken(bounded.removePrefix("Contexto: "))
            } else if (bounded.startsWith("Conocimiento: ")) {
                knowledgeEngine.restoreContextToken(bounded.removePrefix("Conocimiento: "))
            } else if (bounded.startsWith("Tú: ") || bounded.startsWith("JARVIS: ")) {
                turns.add(bounded)
                if (bounded.startsWith("Tú: ")) {
                    val typed = bounded.removePrefix("Tú: ")
                    topicDialogue.restoreTopicHint(typed)
                    knowledgeEngine.restoreTopicHint(typed)
                }
            }
        }
        while (turns.size > 24) turns.removeAt(0)
        lastAnswer = turns.lastOrNull { it.startsWith("JARVIS: ") }
            ?.removePrefix("JARVIS: ")
    }

    fun clear() {
        turns.clear()
        lastAnswer = null
        lastNumericResult = null
        topicDialogue.clear()
        knowledgeEngine.clear()
    }
    private companion object {
        const val MAX_INPUT_LENGTH = 280
    }

}
