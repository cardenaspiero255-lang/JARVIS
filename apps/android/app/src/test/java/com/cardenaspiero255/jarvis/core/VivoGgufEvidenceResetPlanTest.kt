package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VivoGgufEvidenceResetPlanTest {
    @Test fun aReplacementInvalidatesOnlyModelDependentSuccesses() {
        val fields = VivoGgufEvidenceResetPlan.changesForModelMutation()
        assertEquals("NO_PROBADO", fields["qwen"])
        assertEquals(null, fields["qwen_ms"])
        assertEquals("NO_PROBADO", fields["QWEN"])
        assertEquals("NO_PROBADO", fields["BARGE_IN"])
        assertEquals("NO_PROBADO", fields["ECHO_REJECTION"])
        assertFalse(fields.containsKey("stop_status"))
        assertFalse(fields.containsKey("MIC_REVOKED"))
        assertFalse(fields.containsKey("OWNER"))
    }
}
