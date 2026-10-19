# JARVIS — evidencia de escucha con pantalla bloqueada y evaluación biométrica

**Estado (2026-10-18): INFRAESTRUCTURA DE PRUEBAS, NO CERTIFICADO.**
Este protocolo no convierte una puntuación de coseno en identidad y nunca
activa `SpeakerEvidence.MATCHED`. Las pruebas reales no se suponen realizadas
hasta que sus registros originales existan y estén verificados.

## 1. Pantalla bloqueada: qué está probado y qué no

- Android permite que un FGS de tipo microphone iniciado con una Activity
  visible continúe grabando tras bloquear la pantalla, sujeto al permiso,
  la política de energía y las decisiones del OEM. **No** permite asumir
  inicio invisible tras BOOT_COMPLETED ni garantía de escucha 24/7.
- JARVIS inicia la sesión hasta 30 minutos mediante pulsación y aviso explícitos.
  Debe conservar notificación con DETENER; descartar la tarea desde Recientes
  cancela la sesión. Sin permiso no se inicia el micrófono.
- `LockScreenAudioEvidence` solo cuenta `AudioRecord.read` positivos durante
  `PowerManager.isInteractive == false`, al menos 2 reportes distintos y
  16.000 muestras agregadas. No recopila ni almacena PCM, texto o embeddings.
- La prueba instrumental Android 14 arranca el motor offline con Activity
  visible, envía KEYCODE_SLEEP, comprueba `!PowerManager.isInteractive`, 
  verifica muestras de audio mientras sigue apagada y finalmente detiene
  servicio, despierta el emulador y libera recursos.
- Ese resultado es **evidencia de un emulador**, no prueba de funcionamiento
  en un Vivo V25 Pro/Funtouch OS con ahorro de batería, pantalla bloqueada
  30 minutos, llamadas, otras apps o permisos retirados.
- Para el ensayo físico posterior: anotar modelo, Android/Funtouch, batería,
  ahorro de energía, permiso micro, hora de inicio, si hubo notificación
  continua, estado a 1/5/15/30 minutos con pantalla apagada, `screenoff_samples`,
  fecha y logs del sistema si están disponibles. Evitar grabar conversaciones.

## 2. Corpus de voz del propietario e impostores

**No existe un corpus real acreditado en GitHub.** El guion solo evalúa
metadatos y scores nuevos generados por una captura consentida.
Las tres muestras de inscripción se reservan exclusivamente para construir la
plantilla cifrada en el dispositivo y **nunca** son probes ni holdout.

Campos del JSONL privado por cada ensayo (sin nombre ni identificador personal):

`trial_id, split, kind, speaker_id, session_id, recording_sha256,
enrollment_sha256, model_sha256, score, consent`.

- `kind`: `genuine` (propietario en nueva sesión), `impostor` (otra
  persona con autorización explícita) o `replay` (reproducción controlada de
  material autorizado del propietario).
- `split`: `calibration` o `holdout`, asignado **antes** de inspeccionar
  puntuaciones. Ningún audio/huella/session se reutiliza entre particiones.
  Los hablantes impostores se separan por partición; no se filtran puntuaciones
  del holdout para elegir el umbral.
- `speaker_id`: pseudónimo `owner` para voz del dueño y replay dirigida al
  dueño; los impostores tienen pseudónimos diferentes, no datos personales.
- `recording_sha256`: hash del audio de prueba original independiente.
  `enrollment_sha256`: huella del audio/registro de inscripción de referencia;
  debe ser distinta a cada prueba.
- `replay_source_sha256`: obligatorio para `replay`, identifica la grabación
  original que fue reproducida, también separada del holdout y no puede ser
  un audio usado en la inscripción.
- `model_sha256`: debe ser exactamente el del ONNX ERes2Net fijado en la APK:
  `1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b`.
- `score`: coseno local real del motor; no aproximación ni umbral inventado.
  `consent: true` requiere consentimiento real conservado fuera del repositorio.

### Mínimos estrictos para intentar evaluar FAR de 1 %

Por partición independiente: >=30 pruebas genuinas de al menos 3 sesiones,
>=300 pruebas de impostor de al menos 20 personas diferentes,
>=300 pruebas de reproducción de al menos 20 grabaciones originales. Se
necesitan dos particiones: calibración y holdout. Son **mínimos de validación
técnica**, no suficientes para afirmar identidad segura, ya que múltiples
ensayos de una misma persona pueden estar correlacionados.

Estrategia: seleccionar umbral únicamente sobre calibración bajo
`FAR<=1 %` y cero aceptaciones de reproducción **en calibración**.
Aplicarlo sin modificar al holdout; reportar FRR, FAR y tasa de aceptación
de replay con límite superior de confianza unilateral 95 %.
Para 300 pruebas negativas independientes sin falsos aceptados, el límite
Wilson superior ronda 0,9 %. Aun así, la variedad de hablantes, micrófonos,
ruido, dispositivos, métodos de reproducción y ataques adaptativos importa
más que un conteo bruto: **no hay garantía universal de FAR**.

Conservar evidencias de consentimiento, versiones, SHA de modelo y archivos,
sesiones y separación; **no subir** voces, scores, huellas de grabaciones
privadas, plantillas ni informes particulares al repo público.
Ejecutar localmente:

```bash
python3 tools/evaluate_owner_biometric_holdout.py \
  --input /ruta/privada/trials.jsonl \
  --expected-holdout-sha256 <sha256_fijado_antes_de_calibrar> \
  --output /ruta/privada/aggregate.json
```

El SHA requerido es el SHA de las filas holdout normalizadas que calcula el
evaluador, no el SHA del archivo JSONL completo. Debe quedar sellado en un
registro independiente *antes* de seleccionar umbral. El programa compara
solo scores locales y publica agregados; la procedencia y permisos requieren
auditoría externa. Para probar el programa se usan scores sintéticos **solo en
unit tests**; no constituyen evidencia biométrica.

### Criterio de salida

- Prueba Android14 con pantalla apagada **verde**, sin ocultar fallos.
- Ensayo físico Vivo a 1/5/15/30 min y pruebas de interrupción y revocación
  independientes, sin batería agotada ni reactivaciones automáticas forzadas.
- Corpus real y consentimiento, captura genuina/impostor/replay independiente.
- Cálculo de FAR/FRR y replay con límites, umbral fijo antes del holdout,
  comparación con baseline y revisión de posibles correlaciones.
- **Mantener bloqueadas órdenes owner-only hasta contar con liveness/
  anti-replay, validación física y calibración certificada**.

Referencias:
- Android FGS microphone / while-in-use:
  https://developer.android.com/about/versions/14/changes/fgs-types-required
- NIST SRE:
  https://www.nist.gov/publications/nist-2021-speaker-recognition-evaluation-plan
- NIST incertidumbre:
  https://www.nist.gov/publications/measurement-uncertainties-speaker-recognition-evaluation
