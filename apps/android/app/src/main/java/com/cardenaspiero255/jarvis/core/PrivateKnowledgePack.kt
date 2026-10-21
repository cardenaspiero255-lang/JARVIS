package com.cardenaspiero255.jarvis.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

/**
 * Offline BM25 adaptation inspired by the MIT-licensed KnowledgeBase.kt from
 * https://github.com/alwin123098/jarvis-android (Copyright 2026 Codotype).
 * This version is bounded, Spanish-aware and ephemeral. See bundled MIT notice.
 *
 * Text is untrusted DATA. No document content becomes an instruction, tool
 * invocation, voice transcript, automation or owner authorization.
 */
class PrivateKnowledgePack {
    private data class Passage(val original: String, val terms: List<String>)
    private var passages: List<Passage> = emptyList()
    private var frequencies: Map<String, Int> = emptyMap()
    private var averageLength = 1.0

    companion object {
        const val MAX_NOTE_BYTES = 65_536
        private const val MAX_CHUNKS = 128
        private const val CHUNK_LENGTH = 500
        private const val CHUNK_OVERLAP = 50
        private val ignored = setOf(
            "de", "del", "el", "la", "las", "los", "lo", "le", "les", "y", "o",
            "en", "un", "una", "uno", "que", "como", "cuando", "donde",
            "con", "sin", "por", "para", "sobre", "se", "es", "son", "sus",
            "su", "mi", "mis", "tu", "tus", "al", "a", "me", "cual",
            "nota", "notas", "busca", "consulta", "quiero", "saber"
        )

        /** Never read beyond the 64 KiB bound or accept malformed UTF-8. */
        fun readText(input: InputStream, isActive: () -> Boolean = { true }): String {
            val buffer = ByteArray(4096)
            val out = ByteArrayOutputStream()
            try {
                input.use { source ->
                    while (true) {
                        check(isActive() && !Thread.currentThread().isInterrupted) {
                            "Importación de nota revocada"
                        }
                        val n = source.read(buffer)
                        if (n == -1) break
                        require(n > 0 && out.size() + n <= MAX_NOTE_BYTES) {
                            "Nota demasiado grande o flujo sin progreso"
                        }
                        out.write(buffer, 0, n)
                    }
                }
                check(isActive() && !Thread.currentThread().isInterrupted) {
                    "Importación de nota revocada"
                }
                return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString()
            } finally {
                buffer.fill(0)
                out.reset()
            }
        }
    }

    fun importText(text: String): Int {
        require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= MAX_NOTE_BYTES &&
            text.none { it == '\u0000' || (Character.isISOControl(it) &&
                it != '\n' && it != '\r' && it != '\t') }) { "Nota inválida o demasiado larga" }
        val loaded = ArrayList<Passage>()
        val paragraphs = text.split(Regex("""\n\s*\n"""))
        for (paragraph in paragraphs) {
            val cleaned = paragraph.trim().replace(Regex("""[ \t]+"""), " ")
            if (cleaned.length < 20) continue
            var start = 0
            while (start < cleaned.length) {
                val end = (start + CHUNK_LENGTH).coerceAtMost(cleaned.length)
                val snippet = cleaned.substring(start, end)
                val tokens = tokenize(snippet)
                if (tokens.isNotEmpty()) {
                    require(loaded.size < MAX_CHUNKS) { "Exceso de pasajes locales" }
                    loaded.add(Passage(snippet, tokens))
                }
                if (end == cleaned.length) break
                start = end - CHUNK_OVERLAP
            }
        }
        require(loaded.isNotEmpty()) { "Sin texto recuperable" }
        val docs = HashMap<String, Int>()
        for (p in loaded) for (word in p.terms.toSet()) {
            docs[word] = (docs[word] ?: 0) + 1
        }
        passages = loaded
        frequencies = docs
        averageLength = loaded.sumOf { it.terms.size }.toDouble() / loaded.size
        return loaded.size
    }

    fun search(query: String): String? {
        if (passages.isEmpty() || query.isBlank() || query.length > 160) return null
        val words = tokenize(query).distinct().take(20)
        if (words.isEmpty()) return null
        val n = passages.size
        var bestScore = 0.0
        var best: Passage? = null
        for (p in passages) {
            val tf = p.terms.groupingBy { it }.eachCount()
            var score = 0.0
            for (word in words) {
                val count = tf[word] ?: continue
                val df = frequencies[word] ?: continue
                val idf = ln(1.0 + (n - df + 0.5) / (df + 0.5))
                val lengthScale = 1.5 * (0.25 + 0.75 * p.terms.size / averageLength)
                score += idf * count * 2.5 / (count + lengthScale)
            }
            if (score > bestScore) { best = p; bestScore = score }
        }
        // Return only a literal excerpt, explicitly tagged as UNVERIFIED data.
        return best?.let {
            "Dato de una nota local, no verificado: " +
                it.original.replace(Regex("""[\p{Cc}\p{Cf}]+"""), " ").take(235)
        }
    }

    fun clear() {
        passages = emptyList()
        frequencies = emptyMap()
        averageLength = 1.0
    }

    private fun tokenize(text: String): List<String> =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 2 && it !in ignored }
}
