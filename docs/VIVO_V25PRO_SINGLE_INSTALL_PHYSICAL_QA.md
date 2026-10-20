# JARVIS — laboratorio físico Vivo V25 Pro (una sola instalación)

## Alcance
Compilar e instalar **una** APK Android de depuración aislada del JARVIS normal (`com.cardenaspiero255.jarvis.handsfreepreview`). La interfaz incluye el botón **DIAGNÓSTICO VIVO V25 PRO · 1 INSTALACIÓN**. Las pruebas no se ejecutan al abrir la actividad; cada uso de micrófono requiere pulsación, permisos de Android y servicio en primer plano con parada visible.

**No certificado** hasta disponer de resultados físicos. El emulador Android 14 y el modelo Qwen de GitHub Actions no prueban Funtouch OS ni propietario real. El GGUF oficial de CI (491 MB) se usa únicamente en los tests y **no está incluido** en la APK: para un ensayo Qwen local hace falta importar, por elección del propietario, un GGUF compatible desde la pantalla principal.

## Secuencia física con el teléfono

1. Instalar la APK debug del workflow **Android shell** del commit actual, sin desinstalar la versión previa o perder modelos propios; el identificador debug es distinto del de producción. Abrir **JARVIS → DIAGNÓSTICO VIVO V25 PRO** y pulsar **ACTUALIZAR**. Verificar el modelo mostrado y Android API.
2. **Micrófono + TTS (4 s)**: conceder los permisos necesarios, pulsar de nuevo y hablar durante cuatro segundos. La app solo mide cantidad/nivel de muestras; no almacena audio. Escuchar si reproduce el texto de prueba en español. Registrar por separado **MICROPHONE** y **TTS** como FUNCIONA/FALLA/NO_PROBADO.
3. **Wake + Vosk (60 s)**: decir «Jarvis, dime la hora»; inspeccionar el estado ASR_NATIVE_READY, reconocimiento y reproducción. Registrar **WAKE**, sin llamarlo reconocimiento de propietario.
4. **Pantalla bloqueada**: con permiso explícito, iniciar la escucha extendida, bloquear por unos segundos, volver a la actividad, pulsar ACTUALIZAR. El reporte distingue `lockscreen_off_samples` y `SCREEN_OFF_AUDIO_OBSERVED`. Un contador >0 prueba que hubo muestras durante un estado reportado de pantalla apagada; **no** prueba reactivación automática desde el teléfono dormido ni escucha perpetua.
5. **Identidad vocal**: abrir el registro/probador existente bajo consentimiento explícito; no se recolecta ni comparte voz en este informe. Marcar OWNER únicamente como resultado informado, sin promocionar el experimento como antisuplantación/FAR-FRR certificado.
6. **Qwen offline**: si hay un GGUF privado ya importado, pulsar prueba directa y observar latencia y respuesta sin Internet. Cuando falta el modelo se informa GGUF_NO_IMPORTADO y **no se simula éxito**. Para el flujo completo Vosk→GGUF→TTS puede iniciarse la sesión dedicada de 30 minutos solo con consentimiento.
7. **DETENER** debe cancelar el trabajo y pedir liberación del micrófono. Marcar STOP manualmente. Finalmente pulsar **COMPARTIR INFORME**: el texto contiene solo modelo genérico, API, mediciones cuantitativas, códigos fijos y marcas manuales; no incluye números de serie, IMEI, audio, transcripciones, texto de preguntas/respuestas, rutas de modelos ni secretos.

## Condiciones de aceptación
- CI de la rama nueva: Python Contract tests, JARVIS QA50, Android shell (unit tests y APK), Android 14 instrumentation, y las pruebas GGUF aplicables en HEAD exacto.
- Uso físico: ninguna de las siete pruebas puede declararse aprobada por el simple hecho de tocar un botón o de que el emulador funcione.
- Si el modelo local no está instalado, declarar `GGUF_NO_IMPORTADO`. Ninguna descarga silenciosa ni habilitación de red.
- No se ejecutan acciones telefónicas privilegiadas ni se activa hands-free al abrir la APK. La función de fondo solo es la sesión de micrófono con autorización explícita (limitada a 30 minutos y control DETENER visible).
- Rechazar cualquier informe que afirme reconocimiento de voz del dueño, escucha 24/7 o continuidad ilimitada sin validación física y evaluación independiente.

## Evidencia de entrega
El ZIP `jarvis-omega-debug-apk` del workflow Android shell contiene `app-debug.apk`. Si el ZIP no aparece en el HEAD vigente o falla un check, no describir la APK como comprobada.
