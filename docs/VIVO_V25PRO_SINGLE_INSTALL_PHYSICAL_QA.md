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


## Campaña presencial tras PR #99 · GGUF y voz (sin certificación automática)

**Modelo compatible y derechos:** el laboratorio de debug permite ahora
`IMPORTAR GGUF PRIVADO · ARCHIVO LOCAL` sin abandonar la pantalla.
Android abre su selector de documentos con `ACTION_OPEN_DOCUMENT` y preferencia
de archivos locales. Se copia a almacenamiento privado y se registra SHA-256,
sin permisos generales de almacenamiento ni acceso a Internet desde JARVIS.

Para una prueba pequeña y reproducible, el repositorio fija un ejemplo de
SmolLM2 135M Q4_K_M, bajo Apache-2.0, documentado en
`tools/prepare_gguf_e2e_fixture.py`:
`https://huggingface.co/ggml-org/SmolLM2-135M-GGUF`
`SmolLM2-135M-Q4_K_M.gguf`.
**No está integrado en la APK**: el propietario debe descargarlo por su
cuenta, comprobar origen y condiciones y elegirlo voluntariamente mediante
el selector. No sustituir por una extensión .gguf renombrada.
El resultado positivo `TEXTO_NO_VACIO_NO_CERTIFICADO` prueba solamente texto
generado/no centinela + latencia; precisión semántica requiere observación.

### Orden en el Vivo V25 Pro (una instalación)
1. Con batería suficiente y Android 14, abrir JARVIS debug → LABORATORIO
   VIVO; `ACTUALIZAR`, anotar modelo, permisos, térmico y RAM.
2. Importar GGUF desde **ARCHIVO LOCAL** y comprobar
   `GGUF_IMPORTADO_SHA256`. Si ya existe, puede omitirse la importación.
   El ensayo después de una importación limpia empieza en `NO_PROBADO`;
   no hereda un antiguo `QWEN=FUNCIONA`.
3. Desactivar temporalmente Wi-Fi y datos **manualmente** si se desea
   un ensayo de aislamiento; pulsar `Qwen local: respuesta + latencia`.
   Guardar únicamente código de resultado y milisegundos. No reenviar
   texto generado, audio ni URI del modelo. Reactivar la conectividad
   manualmente después.
4. Iniciar la escucha extendida consentida, bloquear pantalla,
   pronunciar `Jarvis, dime la hora`, observar si responde **sin desbloquear**.
   Repetir con `Jarvis, para` y ver si la notificación desaparece.
   Separar PCM, contadores HORA/STOP/preguntas y audibilidad USER_REPORTED.
5. Para revocación: iniciar una sesión, entrar a Ajustes Android
   → Permisos → Micrófono → JARVIS → Denegar. Volver a JARVIS,
   inspeccionar `Revocacion_MIC_observada` (si es `NO_OBSERVADO`,
   la causa sigue sin acreditarse), el indicador de uso del micrófono
   en Android y si el servicio cesó. Restablecer el permiso manualmente.
   La negación de una lectura no demuestra por sí sola que la app haya
   liberado el recurso dentro de un plazo concreto.
6. Para eco: iniciar una conversación de hasta seis turnos con GGUF ya
   importado. Registrar `AEC_estado_sesion`; durante TTS hablar
   `Jarvis, ¿cuál es la capital de Chile?` y comprobar si responde a
   **una sola** interrupción. Repetir sin hablar: el TTS propio no debe
   generar turnos espurios. Si AEC no está disponible, usar DETENER
   visible: **no** declarar superada la interrupción acústica.
7. Compartir el reporte sin identificadores. La app nunca declara
   propietario autenticado, resistencia replay, 24/7 ni voz audible
   certificada por una marca manual.

### Alcance de CI
`Android GGUF real generation E2E` se activa ahora por cambios en el
laboratorio o en el clasificador; ejecuta el modelo verdadero en un
**emulador**, no en el Vivo. El resultado nunca certifica Funtouch OS,
revocación de micrófono ni supresión acústica del eco.


## Consolidación de recibos y rendimiento limitado

Una prueba de generación **por voz** registra contadores sin guardar preguntas ni
respuestas: `Voice_GGUF_respuestas_medido` y
`Voice_GGUF_ultima_latencia_ms`. Esto es independiente del botón 5
(`Qwen_ejecución`), cuya cancelación al salir sigue siendo real y no
acredita un fallo de la conversación. El informe solo considera ambos
canales como fuentes diferentes y nunca certifica precisión o audio audible.

La escucha extendida persiste muestras reales de `AudioRecord`
antes de encolarlas en el hilo principal; la última fracción de audio se
drena al liberar la captura. Si Android/Funtouch no entrega PCM mientras
la pantalla está apagada, el informe sigue indicando 0, sin inventar
observaciones. Se contabilizan eventos `BargeIn` y `TTS_STARTED`, que
solo comprueban callbacks y no son certificados acústicos.

El perfil rápido limita hasta **32 tokens** las respuestas habladas y
**64 tokens** las consultas escritas; el JNI conserva el tope de 96
como barrera superior. Se prioriza latencia frente a respuestas extensas. El motor nativo usa entre
**2 y 4** hilos CPU según núcleos visibles y retiene pesos GGUF de
hasta 650 MB únicamente si Android informa por lo menos 3 GiB de
memoria disponible y no marca presión de memoria. Esto es una
hipótesis medible, no una garantía de velocidad: hay que comparar
latencia, temperatura, RAM/PSS y calidad real en el Vivo. Por seguridad,
no desactivar SHA-256, cancelación ni autorización. Modelos más grandes
siguen usando la ruta que libera pesos entre respuestas.
