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

    @Test fun knownVocabularyIncludesTimeBatteryAndStopButNoActions() {
        val file = writeWords(
            "jarvis", "dime", "la", "hora", "que", "es", "para", "detente",
            "bateria", "cuanta", "tengo", "otra"
        )
        assertEquals(
            listOf(
                "jarvis", "jarvis dime la hora", "jarvis que hora es",
                "jarvis para", "jarvis detente", "dime la hora",
                "que hora es", "para", "detente",
                "jarvis bateria", "jarvis cuanta bateria tengo",
                "jarvis dime la bateria", "bateria",
                "cuanta bateria tengo", "dime la bateria", "[unk]"
            ).joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" },
            OfflineCommandGrammarPolicy().fromVocabulary(file)
        )
    }

    @Test fun oldGrammarMissingBatteryFallsBackToFreeOfflineVosk() {
        val file = writeWords(
            "jarvis", "dime", "la", "hora", "que", "es", "para", "detente"
        )
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(file))
    }

    @Test fun absentWakeWordNeverRestrictsGrammar() {
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(writeWords("dime", "la", "hora")))
    }

    @Test fun unavailableVocabularyNeverRestrictsOrDownloads() {
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(File(folder.root, "absent")))
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(writeWords()))
    }

    @Test fun omitsUnknownPhrasesButKeepsBatteryAndUnknownFallback() {
        val file = writeWords("jarvis", "dime", "la", "hora", "bateria", "unknown")
        assertEquals(
            "[\"jarvis\",\"jarvis dime la hora\",\"dime la hora\",\"jarvis bateria\",\"jarvis dime la bateria\",\"bateria\",\"dime la bateria\",\"[unk]\"]",
            OfflineCommandGrammarPolicy().fromVocabulary(file)
        )
    }

    @Test fun oversizeVocabularyIsNotParsed() {
        val file = writeWords("jarvis")
        java.io.RandomAccessFile(file, "rw").use { it.setLength(8_000_001L) }
        assertNull(OfflineCommandGrammarPolicy().fromVocabulary(file))
    }

    @Test fun noExecutableOrNetworkCommandsInGrammar() {
        val file = writeWords(
            "jarvis", "dime", "la", "hora", "bateria",
            "abre", "shizuku", "shell", "borra"
        )
        val grammar = OfflineCommandGrammarPolicy().fromVocabulary(file)!!
        for (unsafe in listOf("abre", "shizuku", "shell", "borra")) {
            assertFalse(grammar.contains(unsafe))
        }
        assertTrue(grammar.contains("jarvis bateria"))
    }
}
