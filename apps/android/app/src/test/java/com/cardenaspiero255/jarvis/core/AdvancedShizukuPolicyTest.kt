package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class AdvancedShizukuPolicyTest {
    @Test fun processAndServiceProbesAreReadOnlyAllowlisted() {
        assertEquals(listOf("/system/bin/getprop", "init.svc.zygote"),
            ShizukuFixedProbe.ZYGOTE_SERVICE.command)
        assertEquals(listOf("/system/bin/getprop", "init.svc.surfaceflinger"),
            ShizukuFixedProbe.DISPLAY_SERVICE.command)
        assertEquals(listOf("/system/bin/pm", "path", "com.android.settings"),
            ShizukuFixedProbe.SETTINGS_PACKAGE.command)
    }

    @Test fun resultChecksRejectMaliciousOrUnexpectedData() {
        assertTrue(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.ZYGOTE_SERVICE, "running"))
        assertTrue(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.DISPLAY_SERVICE, "stopped"))
        assertTrue(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.SETTINGS_PACKAGE,
            "package:/system/priv-app/Settings/Settings.apk"))
        assertFalse(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.ZYGOTE_SERVICE, "running\nrm -rf"))
        assertFalse(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.SETTINGS_PACKAGE, "package:/data/user/0/secret.apk"))
    }

    @Test fun animationSettingsMustRemainReadOnly() {
        assertEquals(listOf("/system/bin/settings", "get", "global", "window_animation_scale"),
            ShizukuFixedProbe.WINDOW_ANIMATION_SCALE.command)
        assertTrue(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.WINDOW_ANIMATION_SCALE, "1.0"))
        assertFalse(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.WINDOW_ANIMATION_SCALE, "delete"))
    }

    @Test fun voicePreviewNeverRunsAutomatically() {
        assertEquals(SafeRoutine.SHIZUKU_ADVANCED_DIAGNOSTIC,
            SafeAutomationVoiceRouter.parse("Jarvis, revisa servicios Android"))
        assertEquals(SafeRoutine.GAMEHUB_PROFILE_PREVIEW,
            SafeAutomationVoiceRouter.parse("Jarvis, prepara perfil GameHub"))
        assertNull(SafeAutomationVoiceRouter.parse("Jarvis, no revises servicios Android"))
        assertNull(SafeAutomationVoiceRouter.parse("Jarvis, cambia permisos de Shizuku"))
        val g = object : SafeAutomationGateway {
            var count = 0
            override fun perform(step: SafeAutomationStep): Boolean { count++; return true }
        }
        val engine = SafeAutomationEngine(g)
        engine.prepare(SafeRoutine.SHIZUKU_ADVANCED_DIAGNOSTIC)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_ADVANCED_DIAGNOSTIC, false).outcome)
        assertEquals(0, g.count)
    }
}
