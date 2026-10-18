package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Device bug: Android 14 provider rejects hardcoded es-CL with error 12. */
class OfflineSpanishSupportPolicyTest {
    private val policy = OfflineSpanishSupportPolicy()

    @Test fun selectsActuallyInstalledSpanishInsteadOfForcingChile() {
        assertEquals("es-ES", policy.selectInstalled(
            listOf("en-US", "es-ES", "es-MX")
        ))
        assertEquals("es-MX", policy.selectInstalled(listOf("en-US", "es-MX")))
        assertEquals("es-CL", policy.selectInstalled(listOf("es-ES", "es-CL")))
        assertEquals("es-419", policy.selectInstalled(listOf("es-419", "en-US")))
        assertEquals("es", policy.selectInstalled(listOf("es", "de-DE")))
    }

    @Test fun rejectsCloudOnlyNonSpanishAndUndownloadedLanguages() {
        assertNull(policy.selectInstalled(listOf("en-US", "de-DE")))
        assertNull(policy.selectInstalled(emptyList()))
        assertNull(policy.selectInstalled(listOf("es-XX-other")))
        assertEquals(SpanishAvailability.DOWNLOAD_REQUIRED,
            policy.availability(listOf("en-US"), listOf("es-ES", "en-US")))
        assertEquals(SpanishAvailability.NOT_SUPPORTED,
            policy.availability(listOf("en-US"), listOf("en-GB")))
        assertEquals(SpanishAvailability.READY,
            policy.availability(listOf("es-MX"), emptyList()))
    }

    @Test fun rejectsFalsePrefixAndCanonicalizesSimpleBcp47Tags() {
        assertNull(policy.selectInstalled(listOf("esen", "sp", "spanish")))
        assertEquals("es-ES", policy.selectInstalled(listOf("ES_es")))
        assertEquals("es-MX", policy.selectInstalled(listOf("eS-mX")))
    }

    @Test fun noSupportQueryFallsBackToWidelyUsedSpanishOnce() {
        assertEquals("es-ES", policy.unverifiedFallbackLanguage())
        assertEquals("es-MX", policy.fallbackAfterUnsupported("es-ES", setOf("es-ES")))
        assertNull(policy.fallbackAfterUnsupported("es-MX", setOf("es-ES", "es-MX")))
        assertNull(policy.fallbackAfterUnsupported("es-CL", setOf("es-CL", "es-ES", "es-MX")))
    }

    @Test fun unsupportedErrorDiffersFromModelNotDownloaded() {
        assertEquals("LANGUAGE_NOT_SUPPORTED", policy.errorReason(12))
        assertEquals("LANGUAGE_MODEL_NOT_INSTALLED", policy.errorReason(13))
    }

    @Test fun respectsSpanishOnlyAndNeverSuggestsCloudFallback() {
        assertEquals("es-AR", policy.selectInstalled(listOf("en", "es-AR")))
        assertNull(policy.fallbackAfterUnsupported("es-ES",
            setOf("es-ES", "es-MX", "es")))
    }
}
