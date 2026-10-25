# Estabilización Android — bloques TTS y errores GGUF

## Alcance y procedencia

- Rama local: `fix/android-voice-playback-lifecycle`.
- Base exacta: `6e24129d62eddc77304ba77857f1cd6b61f8a448` (#101).
- Estado remoto consultado: #98, #99, #100 y #101 abiertos y Draft, sin fusionar.
- Dependencias conservadas, no reimplementadas: #98 → #99 → #101. #100 es
  independiente y no se ha incorporado. No se publicaron commits ni PR.
- Ningún cambio de manifiesto, permisos, proveedor, modelo, JNI, workflow o
  autorización de herramientas. No se ejecutaron Actions, APKs ni modelos.

## Priorización tras revalidar el código

| Prioridad | Problema | Reproducibilidad y decisión |
| --- | --- | --- |
| P0 | AudioRecord sin muestras, errores negativos y callbacks después de STOP | Correcciones ya presentes en #99/#101; conservarlas y ejecutar sus pruebas puras. Su eficacia física sigue PENDIENTE. |
| P0 | Watchdog TTS reabre reconocimiento durante reproducción; callbacks duplicados | CONFIRMADO por el flujo del servicio. Seleccionado como primer cambio pendiente, relacionado con micrófono y cancelación. |
| P1 | Fallos JNI contabilizados como respuestas GGUF | CONFIRMADO mediante regresiones JVM fallidas antes del arreglo; segundo bloque independiente. |
| P1 | Activación «Jarvis», STOP acústico durante inferencia y eficacia AEC | Cableado conocido, pero causa acústica no reproducida. PENDIENTE; no ampliar gramáticas ni desactivar protecciones por hipótesis. |
| P1/P2 | Energía, RAM, latencia, historial GGUF y Windows | PENDIENTE; no modificar sin línea base física y sin cerrar integración Android. |

## Bloque 1 — ciclo de reproducción y cancelación

### CONFIRMADO: causa raíz en la base #101

`apps/android/app/src/main/java/com/cardenaspiero255/jarvis/HandsFreePreviewService.kt`:

- `play`, líneas 1085–1095 y 1139–1148: `onStart` no desarmaba el plazo de
  diez segundos. Este llamaba `finishOrResume`, que reabría ASR tras 350 ms
  sin parar antes el TTS que podía seguir reproduciéndose.
- `play`: la generación se comprobaba, pero no la fase ni si el callback
  terminal ya había sido consumido. Un `onError` tampoco retiraba el watchdog.
- `speakThenStop`, líneas 1055–1068: la espera de inicialización solo tenía
  watchdog en modo extendido. La pregunta única ya había retirado sus otros
  plazos antes de esperar TTS.
- `prepareTts`, línea 877: faltaba comprobar `endedNormally`, estado que STOP
  establece antes de que Android destruya el servicio.
- `processOfflineQuestion`: no invalidaba el ticket usado por una tarea de
  reactivación de la cola de eco del turno anterior.

### CORREGIDO

Se reutiliza `core/SpeechUtteranceTracker.kt`, sin reescribir el servicio:

- `play`: IDs de locución de un solo uso; inicio, finalización y errores deben
  corresponder a la fase y generación actuales. Los duplicados se descartan.
- Plazos separados: 7 s esperando inicialización después de preparar una
  respuesta; 10 s esperando inicio; 60 s después de inicio esperando fin.
  El límite de reproducción es conservador, no una medición del Vivo.
- Errores, rechazo y timeouts cierran la sesión mediante `finish`, que detiene
  captura y TTS. No reabren ASR ante un motor de reproducción incierto.
- `finish`, interrupción por pregunta y `onDestroy` cancelan el tracker y el
  watchdog. STOP también borra la respuesta pendiente inmediatamente.
- `prepareTts` y los puntos de reproducción rechazan una sesión ya terminada.
- Una nueva pregunta invalida la tarea de reactivación de eco anterior.
- La finalización normal conserva la espera de cola de eco de 350 ms.

**Cambio visible:** un error/timeout de TTS termina la sesión extendida y
requiere iniciarla de nuevo explícitamente. Una locución que supere 60 s
también la termina. Se prefiere ese cierre a escuchar el propio altavoz.

### Pruebas y alcance

- RED: los seis nuevos contratos de cableado fallaron contra el servicio base.
  Son inspecciones de fuente, no ejecuciones Android ni reproducción acústica.
- VERIFICADO en JVM: ocho pruebas de `SpeechUtteranceTrackerTest`, incluidas
  cuatro nuevas para plazo inicial tras inicio, falta de fin, duplicados,
  errores y callbacks/plazos posteriores a STOP.
- VERIFICADO por contratos: cableado de esos controles en el servicio,
  inicialización de pregunta única y revocación de tareas antiguas.
- PENDIENTE: compilar el servicio contra Android y reproducir las secuencias
  con Handler/TTS reales. Las pruebas JVM no ejecutan el servicio Android.

## Bloque 2 — fallos GGUF no son generación

### CONFIRMADO: causa raíz

- `apps/android/app/src/main/cpp/jarvis_llm.cpp:118–120,139–145,164–177`
  devuelve errores como cadenas, mediante el mismo canal que los tokens.
- `core/OfflineVoiceQuestionGate.kt:108–115` en la base solo rechazaba cinco
  prefijos del adaptador, omitiendo los fallos nativos.
- `core/VivoGgufProbePolicy.kt:9–13` utiliza ese filtro. El servicio incrementa
  sus recibos de éxito después de aceptarlo.
- RED real: `VivoGgufProbePolicyTest` devolvió
  `TEXTO_NO_VACIO_NO_CERTIFICADO` para `No se pudo cargar el modelo GGUF.`;
  otra regresión aceptó `Solicitud local inválida.` como respuesta hablada.
  Resultado antes del arreglo: 13 pruebas JVM, dos fallidas.

### CORREGIDO

`OfflineVoiceAnswerPolicy.forTts` ahora comprueba los 19 prefijos de fallo
del JNI y del adaptador antes de truncar la respuesta. La misma corrección
protege tanto TTS como los recibos de diagnóstico existentes, sin alterar
el formato JNI ni la política de autorización de preguntas.

### VERIFICADO y límites

- Regresiones ejecutables: errores nativos, mayúsculas, espacios, error seguido
  de texto largo, respuesta normal y clasificación de las sondas.
- Nuevo contrato de fuente comprueba que cada centinela literal de los
  productores está cubierto y que ambas rutas siguen usando el filtro común.
- Es un arreglo pequeño del puente de cadenas, **no** un resultado JNI tipado.
  Una generación que empiece exactamente por un prefijo de fallo puede ser
  rechazada por prudencia. El resultado tipado continúa PENDIENTE.
- No se verificaron carga real, persistencia, calidad ni cancelación nativa
  con pesos GGUF durante este trabajo.

## Resultado automatizado conjunto

- **65 pruebas JVM aprobadas, cero fallidas al cierre**, en 14 clases:
  `SpeechUtteranceTracker`, `LocalGgufPolicy`, `OfflineVoiceQuestionGate`,
  `VivoGgufProbePolicy`, `BargeInStopPolicy`, `BargeInQuestionPolicy`,
  `HandsFreePreviewPolicy`, `PreviewWakeTurnGate`,
  `NativeAudioSourceFailoverPolicy`, `NativeAudioZeroReadGuard`,
  `NativeAudioReadFailurePolicy`, `NativeAudioExitPolicy`,
  `NativeWakeDeliveryGate` y `LocalGgufRunState` (cada una con su `Test`).
- **70 pruebas Python/contratos aprobadas**, cero fallidas al cierre.
- `git diff --check` aprobado.
- Compilación JVM directa, sin ejecutar Gradle/preBuild ni descargar assets:
  compilador Kotlin 2.4.0 ya instalado con Gradle, en modo lenguaje/API 2.3,
  destino JVM 17; ejecución con OpenJDK 25.0.4.1 y JUnit 4.13.2.
  Esto no reemplaza Kotlin 2.3.21/JDK 17/SDK/NDK del proyecto Android.
- Solo se descargaron JUnit y Hamcrest de Maven Central, aproximadamente
  429 KB en conjunto, a `/tmp/opencode`; compilación limitada a 512 MiB de
  heap y ejecución de JUnit a 128 MiB. Sin descargas de modelos ni SDK.

Repetición de los contratos ligeros desde la raíz:

```bash
PYTHONPATH=tests python -B -m unittest \
  test_preview_tts_lifecycle_contract test_gguf_failure_sentinel_contract \
  test_voice_bargein_upstream_contract test_conversational_bargein_contract \
  test_vivo_one_install_diagnostic_contract test_offline_voice_conversation_contract \
  test_handsfree_preview_contract test_speech_timeout_safety \
  test_privacy_session_lock_contract test_omega_voice_dashboard_contract \
  test_local_gguf_feature_contract test_gguf_warm_cache_contract \
  test_gguf_real_e2e_contract
```

Los nombres `real_e2e_contract` solo inspeccionan contratos de fuente aquí:
no se ejecutó el E2E con modelo verdadero.

## Rendimiento y siguientes puertas

No hay mediciones comparables de CPU, RAM, batería ni latencia del teléfono;
no se afirma ninguna mejora de rendimiento. Los arreglos no añaden polling,
workers, carga de modelos ni nuevas solicitudes de red.

**No ejecutado por control de recursos:** Gradle Android, APK, emulador,
instrumentación, E2E JNI con modelo y ensayos físicos. La próxima puerta es
compilar el SHA candidato con el toolchain Android del proyecto y probar
el servicio con un motor TTS controlado antes de ampliar estos bloques.

### Pruebas personales en Vivo V25 Pro / Android 14

Se necesita una APK compilada desde esta rama; las APK antiguas de #99 no
contienen estos arreglos. No instalar/desplegar automáticamente.

1. Iniciar una sesión explícita offline, comprobar hora/batería y repetir una
   consulta. Debe haber una sola respuesta por turno.
2. Si es posible generar una respuesta hablada de más de diez segundos,
   comprobar que la escucha normal no se reactiva a los diez segundos y que
   continúa después del fin real. Registrar duración y estado, no audio privado.
3. Pulsar DETENER durante preparación, inferencia y voz. Esperar más de diez
   segundos: no debe reaparecer una respuesta ni reiniciarse la sesión.
4. Probar `Jarvis, para` por separado e indicar en qué fase funciona. Durante
   inferencia y sin AEC el STOP acústico sigue limitado por el diseño anterior;
   usar siempre el control visible de parada. No se certifica aquí esa ruta.
5. Probar ausencia/fallo de voz local y revocación de micrófono desde Android.
   Registrar si termina la sesión y el indicador de uso del micrófono; un
   contador o callback no acredita por sí solo liberación física.
6. Con GGUF legítimo ya importado, comprobar consulta offline y persistencia
   tras reabrir la app. Si aparece un fallo de inferencia, no debe contarse
   como generación exitosa ni reproducirse como respuesta del modelo.

Continúan **PENDIENTES** el wake real, AEC, cancelación nativa dentro de una
decodificación/carga, presión de memoria, consumo y certificación física.
No se han ampliado permisos ni convertido texto del modelo en acciones.
