# Omega Privacy Lock — bloqueo de sesión por pulsación

**BLOQUEAR SESIÓN — PRIVACIDAD** activa una operación local visible que intenta detener o invalidar los siguientes módulos, incluso si alguno falla: solicitudes por voz, síntesis TTS, trabajo OCR pendiente, Omega Hands (parada de emergencia), acceso a memoria privada cifrada y conversación de la sesión en pantalla.

Un coordinador Kotlin invoca cada cierre una vez y **notifica fallos** en vez de afirmar éxito si algún módulo no responde. La revocación de Hands permanece aplicada hasta que el usuario vuelva a autorizar su uso; TTS y memoria privada requieren una nueva activación visible.

**No borra el historial cifrado guardado**: eso requiere el botón separado BORRAR HISTORIAL Y CLAVE CIFRADA. Tampoco apaga el micrófono de servicios externos a JARVIS ni revoca permisos Android del sistema. No hay privilegios especiales, nuevas redes o registros.

**Límite explícito:** el PR #16 de MediaProjection fue cerrado sin fusionar. La integración de MediaProjection se incorpora en el PR #27; antes de fusionarse requiere CI y ensayos Android 14+. Hasta entonces la APK de `main` no incluye captura continua. No presentar este botón como parada global de funciones aún no fusionadas.

**Pruebas:** JUnit cubre orden, acciones faltantes, fallos de un módulo e idempotencia. Contratos Python comprueban la presencia de todos los cierres en la UI y ausencia de borrado implícito. CI compila la APK, pero no demuestra interrupción perceptible de voz/OCR en teléfono físico.


## Redacción de diagnósticos visibles (PR #35)

Al pulsar **BLOQUEAR SESIÓN — PRIVACIDAD**, JARVIS también reemplaza el contenido visible de los paneles de autodiagnóstico, batería/RAM/térmicas y Game Readiness por etiquetas neutras. El canal **LOCAL_DIAGNOSTICS** es independiente: si falla su limpieza se muestra el bloqueo parcial, sin impedir los otros pasos. Las etiquetas también se sustituyen cuando MainActivity pasa a segundo plano para no conservar lecturas antiguas en la vista al volver.

Esta medida elimina los informes **de los TextView de la aplicación**; no equivale a borrar capturas del sistema, backups, logs ajenos, el historial cifrado (que requiere su control independiente) ni a cerrar todos los servicios Android. Se deben seguir verificando recents/snapshots, lifecycle y privacidad física en dispositivos reales. No se añaden permisos ni telemetría.


## Protección de pantalla y vista de aplicaciones recientes (PR #36)

Desde la creación de MainActivity, JARVIS activa `FLAG_SECURE` de Android. Esto solicita al sistema impedir capturas de la ventana y su representación en superficies no seguras, incluyendo normalmente la miniatura de aplicaciones recientes. Es una barrera proporcionada por Android; no impide fotografías realizadas con otra cámara ni permite afirmar cobertura idéntica en todos los fabricantes.

Para facilitar depuración, el botón **PERMITIR CAPTURAS 60 S — DEPURACIÓN** permite capturas temporalmente mediante un toque visible. Durante esa ventana cualquier mecanismo de captura permitido por el sistema podría registrar lo que JARVIS muestre, incluyendo texto de chat. Una segunda pulsación no extiende un permiso activo. Se vuelve a establecer el modo seguro automáticamente mediante temporizador, y de manera inmediata al pulsar **PROTEGER PANTALLA AHORA**, **BLOQUEAR SESIÓN — PRIVACIDAD**, cerrar o mandar la Activity a segundo plano. El estado no se guarda ni se restaura.

El nuevo canal `SCREEN_CAPTURE` se integra con `SessionLockCoordinator`: la protección se restablece incluso si otro cierre falla. No añade permisos, servicios, red ni almacenamiento de imágenes.

**Limitaciones:** FLAG_SECURE puede restringir también la captura consentida de la propia ventana JARVIS, relevante para pruebas futuras de MediaProjection PR #27. Ese PR no debe eliminar la protección por defecto ni fusionarse sin verificar físicamente esta interacción. Las pruebas CI acreditan la política y su integración de código; Android real aún requiere ensayos de captura, recientes y ciclo de vida.

## Omega Vision: detención de proyección (PR #27)

El canal `SCREEN_PROJECTION` detiene cualquier captura activa y revoca los inicios
pendientes del servicio MediaProjection. El consentimiento del sistema Android
se requiere para cada sesión. Nunca se modifica automáticamente `FLAG_SECURE`
ni los canales `LOCAL_DIAGNOSTICS`, `GAMEHUB_PREFLIGHT` o `SCREEN_CAPTURE`.
Esta integración requiere ensayos Android 14+ de consentimiento, rotación,
notificaciones y revocación antes de certificación física.
