package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStyleSelectorTest {
    @Test fun spanishChoosesChileanOfflineVoiceBeforeGenericSpanish() {
        val installed = listOf(
            InstalledVoice("es-es-offline", "es-ES", false),
            InstalledVoice("chile-online", "es-CL", true),
            InstalledVoice("chile-offline", "es-CL", false),
        )
        val selected = VoiceStyleSelector.select(VoiceStyle.SPANISH, installed)
        assertEquals("chile-offline", selected?.name)
        assertFalse(selected!!.requiresNetwork)
    }

    @Test fun britishVoiceMustBeEnglishGreatBritainAndInstalledOffline() {
        val installed = listOf(
            InstalledVoice("usa-voice", "en-US", false),
            InstalledVoice("uk-online", "en-GB", true),
            InstalledVoice("british-installed", "en-GB", false),
        )
        assertEquals("british-installed",
            VoiceStyleSelector.select(VoiceStyle.BRITISH_ASSISTANT, installed)?.name)
        assertNull(VoiceStyleSelector.select(VoiceStyle.BRITISH_ASSISTANT,
            installed.filter { it.requiresNetwork || it.localeTag == "en-US" }))
    }

    @Test fun britishProfilePrefersOfflineMaleCharacterWithoutChoosingFemaleLabel() {
        val available = listOf(
            InstalledVoice("en-gb-female-1", "en-GB", false),
            InstalledVoice("en-gb-neutral-1", "en-GB", false),
            InstalledVoice("en-gb-male-1", "en-GB", false),
            InstalledVoice("en-gb-male-online", "en-GB", true),
        )
        val ranked = VoiceStyleSelector.candidates(
            VoiceStyle.BRITISH_ASSISTANT, available).map { it.name }
        assertEquals("en-gb-male-1", ranked.first())
        assertEquals("en-gb-neutral-1", ranked[1])
        assertEquals("en-gb-female-1", ranked[2])
        assertFalse(ranked.contains("en-gb-male-online"))
    }

    @Test fun britishCadenceIsCalmAndLowWithoutUnnaturalPitch() {
        assertTrue(VoiceStyleSelector.rate(VoiceStyle.BRITISH_ASSISTANT) in 0.84f..0.94f)
        assertTrue(VoiceStyleSelector.pitch(VoiceStyle.BRITISH_ASSISTANT) in 0.84f..0.94f)
    }

    @Test fun noOfflineVoiceNeverFallsBackToCloudTts() {
        assertNull(VoiceStyleSelector.select(VoiceStyle.SPANISH,
            listOf(InstalledVoice("online-only", "es-CL", true))))
        assertNull(VoiceStyleSelector.select(VoiceStyle.BRITISH_ASSISTANT, emptyList()))
    }

    @Test fun profileRateAndPitchAreBoundedAndNeverRequireSpeechService() {
        for (style in VoiceStyle.values()) {
            assertTrue(VoiceStyleSelector.rate(style) in 0.8f..1.2f)
            assertTrue(VoiceStyleSelector.pitch(style) in 0.8f..1.2f)
        }
        assertTrue(VoiceStyleSelector.rate(VoiceStyle.BRITISH_ASSISTANT) < 1f)
        assertTrue(VoiceStyleSelector.pitch(VoiceStyle.BRITISH_ASSISTANT) < 1f)
    }

    @Test fun messageWhitelistsAreLanguageSpecificButKeepTheSameSafetyConstraints() {
        val policy = SpokenReplyPolicy()
        assertEquals("JARVIS está disponible en modo observador. Las funciones de control remoto están desactivadas.",
            policy.safeText(SpokenMessage.LOCAL_STATUS, VoiceStyle.SPANISH))
        val british = policy.safeText(SpokenMessage.LOCAL_STATUS, VoiceStyle.BRITISH_ASSISTANT)
        assertTrue(british.contains("observer mode"))
        assertTrue(british.contains("Remote control is disabled"))
        assertFalse(british.contains("approved for merge"))
        assertNotNull(policy.safeText(SpokenMessage.PRIVACY, VoiceStyle.BRITISH_ASSISTANT))
        assertTrue(SpeechSessionState().stopped)
    }
    @Test fun fallbackCandidatesAreOrderedAndNeverIncludeOnlineVoices() {
        val installed = listOf(
            InstalledVoice("cloud-es-cl", "es-CL", true),
            InstalledVoice("offline-es-mx", "es-MX", false),
            InstalledVoice("offline-es-cl", "es-CL", false),
            InstalledVoice("offline-es-es", "es-ES", false),
            InstalledVoice("cloud-es-es", "es-ES", true),
        )
        val choices = VoiceStyleSelector.candidates(VoiceStyle.SPANISH, installed)
        assertEquals(listOf("offline-es-cl", "offline-es-es", "offline-es-mx"),
            choices.map { it.name })
        assertTrue(choices.none { it.requiresNetwork })
        assertNull(VoiceStyleSelector.select(VoiceStyle.BRITISH_ASSISTANT, installed))
    }


}
