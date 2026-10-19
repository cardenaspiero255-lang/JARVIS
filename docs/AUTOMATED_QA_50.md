# JARVIS — pruebas automáticas de las 50 mejoras (sin instalar APK)

Este documento describe QUÉ comprueba GitHub Actions automáticamente, qué
resultado aparece como evidencia y qué **no** puede declararse probado.

## Automatización ejecutable

- **En cada PR/push a main**:
  - `Contract tests`: contratos Python, seguridad, adversariales, privacidad,
    integración de repositorios, evaluación de FAR/FRR sintética.
  - `Android shell`: Gradle unit tests de todas las clases, construcción APK
    debug y test APK.
  - `Android 14 instrumentation`: emulador Android 14 real, modelos
    ONNX/Vosk, permisos/privacidad, pantalla bloqueada, Keystore y ahora
    estrés nativo KWS con reinicialización, silencio y ruido sintético.
  - `JARVIS QA 50 coverage`: repite el corpus Python, genera inventario
    de todas las 50 funcionalidades y registra los huecos de cobertura.
- **Todas las noches**: se repite el inventario completo y la instrumentación
  Android 14, sin necesidad de instalación ni pulsaciones del propietario.
- Se guardan logs del emulador e informes JSON/Markdown como artefactos
  de GitHub Actions. Cada fallo genera check rojo, no se omite ni se
  interpreta como mejora terminada. La fusión requiere revisar el HEAD
  más reciente con los checks pertinentes verdes.

GitHub: Actions del repositorio / PR / checks. El artefacto
`jarvis-50-feature-qa-evidence` contiene `features.json` y `summary.md`.
`jarvis-android14-native-diagnostics` conserva logs de fallos instrumentados.

## Cobertura de 50 mejoras

`tools/qa_coverage_matrix.py` parsea
`docs/SSS_PLUS_50_TRACKER.md`, exige IDs 1–50 únicos y referencias reales
a tests por mejora. Las 18 sin prueba asociada aparecen como
`NO_MAPPED_TESTS`; las demás como `TEST_DEFINED_NOT_EXECUTION_EVIDENCE`.
Una prueba existente no es una ejecución aprobada, una compilación no acredita
el comportamiento y **nunca** se informa certificación de las 50 mejoras.

El inventario es deliberadamente conservador: varias pruebas asociadas a una
mejora parcial cubren solo una parte, no todos sus requisitos. Se necesitan
tests adicionales para las capacidades todavía inexistentes antes de
promoverlas a `Completada`.

## Pruebas de estrés y ataques

- `VoiceAdversarialMatrixTest` (JVM): 10.000 ciclos de activación/expiración
  simulados, 30 minutos virtuales de sesión y muestras, acciones negadas,
  parada de emergencia incluso con identidad no verificada, barreras de
  comandos protegidos, rechazo biométrico sin calibración.
- `NativeVoiceStressInstrumentedTest` (Android): modelo ONNX real
  cargado desde la APK con SHA verificado; 3 ciclos abrir/inferir/cerrar,
  14 segmentos de silencio por ciclo, PCM inválido, ruido pseudorrandom
  sintético, barrera contra autorización automática.
- `LockScreenAudioContinuityInstrumentedTest` (Android): pantalla del
  emulador verdaderamente apagada y conteo de muestras efectivas capturadas
  por el micrófono cuando `PowerManager.isInteractive == false`.
- `test_owner_biometric_holdout_evaluation`: separación de corpus
  `calibration/holdout`, voces ajenas/replay por clase y consentimientos,
  datos duplicados/no finitos, cómputo con límite de confianza.

**Ni el ruido sintético ni embeddings artificiales sirven para estimar
precisión de voz real.** No se recopila voz ajena sin consentimiento ni se
introduce un umbral que habilite `SpeakerEvidence.MATCHED`.

## Límites que NO resuelve un emulador

| Validación | Sin instalar APK | Estado |
|---|---|---|
| Crash JNI, compilación, regresiones, parsing, seguridad básica | Sí | Ejecutada en GitHub Actions |
| Vida de sesión, pantalla bloqueada Android 14 genérico | Sí | Emulador; no Funtouch real |
| 30 min reales, batería/latencia/thermal de Vivo V25 Pro | No | DEVICE_REQUIRED |
| Reconocimiento «Jarvis» en español chileno con voces auténticas | Requiere corpus consentido | NO_REAL_AUDIO_EVIDENCE |
| FAR/FRR y anti-replay del propietario | Requiere muestras nuevas consentidas e independientes | NOT_CERTIFIED |
| Shizuku, Dhizuku, OwnDroid y Termux con sus permisos instalados | Requiere entornos/accesorios conectados o un laboratorio físico | NO_E2E_DEVICE_EVIDENCE |
| Activación automática 24/7 | Android impone restricciones; no garantizada | NOT_IMPLEMENTED_CERTIFIABLY |

Esta CI detecta muchas regresiones antes de que el usuario pruebe la APK.
No puede arreglar código de forma autónoma solo por tener un check rojo;
cualquier corrección debe ir en un commit nuevo y volver a pasar todos los
checks. No se deben enviar APKs declaradas 100% perfectas con tests pendientes.

## Reproducción local opcional

```bash
python3 -m unittest discover -s tests -v
python3 tools/qa_coverage_matrix.py \
  --json-out build/qa-coverage/features.json \
  --markdown-out build/qa-coverage/summary.md
cd apps/android
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
# Los Android instrumented tests se ejecutan automáticamente en GitHub CI.
```
