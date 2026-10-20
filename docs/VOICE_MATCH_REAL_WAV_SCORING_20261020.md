# JARVIS — Voice Match acústico real: WAV → ONNX → puntuaciones privadas

**Situación: pipeline de medición disponible; corpus humano y certificación pendientes.**
Esta herramienta no graba personas automáticamente, no autentica a nadie y no
concede funciones de accesibilidad, Shizuku, Termux ni control de aplicaciones.
Los tests de CI usan señales generadas para comprobar lógica, *nunca*
como sustituto de voces humanas reales.

## 1. Arquitectura y fidelidad con Android

\`tools/score_owner_real_wav.py\` realiza inferencias **de verdad** mediante
\`sherpa_onnx.SpeakerEmbeddingExtractor\` de Python, con el ONNX
\`3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx\`
fijado al SHA-256:

\`1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b\`.

Los tres WAV de inscripción se convierten a embeddings reales; cada
vector se normaliza, se exige similitud mutua ≥0,78 y se obtiene la
**media normalizada de los tres**, igual que
\`SpeakerEnrollmentPolicy.kt\` de JARVIS Android.
Cada WAV de prueba se puntúa por coseno como
\`OwnerVoiceCosine.compare\`, pero esa puntuación es **diagnóstica**:
nunca activa \`SpeakerEvidence.MATCHED\`.

El extractor utiliza inferencia CPU 16 kHz, sin acceso a Internet ni
reconocimiento de identidad cloud. Solo usa audio autorizado existente,
no activa el micrófono.

## 2. Corpus privado, consentimiento y requisitos técnicos

Conservar material auténtico **fuera de GitHub y de la carpeta del repositorio**.
Obtener consentimiento informado del propietario y, por separado, de
cada participante que acepte intervenir. Se requieren:

- Tres WAV distintos del propietario para **inscripción**. Nunca
  reutilizarlos como probes. Duración 1,5–10 s.
- WAV de **genuine** (propietario), **impostor** (terceros consentidos) y
  **replay** (una grabación autorizada reproducida frente al micrófono);
  duración de prueba 2,5–10 s.
- WAV originales de cada fuente replay y el SHA-256 de esas fuentes;
  conservar evidencia de qué dispositivo reprodujo cada fuente y cómo
  se grabó el ataque. El programa verifica el archivo fuente, pero
  **no puede probar** que efectivamente se reprodujera frente al micrófono.
- Todos los WAV mono, PCM16, 16000 Hz, máximo 10 s y SHA-256 de sus bytes;
  archivos adulterados, silencio, rutas relativas inseguras y enlaces
  simbólicos se rechazan.
- Nombres de participantes y detalles de grabación en un registro privado
  de consentimiento; en el manifiesto usar solo pseudónimos.

Ejemplo ilustrativo de formato, no datos biométricos ni resultados:

\`\`\`json
{
  "schema": "jarvis.speaker-wav.v1",
  "enrollment": [
    {"file": "enrollment/a.wav", "sha256": "<SHA256 REAL>"},
    {"file": "enrollment/b.wav", "sha256": "<SHA256 REAL>"},
    {"file": "enrollment/c.wav", "sha256": "<SHA256 REAL>"}
  ],
  "trials": [
    {
      "file": "probes/owner-new-session.wav",
      "recording_sha256": "<SHA256 REAL>",
      "trial_id": "owner_trial_001",
      "split": "calibration",
      "kind": "genuine",
      "speaker_id": "owner",
      "session_id": "owner_session_04",
      "consent": true
    },
    {
      "file": "probes/replay-captured.wav",
      "recording_sha256": "<SHA256 REAL>",
      "trial_id": "replay_trial_001",
      "split": "holdout",
      "kind": "replay",
      "speaker_id": "owner",
      "session_id": "replay_session_04",
      "replay_source_file": "replay_sources/original-001.wav",
      "replay_source_sha256": "<SHA256 REAL>",
      "consent": true
    }
  ]
}
\`\`\`

Completar muchas más filas antes de la evaluación estadística. No agregar
scores inventados: el extractor calcula \`score\`. En las filas de impostor
\`speaker_id\` debe ser un pseudónimo distinto de \`owner\`.

## 3. Ejecución local, explícita y privada

El modelo pinneado puede prepararse previamente en un computador autorizado
utilizando \`tools/prepare_speaker_model.py\`, cuyo archivo resultante será
\`verified_sherpa_speaker/model.onnx\`. Este paso de descarga, de ser
necesario, solo ocurre **en el computador**, nunca por iniciativa de la
APK. El modelo se verifica antes de cargar la biblioteca de inferencia.
Instalar \`sherpa-onnx\` y \`numpy\` en un entorno Python local aislado,
registrando sus versiones para la procedencia.

\`\`\`bash
python3 tools/score_owner_real_wav.py \
  --model /ruta/privada/verified_sherpa_speaker/model.onnx \
  --audio-root /ruta/privada/consented-wav \
  --manifest /ruta/privada/manifest.json \
  --output /ruta/privada/scored-trials.jsonl \
  --confirm-consented-local-audio
\`\`\`

**La salida se crea exclusivamente con modo 0600**, sin sobrescribir
otro archivo; no incluye nombres de archivos de audio, PCM, embeddings,
transcripciones ni plantillas. Contiene scores individuales sensibles,
por lo que debe permanecer privada. No subirla a GitHub, Vercel ni CI.

La prueba de CI \`tests/test_score_owner_real_wav.py\` usa archivos
sintéticos y un extractor sustituido para verificar el contrato;
**no prueba que el modelo real reconozca a una persona.**

## 4. Independencia, FAR/FRR y posterior evaluación

Antes de calcular los scores, distribuir muestras por sesiones, fuentes
replay y hablantes en \`calibration\`/ \`holdout\` de forma irrevocable.
El conjunto de evaluación debe permanecer sin usar para entrenar,
seleccionar modelo o afinar umbrales; mantener prueba externa con fecha
de ese reparto y hashes de WAV.

El evaluador existente usa \`tools/evaluate_owner_biometric_holdout.py\`.
Fijar primero **el SHA del archivo de filas puntuadas del holdout según la
serialización canónica definida por el propio evaluador**, en un registro
independiente *antes* de seleccionar cualquier umbral o leer sus métricas;
la declaración de precompromiso exige evidencia temporal externa.
No es válido calcularlo después de elegir umbral y declararlo
retroactivamente sellado.

\`\`\`bash
python3 tools/evaluate_owner_biometric_holdout.py \
  --input /ruta/privada/scored-trials.jsonl \
  --expected-holdout-sha256 SHA256_CANONICO_PRECOMPROMETIDO \
  --output /ruta/privada/aggregate-voice.json
\`\`\`

Para intentar evaluar FAR ~1 % se necesitan **por partición** al menos
30 genuinas (≥3 sesiones), 300 impostoras (≥20 personas distintas)
y 300 replay (≥20 fuentes distintas). Es un mínimo técnico
insuficiente, por sí solo, para seguridad contra ataques reales.

El evaluador produce FAR, FRR, replay y límites de confianza;
mantiene siempre \`authentication=NOT_CERTIFIED\`.
La independencia y consentimiento reales no pueden acreditarse
solo leyendo JSON. Hace falta verificar el protocolo de captura,
anti-replay/liveness, dispositivos, ruido, calidad del audio y la
comparabilidad de modelos Android/Python.

## 5. Puertas de seguridad

- Una puntuación alta no demuestra vida humana, posesión de dispositivo
  ni identidad. Ni el umbral de investigación ni las etiquetas de clase
  se despliegan a la APK automáticamente.
- Jamás aceptar audio no consentido ni almacenar audios de terceros en
  repositorios o servicios externos.
- Si faltan WAV reales, procedencia, sello independiente o revisión del
  corpus, la evaluación humana y el producto permanecen **NOT_CERTIFIED**.
- Las órdenes owner-only siguen bloqueadas; no cambiar
  \`VoiceMatchDecisionPolicy\` para devolver \`SpeakerEvidence.MATCHED\`
  en ausencia de una calibración segura, validada y anti-replay.
