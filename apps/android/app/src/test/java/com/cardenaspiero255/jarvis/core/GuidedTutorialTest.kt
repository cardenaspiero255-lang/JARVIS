package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Contract first: tutorials are read-only, user-operated, bounded navigation. */
class GuidedTutorialTest {
    @Test fun everyRequestedTopicHasCompleteOfflineSteps() {
        assertEquals(5, TutorialTopic.values().size)
        for (topic in TutorialTopic.values()) {
            val guide = GuidedTutorialCatalog.get(topic)
            assertTrue(guide.title.isNotBlank())
            assertTrue(guide.steps.size in 2..8)
            for (step in guide.steps) {
                assertTrue(step.title.isNotBlank())
                assertTrue(step.instruction.isNotBlank())
                assertTrue(step.expectedResult.isNotBlank())
            }
        }
    }

    @Test fun navigationStartsOnFirstStepAndStopsAtBounds() {
        val session = GuidedTutorialSession(TutorialTopic.OFFLINE_VOICE)
        assertEquals(0, session.position)
        assertFalse(session.previous())
        assertEquals(0, session.position)
        val count = session.guide.steps.size
        repeat(count - 1) { assertTrue(session.next()) }
        assertEquals(count - 1, session.position)
        assertFalse(session.next())
        assertEquals(count - 1, session.position)
        assertEquals("$count / $count", session.progress())
        assertTrue(session.previous())
        assertEquals(count - 2, session.position)
    }

    @Test fun changingTopicsResetsPositionWithoutPerformingActions() {
        val session = GuidedTutorialSession(TutorialTopic.SHIZUKU)
        assertTrue(session.next())
        assertTrue(session.position > 0)
        session.select(TutorialTopic.TERMUX)
        assertEquals(TutorialTopic.TERMUX, session.topic)
        assertEquals(0, session.position)
        assertEquals("1 / ${session.guide.steps.size}", session.progress())
    }

    @Test fun guidesDiscloseImportantSecurityLimitations() {
        val voice = GuidedTutorialCatalog.get(TutorialTopic.OWNER_VOICE)
            .steps.joinToString(" ") { it.instruction + " " + it.expectedResult }.lowercase()
        assertTrue(voice.contains("identidad"))
        assertTrue(voice.contains("no autoriza"))
        val system = GuidedTutorialCatalog.get(TutorialTopic.SHIZUKU)
            .steps.joinToString(" ") { it.instruction + " " + it.expectedResult }.lowercase()
        assertTrue(system.contains("permiso"))
        assertTrue(system.contains("no ejecuta"))
    }

    @Test fun tutorialsAreGuidanceAndNeverProduceExecutableCommands() {
        for (topic in TutorialTopic.values()) {
            assertFalse(GuidedTutorialCatalog.get(topic).canExecuteDeviceActions)
        }
    }

    @Test fun resetDoesNotAffectAnotherSession() {
        val a = GuidedTutorialSession(TutorialTopic.TERMUX)
        val b = GuidedTutorialSession(TutorialTopic.TERMUX)
        assertTrue(a.next())
        a.reset()
        assertEquals(0, a.position)
        assertEquals(0, b.position)
    }
}
