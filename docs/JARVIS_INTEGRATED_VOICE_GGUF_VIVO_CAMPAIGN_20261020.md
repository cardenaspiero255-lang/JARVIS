# JARVIS — campaña integral de voz, modelo GGUF y Vivo (2026-10-20)

**Estado inicial:** CORPUS FÍSICO/AUDIO PRIVADO PENDIENTE, producto **NOT_CERTIFIED**.
Este protocolo une evaluadores reales existentes; no genera voces, métricas ni
pruebas sintéticas que aparenten resultados físicos. Las ejecuciones de CI,
el emulador y la instalación de bibliotecas no constituyen certificación acústica.

## 1. Ya automatizable sin reinstalar JARVIS en el teléfono

- `python3 -m unittest discover -s tests -v`: contratos, STOP, permisos,
  gramática, Voice Match, anti-replay y consistencia de informes.
- Android 14 CI: reproducción sintética/no humana y estabilidad de JNI, prueba
  del micrófono con pantalla apagada en emulador, `assembleDebug`.
- `.github/workflows/android-gguf-e2e.yml`: prueba separada de generación **real**
  con SmolLM2-135M Q4_K_M verificado por SHA-256. Solo descarga el modelo al
  host de CI. No incluye voz humana ni prueba de audio TTS.
- `python3 tools/jarvis_campaign_gate.py --output /tmp/jarvis-campaign.json`
  muestra **PENDING** en ausencia de registros, no resultados inventados.

El evaluador biométrico ahora rechaza un mismo **origen de grabación replay**
usado en calibración y holdout, incluso cuando esa grabación no aparece como
fila de prueba. El test de regresión ejecuta esta condición con metadatos
sintéticos, **no** con voces.

## 2. Evidencia auténtica pendiente, en privado

| Componente | Prueba real | Criterio |
| --- | --- | --- |
| Wake «Jarvis» | `tools/evaluate_kws_real_wav.py` | WAV mono PCM16 16 kHz, hashes correctos, casos wake y no-wake de *holdout* consentido |
| Identidad | `tools/evaluate_owner_biometric_holdout.py` | Sesiones independientes, impostores, replay, split fijado, umbral solo en calibración |
| GGUF físico | `tools/run_vivo_gguf_qa.sh` | Único Vivo V25 Pro por ADB, modelo oficial SHA fijado, 5 inferencias reales, latencia/PSS |
| Ruta hablada | Sesión funcional explícita | Micrófono → Vosk → GGUF → TTS audible, STOP, cancelación, sin conexión y sin comandos del sistema |
| Funtouch | Ensayo de pantalla bloqueada | Micrófono/notificación y muestras a 1, 5, 15 y 30 min; verificar revocación y ahorro energético |
| Energía y calidad | Instrumentación física consentida | Latencia P50/P95, memoria, batería/temperatura reales, adversarios y degradaciones |

La aplicación ya contiene la **ruta opcional de una pregunta** Vosk → GGUF →
TTS (PR #91), pero el benchmark antiguo de cinco generaciones GGUF **no**
ejercita esa ruta y conserva un marcador heredado de «no implementada»;
interpretarlo como **NO MEDIDA POR ESA SUITE**, no como estado de la APK.

### Voz del propietario: mínimos para evaluar, no para autorizar

El evaluador exige por partición al menos 30 intentos genuinos de distintas
sesiones, 300 impostores de ≥20 personas y 300 ataques replay de ≥20 orígenes.
Son dos particiones, calibración y holdout; impostores, audio, sesiones y
orígenes replay deben permanecer independientes. El sello del holdout se fija
antes de elegir umbral; no usar el mismo audio para inscripción y prueba.
Se requieren consentimientos genuinos y metadatos conservados en un almacén
privado. Incluso un reporte favorable mantiene `authentication=NOT_CERTIFIED`
hasta auditoría de procedencia, anti-replay real, baseline y hardware.
**Nunca habilitar `SpeakerEvidence.MATCHED` por puntuaciones sintéticas.**

## 3. Una campaña física final, no reinstalaciones sucesivas

Solo cuando el desarrollo esté estabilizado, conectar voluntariamente el
Vivo a un computador autorizado una vez. El script ya existente exige la
opción explícita, rechaza emuladores y modifica únicamente la APK de depuración:

```bash
bash tools/run_vivo_gguf_qa.sh --accept-debug-test-model-reset
```

Produce `gguf-qa-results.json`, `gguf-qa-results.md` y, desde esta revisión,
`physical-campaign-summary.json`, sin voz, claves ni identificadores USB
en el resumen agregado. No mide automáticamente lo que aún no está instrumentado
(pruebas acústicas, anti-replay, llamadas, batería ni pantalla bloqueada).

En el computador autorizado, procesar los informes **fuera del repositorio**:

```bash
python3 tools/jarvis_campaign_gate.py \
  --gguf /ruta/privada/gguf-qa-results.json \
  --owner /ruta/privada/aggregate-voice.json \
  --kws /ruta/privada/kws-evidence.json \
  --output /ruta/privada/jarvis-campaign-summary.json
```

El resumen excluye WAV, transcripciones, scores individuales, hashes de
grabaciones, pseudónimos y serial del teléfono. Solo admite estructuras
coherentes y marca la evidencia como **reportada, pendiente de auditoría**;
no puede probar por sí mismo que un informe sea auténtico.

## 4. Puertas no negociables

1. Nunca subir muestras, etiquetas privadas, reportes individuales o tokens
   a GitHub, Actions ni Vercel.
2. Si un workflow falla, permanece en rojo. No eliminar, omitir ni
   suplantar checks de seguridad.
3. El texto generado por GGUF es información no confiable: no ejecutar
   comandos Android, Shizuku, Termux, accesibilidad o GitHub a partir de él.
4. En ausencia de hardware/audio/consentimiento auténticos, los campos
   `real_audio_voice_e2e_verified`, `vivo_full_session_verified` y
   `owner_actions_authorized` permanecen **false**.
5. Un resultado de CI «success» solo acredita lo que el test ejecutó;
   este conjunto NO certifica JARVIS en el Vivo V25 Pro.
