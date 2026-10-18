package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineCommandGrammarPolicyTest {
    @get:Rule val folder = TemporaryFolder()

    private fun writeWords(vararg words: String): File {
        val f = folder.newFile("words.txt")
        f.writeText(words.mapIndexed { i, word -> "$word $i" }.joinToString("\n"))
        return f
    }

    @Test fun acceptedFixedWordsYieldOnlyReadOnlyClockAndSafeStop() {
        val file = writeWords(
            "jarvis", "dime", "la", "hora", "que", "es", "para", "detente", "otra"
        )
        assertEquals(
            "[\"jarvis\",\"jarvis dime la hora\",\"jarvis que hora es\",\"jarvis para\",\"jarvis detente\",\"dime la hora\",\"que hora es\",\"para\",\"detente\",\"[unk]\"]",
            OfflineCommandGrammarPolicy().fromVocabulary(file)
        )
    }

    @Test fun absentWakeWordNeverRestrictsGrammar() {
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(writeWords("dime", "la", "hora")))
    }

    @Test fun unavailableVocabularyNeverRestrictsOrDownloads() {
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(File(folder.root, "absent")))
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(writeWords()))
    }

    @Test fun omitsUnsupportedPhrasesAndAddsUnknownFallback() {
        val file = writeWords("jarvis", "dime", "la", "hora", "unknown")
        assertEquals(
            "[\"jarvis\",\"jarvis dime la hora\",\"dime la hora\",\"[unk]\"]",
            OfflineCommandGrammarPolicy().fromVocabulary(file)
        )
    }

    @Test fun oversizeVocabularyIsNotParsed() {
        val file = writeWords("jarvis")
        java.io.RandomAccessFile(file, "rw").use { it.setLength(8_000_001L) }
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(file))
    }

    @Test fun noExecutableOrNetworkCommandsInGrammar() {
        val file = writeWords("jarvis", "dime", "la", "hora", "abre", "shizuku", "shell", "borra")
        val grammar = OfflineCommandGrammarPolicy().fromVocabulary(file)!!
        for (unsafe in listOf("abre", "shizuku", "shell", "borra")) {
            assertFalse(grammar.contains(unsafe))
        }
    }
}
