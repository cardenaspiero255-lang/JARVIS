# Omega Privacy Lock — bloqueo de sesión por pulsación

**BLOQUEAR SESIÓN — PRIVACIDAD** activa una operación local visible que intenta detener o invalidar los siguientes módulos, incluso si alguno falla: solicitudes por voz, síntesis TTS, trabajo OCR pendiente, Omega Hands (parada de emergencia), acceso a memoria privada cifrada y conversación de la sesión en pantalla.

Un coordinador Kotlin invoca cada cierre una vez y **notifica fallos** en vez de afirmar éxito si algún módulo no responde. La revocación de Hands permanece aplicada hasta que el usuario vuelva a autorizar su uso; TTS y memoria privada requieren una nueva activación visible.

**No borra el historial cifrado guardado**: eso requiere el botón separado BORRAR HISTORIAL Y CLAVE CIFRADA. Tampoco apaga el micrófono de servicios externos a JARVIS ni revoca permisos Android del sistema. No hay privilegios especiales, nuevas redes o registros.

**Límite explícito:** el PR #16 de MediaProjection todavía no está fusionado en `main`. Este botón no detiene una futura sesión de MediaProjection hasta conectar su servicio; esa integración y su prueba física son un bloqueo obligatorio de ese PR. No presentar este botón como parada global de funciones aún no incluidas.

**Pruebas:** JUnit cubre orden, acciones faltantes, fallos de un módulo e idempotencia. Contratos Python comprueban la presencia de todos los cierres en la UI y ausencia de borrado implícito. CI compila la APK, pero no demuestra interrupción perceptible de voz/OCR en teléfono físico.


## Redacción de diagnósticos visibles (PR #35)

Al pulsar **BLOQUEAR SESIÓN — PRIVACIDAD**, JARVIS también reemplaza el contenido visible de los paneles de autodiagnóstico, batería/RAM/térmicas y Game Readiness por etiquetas neutras. El canal **LOCAL_DIAGNOSTICS** es independiente: si falla su limpieza se muestra el bloqueo parcial, sin impedir los otros pasos. Las etiquetas también se sustituyen cuando MainActivity pasa a segundo plano para no conservar lecturas antiguas en la vista al volver.

Esta medida elimina los informes **de los TextView de la aplicación**; no equivale a borrar capturas del sistema, backups, logs ajenos, el historial cifrado (que requiere su control independiente) ni a cerrar todos los servicios Android. Se deben seguir verificando recents/snapshots, lifecycle y privacidad física en dispositivos reales. No se añaden permisos ni telemetría.
