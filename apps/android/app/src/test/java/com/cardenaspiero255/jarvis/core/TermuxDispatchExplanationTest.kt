package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxDispatchExplanationTest {
    @Test fun missingPermissionExplainsHowToGrantWithoutClaimingExecution() {
        val txt = TermuxDispatchExplanation.describe(TermuxDispatchStatus.MISSING_PERMISSION)
        assertTrue(txt.contains("Permisos adicionales"))
        assertTrue(txt.contains("RUN_COMMAND"))
        assertTrue(TermuxDispatchExplanation.shouldOfferAppSettings(TermuxDispatchStatus.MISSING_PERMISSION))
    }

    @Test fun backgroundRestrictionDistinctFromPermissionFailure() {
        val txt = TermuxDispatchExplanation.describe(TermuxDispatchStatus.ANDROID_RESTRICTED)
        assertTrue(txt.contains("Android"))
        assertTrue(txt.contains("Termux"))
        assertFalse(TermuxDispatchExplanation.shouldOfferAppSettings(TermuxDispatchStatus.ANDROID_RESTRICTED))
    }

    @Test fun noServiceDoesNotClaimCommandRan() {
        val txt = TermuxDispatchExplanation.describe(TermuxDispatchStatus.SERVICE_UNAVAILABLE)
        assertTrue(txt.contains("servicio"))
        assertFalse(txt.contains("ejecutado correctamente"))
    }

    @Test fun dispatchSuccessStillAwaitsVerifiedCallback() {
        val txt = TermuxDispatchExplanation.describe(TermuxDispatchStatus.REQUEST_SENT)
        assertTrue(txt.contains("pendiente"))
        assertFalse(txt.contains("ejecutado correctamente"))
    }
}
