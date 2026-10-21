# JARVIS — laboratorio físico Vivo V25 Pro (una sola instalación)

## Alcance
Compilar e instalar **una** APK Android de depuración aislada del JARVIS normal (`com.cardenaspiero255.jarvis.handsfreepreview`). La interfaz incluye el botón **DIAGNÓSTICO VIVO V25 PRO · 1 INSTALACIÓN**. Las pruebas no se ejecutan al abrir la actividad; cada uso de micrófono requiere pulsación, permisos de Android y servicio en primer plano con parada visible.

**No certificado** hasta disponer de resultados físicos. El emulador Android 14 y el modelo Qwen de GitHub Actions no prueban Funtouch OS ni propietario real. El GGUF oficial de CI (491 MB) se usa únicamente en los tests y **no está incluido** en la APK: para un ensayo Qwen local hace falta importar, por elección del propietario, un GGUF compatible desde la pantalla principal.

## Secuencia física con el teléfono

1. Instalar la APK debug del workflow **Android shell** del commit actual, sin desinstalar la versión previa o perder modelos propios; el identificador debug es distinto del de producción. Abrir **JARVIS → DIAGNÓSTICO VIVO V25 PRO** y pulsar **ACTUALIZAR**. Verificar el modelo mostrado y Android API.
2. **Micrófono + TTS (4 s)**: conceder los permisos necesarios, pulsar de nuevo y hablar durante cuatro segundos. La app solo mide cantidad/nivel de muestras; no almacena audio. Escuchar si reproduce el texto de prueba en español. Registrar por separado **MICROPHONE** y **TTS** como FUNCIONA/FALLA/NO_PROBADO.
3. **Wake + Vosk (60 s)**: decir «Jarvis, dime la hora»; inspeccionar el estado ASR_NATIVE_READY, reconocimiento y reproducción. Registrar **WAKE**, sin llamarlo reconocimiento de propietario.
4. **Pantalla bloqueada**: con permiso explícito, iniciar la escucha extendida y esperar `ASR_NATIVE_READY`. Bloquear el teléfono; sin encender la pantalla decir **«Jarvis, dime la hora»**. Volver a la actividad y pulsar ACTUALIZAR. El informe presenta por separado muestras PCM capturadas por AudioRecord con pantalla apagada (`lockscreen_off_samples`), `Comandos_HORA_recibidos_pantalla_apagada` y `Comandos_STOP_recibidos_pantalla_apagada`. La primera medida sola **no prueba respuesta audible**. Indicar manualmente si la voz respondió; un contador >0 no certifica escucha perpetua ni identidad.
5. **Identidad vocal**: abrir el registro/probador existente bajo consentimiento explícito; no se recolecta ni comparte voz en este informe. Marcar OWNER únicamente como resultado informado, sin promocionar el experimento como antisuplantación/FAR-FRR certificado.
6. **Qwen offline**: si hay un GGUF privado ya importado, pulsar prueba directa y observar latencia y respuesta sin Internet. Cuando falta el modelo se informa GGUF_NO_IMPORTADO y **no se simula éxito**. Para el flujo completo Vosk→GGUF→TTS puede iniciarse la sesión dedicada de 30 minutos solo con consentimiento.
7. **DETENER** se prueba únicamente con una sesión de micrófono activa. Se puede decir «Jarvis, para» o usar el botón DETENER / la notificación. La nueva sonda exige un recibo de la solicitud y que el servicio quede inactivo; tocar DETENER sin sesión registra `SIN_SESION_ACTIVA`, **no** un éxito. Si solo había Qwen activo informa `QWEN_SOLO_CANCELADO`. Marcar STOP manualmente según el resultado audible/visible; el servicio no puede medir por sí mismo si dejó de reproducir sonido. Finalmente pulsar **COMPARTIR INFORME**: el texto contiene solo modelo genérico, API, mediciones cuantitativas, códigos fijos y marcas manuales; no incluye números de serie, IMEI, audio, transcripciones, texto de preguntas/respuestas, rutas de modelos ni secretos.

## Condiciones de aceptación
- CI de la rama nueva: Python Contract tests, JARVIS QA50, Android shell (unit tests y APK), Android 14 instrumentation, y las pruebas GGUF aplicables en HEAD exacto.
- Uso físico: ninguna de las siete pruebas puede declararse aprobada por el simple hecho de tocar un botón o de que el emulador funcione.
- Si el modelo local no está instalado, declarar `GGUF_NO_IMPORTADO`. Ninguna descarga silenciosa ni habilitación de red.
- No se ejecutan acciones telefónicas privilegiadas ni se activa hands-free al abrir la APK. La función de fondo solo es la sesión de micrófono con autorización explícita (limitada a 30 minutos y control DETENER visible).
- Rechazar cualquier informe que afirme reconocimiento de voz del dueño, escucha 24/7 o continuidad ilimitada sin validación física y evaluación independiente.

## Evidencia de entrega
El ZIP `jarvis-omega-debug-apk` del workflow Android shell contiene `app-debug.apk`. Si el ZIP no aparece en el HEAD vigente o falla un check, no describir la APK como comprobada.


## Adaptación a PR #98 (experimental)
El laboratorio original #89 se ha portado al HEAD del PR #98, sin
reemplazar sus implementaciones más nuevas de GGUF, AEC ni STOP.
Para conversación de hasta seis preguntas y 30 minutos, únicamente
en compilación debug, se usa `ACTION_START_LONG_CONVERSATION`.
La ruta previa de una pregunta usa `ACTION_START_CONVERSATION`.
Ninguna concede permisos ni ejecuta órdenes del teléfono.

Los contadores de PCM con pantalla apagada se clasifican dentro del hilo
de AudioRecord al capturar cada buffer. El reporte separa muestras, órdenes
reconocidas y marcas manuales. La actividad no inicia audio desde onCreate.

Registrar también: revocación de micrófono durante escucha/inferencia/TTS,
STOP por notificación y botón, falsos wake por eco, casos sin AEC,
barge-in con preguntas legítimas y maliciosas, ruido, auriculares,
pantalla bloqueada, batería, temperatura, ANR/crash y latencia P50/P95.
Las etiquetas BARGE_IN, ECHO_REJECTION y MIC_REVOKED se conservan solo
como USER_REPORTED y nunca acreditan certificación.

No fusionar la rama sobre main ni habilitar la función experimental
en release hasta recibir evidencia física consentida y auditoría.
