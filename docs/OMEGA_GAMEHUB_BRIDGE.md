# GameHub Ultra — integración Android sin privilegios

Esta primera conexión permite **abrir GameHub Ultra cuando está instalado**,
mediante un botón visible en JARVIS y el lanzador normal de Android.

- Se usó el `applicationId` real del repositorio GameHub Ultra:
  `com.cardenaspiero255.gamehubultra`.
- El manifiesto solo declara visibilidad de ese paquete (sin
  `QUERY_ALL_PACKAGES`).
- El lanzador comprueba el paquete exacto de la Activity que Android ofrece
  y detiene la operación si la aplicación está ausente o no es accesible.
- No se comparten transcripciones, imágenes, credenciales, datos del usuario
  ni comandos entre aplicaciones.
- No invoca servicios exportados, broadcast intents, Shizuku, ADB,
  permisos de accesibilidad ni tareas de fondo.
- No modifica GameHub Ultra ni sus datos.

**Identidad:** el identificador de paquete es un criterio de descubrimiento,
no una atestación criptográfica de identidad o firma. Antes de integrar IPC o
datos privados es imprescindible definir certificados permitidos y un
protocolo firmado con capacidades, plazos y autenticación mutua.

**Límite:** abrir la Activity de GameHub desde JARVIS es una integración
operativa básica, pero no controla juegos, CPU, GPU, optimización de red o
perfiles de GameHub Ultra. Se requieren pruebas manuales en dispositivo
con la aplicación instalada.
