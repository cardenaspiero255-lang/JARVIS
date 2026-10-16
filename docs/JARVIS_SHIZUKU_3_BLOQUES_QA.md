# JARVIS OS V3 — tres bloques de integración (sin root)

Este PR está apilado sobre #58: **no fusionar a main** hasta que el PR base esté validado y todos los checks del último SHA sean correctos.

## Bloque 1: diagnóstico + ajustes

- Comprobar mediante la API Binder de Shizuku UID y versión del servidor (viene del PR base). UID 2000 indica shell/ADB, no root.
- Botón de diagnóstico de Android: fabricante/modelo, nivel de batería cuando Android lo informa, RAM disponible, señal de poca memoria y escala de animaciones visible si Android permite leerla.
- **Solo lectura**: no cambia escalas, permisos ni procesos. No solicita acceso a datos privados de otras apps.

## Bloque 2: aplicaciones + gaming

- Consulta únicamente apps con actividad launcher visibles para JARVIS (alcance limitado por Android Package Visibility).
- Selección y **doble interacción** para abrir una app elegida por el propietario; no se detiene, instala ni modifica ninguna app.
- Evaluación gaming conservadora a partir de la señal de baja memoria y RAM disponible. No simula mediciones FPS ni optimizaciones privilegiadas.
- No modifica el refresh rate, servicios de red, procesos ni configuraciones de GameHub Ultra; dichas capacidades requieren adaptadores separados y pruebas físicas.

## Bloque 3: voz y autorización

Frases exactas y normalizadas:
- "Jarvis, diagnostica teléfono"
- "Jarvis, revisa mi teléfono"
- "Jarvis, prepara juego"
- "Jarvis, evalúa modo juego"
- "Jarvis, muestra aplicaciones"

Son **vistas previas**, nunca ejecución desde la transcripción. La pantalla de JARVIS exige una pulsación de confirmación para un solo uso. Los resultados son locales y temporales.

## Preparación de integraciones opcionales

1. **Shizuku**: iniciar servicio, aprobar JARVIS, usar PROBAR LECTURA REMOTA; no demostrar capacidades privilegiadas con una simple detección.
2. **Termux**: instalar desde F-Droid o GitHub oficial, sin mezclar fuentes de firma; abrir y completar instalación inicial; habilitar `allow-external-apps=true` en `~/.termux/termux.properties`; conceder a JARVIS `RUN_COMMAND` en Ajustes > Apps > JARVIS > Permisos adicionales. En JARVIS probar solo comando fijo `id` y consultar la respuesta verificada.
3. **Dhizuku**: instalar versión oficial. Requiere configuración Device Owner para compartir esa autorización, con impacto potencial sobre cuentas y perfiles. No activar Device Owner, borrar cuentas o alterar configuración del Vivo principal como parte de este PR.
4. **OwnDroid**: instalar APK oficial y elegir modo compatible (Device Owner, Dhizuku o perfil de trabajo). Las funciones DevicePolicyManager dependen de dicho modo. No enviar política ni bloqueo sin consentimiento puntual, y nunca guardar clave API.

## Pruebas de salida

- `gradle -p apps/android :app:testDebugUnitTest :app:assembleDebug`.
- `python -m unittest discover -s tests -v`.
- Instrumentación Android 14 y pruebas físicas en Vivo V25 Pro, incluyendo controles de privacidad.
- Las señales de lectura no acreditan por sí solas cambios privilegiados ni mejora en FPS.
