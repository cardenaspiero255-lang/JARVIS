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
}
