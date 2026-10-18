package com.cardenaspiero255.jarvis.core

import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Optional fixed Vosk grammar from words in the bundled Spanish model.
 * ASR is not speaker identity and grammar matches never grant permissions.
 * Missing/untrusted vocabulary -> unrestricted offline recognition.
 */
class OfflineCommandGrammarPolicy {
    fun fromVocabulary(wordsFile: File): String? {
        if (!wordsFile.isFile || wordsFile.length() == 0L ||
            wordsFile.length() > MAX_WORDS_BYTES ||
            Files.isSymbolicLink(wordsFile.toPath())
        ) return null

        val requiredWords = PHRASES.flatMap { it.split(' ') }.toSet()
        val present = mutableSetOf<String>()
        try {
            wordsFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val token = line.trimStart().substringBefore(' ')
                        .substringBefore('\t').lowercase(Locale.ROOT)
                    if (token in requiredWords) present.add(token)
                }
            }
        } catch (_: Exception) {
            return null
        }
        if ("jarvis" !in present) return null
        val supported = PHRASES.filter { phrase ->
            phrase.split(' ').all { it in present }
        }
        if (supported.size < 2) return null
        return (supported + "[unk]").joinToString(
            prefix = "[", postfix = "]", separator = ","
        ) { "\"$it\"" }
    }

    private companion object {
        const val MAX_WORDS_BYTES = 8_000_000L
        val PHRASES = listOf(
            "jarvis",
            "jarvis dime la hora", "jarvis que hora es",
            "jarvis para", "jarvis detente",
            "dime la hora", "que hora es", "para", "detente"
        )
    }
}
