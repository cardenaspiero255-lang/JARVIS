package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VivoGgufProbePolicyTest {
    @Test fun modelHintMissingCannotBecomeGeneratedText() {
        assertEquals("GGUF_NO_IMPORTADO",
            VivoGgufProbePolicy.classify(
                modelHint = false, response = "Una respuesta falsa",
                elapsedMs = 120L))
    }

    @Test fun nativeErrorsNeverBecomeValidGeneration() {
        for (reply in listOf(
            "", "Modelo GGUF no instalado, incompatible o alterado.",
            "Motor nativo de inferencia no disponible en esta arquitectura.",
            "La inferencia local falló. No se ejecutó ninguna acción.",
            "El motor nativo no pudo cargar el modelo.",
            "No se generó una respuesta local.",
            "No se pudo cargar el modelo GGUF.",
            "Memoria insuficiente para el contexto GGUF.",
            "La pregunta supera el contexto local máximo.",
            "Error de tokenización del modelo.",
            "Inferencia local cancelada o fallida.",
            "La generación local se interrumpió.",
            "     "
        )) {
            assertEquals(reply, "ERROR_INFERENCIA",
                VivoGgufProbePolicy.classify(true, reply, 250L))
        }
    }

    @Test fun cancelledOrZeroLatencyMustNotCountAsVerifiedRun() {
        assertEquals("ERROR_INFERENCIA",
            VivoGgufProbePolicy.classify(true, "Respuesta genuina", 0L))
    }

    @Test fun generatedAnswerIsOnlyTextEvidenceNotSemanticCertification() {
        assertEquals("TEXTO_NO_VACIO_NO_CERTIFICADO",
            VivoGgufProbePolicy.classify(true, "El procesador ejecuta instrucciones.", 621L))
    }
}
