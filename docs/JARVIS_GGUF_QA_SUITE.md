# JARVIS: pruebas integradas de GGUF, español, memoria y límites de voz

**Estado de esta batería:** resultados de emulador automatizados por CI.
**No certifica:** Vivo V25 Pro, temperatura/batería reales, FAR/FRR del propietario
ni conversación micrófono → GGUF → voz (todavía no conectada).

## Qué mide realmente sin instalar otra APK en el teléfono

Workflow: `.github/workflows/android-gguf-e2e.yml` (Android 14).

1. Descarga **solo en la máquina de compilación** el GGUF oficial
   `ggml-org/SmolLM2-135M-GGUF`, Q4_K_M, Apache-2.0, **101.016.128 bytes**.
   Rechaza pesos con SHA-256 distinto de
   `e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5`.
   El modelo está solo en el APK de instrumentación, no en la APK de JARVIS.
2. Compila JARVIS para Android 14, desactiva Wi-Fi y datos en el emulador
   (Gradle del **host** aún puede resolver dependencias).
   JARVIS no solicita el permiso Android `INTERNET`.
3. Copia el GGUF a almacenamiento privado de la app aislada, valida SHA-256,
   hace **5 generaciones nativas reales** en el emulador (sin resultados simulados).
   La primera se reporta por separado; las otras cuatro se consideran
   solicitudes consecutivas. El runtime carga el modelo en cada solicitud.
4. Mide **tiempo transcurrido por solicitud** con reloj monotónico y
   calcula P50 (mediana) y P95 (orden estadístico de las observaciones).
   Con solo cuatro muestras repetidas, esos percentiles **son exploratorios**.
   Es tiempo total de respuesta, *no* tiempo al primer token ni tokens/s:
   el JNI actual no reporta el número exacto de tokens decodificados.
5. Muestrea el **PSS del proceso** aproximadamente cada 125 ms con Android
   `Debug.getPss()`, incluyendo memoria nativa residente, además del pico
   aproximado del heap Java. No representa RAM total del teléfono ni energía.
6. Examina cuatro preguntas fijas en español: capital de Chile, 2+2,
   planeta rojo y procesador. Publica **coincidencia léxica por pregunta**
   con palabras clave congeladas. Un match no prueba una respuesta correcta;
   una respuesta incorrecta puede contener una palabra clave. NO calificar
   esto como exactitud factual ni precisión/recall/F1 de un asistente completo.
7. Prueba rutas de seguridad: voz/LLM no puede otorgar permisos para comandos,
   TTS local no lee texto libre solo por una orden desconocida, y STOP se
   mantiene. El flujo micrófono → GGUF → TTS sigue **NO IMPLEMENTADO**;
   por ello el informe marca `voice_e2e_verified=false`.

## Reportes reales y condiciones para aprobar

La suite falla cuando: falta el fixture validado, se omite la instrumentación
real, una generación no devuelve texto, hay errores JNI, no se obtiene JSON
del paquete Android, los valores de memoria/tiempo son inválidos o los datos
de respuestas/voz se declaran certificados sin evidencia.

Artefacto: `jarvis-real-gguf-e2e-evidence` en el workflow GitHub Actions.
Dentro de `apps/android/app/build/diagnostics/gguf-e2e/`:
- `gguf-qa-raw.json`: mediciones originales, sin respuestas textuales ni audio.
- `gguf-qa-results.json`: percentiles, PSS, rúbrica y estado de certificación.
- `gguf-qa-results.md`: resumen legible con las limitaciones.
- `summary.txt`: evidencia de ejecución real.

El analizador `tools/gguf_qa_report.py` **rechaza** JSON incompleto, muestras
vacías, valores imposibles y afirmaciones de voz/propietario no verificadas.
`tests/test_gguf_qa_report.py` comprueba la lógica estadística y el
rechazo de falsos positivos.

El informe reporta siempre `NOT_CERTIFIED` para el producto.
No confundir `CI success` con que la aplicación sea estable en Vivo.

## Perfil físico: una sola campaña posterior en Vivo V25 Pro

Este **paso NO se ejecuta en GitHub**, porque allí no está conectado el Vivo.
Cuando corresponda una prueba física voluntaria, se puede conectar una vez el
Vivo V25 Pro (normalmente `V2158`) con USB y depuración Android autorizada.
Con SDK/NDK, Gradle y Python instalados en el PC:

```bash
bash tools/run_vivo_gguf_qa.sh --accept-debug-test-model-reset
```

El script:
- Rechaza emuladores y equipos cuyo fabricante/modelo no coincidan.
- Aísla el ensayo en el paquete de depuración
  `com.cardenaspiero255.jarvis.handsfreepreview`, no el JARVIS normal.
- **Reemplaza el GGUF dentro de ese paquete de pruebas**; por ello requiere
  confirmación explícita en la línea de comandos.
- Registra una sesión USB local identificada por hash del serial, **sin subir
  identificadores ni archivos a GitHub**, y aplica la misma rúbrica y métricas.
- No desactiva Internet, Funtouch ni funciones del teléfono real.

Al completar esta campaña habrá medidas directamente observadas del Vivo;
su procedencia es un reporte **local de sesión USB**, no certificación
independiente de rendimiento. No ejecutar ahora ni pedir instalaciones
reiteradas para terminar el desarrollo de las demás funciones.

## Pendiente para «probarlo todo» de forma real

- **Voz natural:** integrar conscientemente ASR → LLM → TTS, evitando que
  el texto del modelo o un ASR espurio active Shizuku/Termux/GameHub.
  Solo después añadir una prueba E2E de audio con voz offline instalada.
- **Wake y propietario:** ensayos acústicos con voces consentidas reales,
  ruido/eco/replay, calibración FAR/FRR y holdout independiente.
- **Vivo/Funtouch:** continuidad con pantalla bloqueada, latencia,
  batería (medidor externo o telemetría calibrada), térmicas, ANR y
  recuperación de sesión, con modelo GGUF adaptado al presupuesto de RAM.
- **Calidad:** corpus representativo ciego, revisiones humanas o
  evaluadores independientes; el tiny SmolLM2 de 135M es un **fixture**
  de ejecución, no la elección definitiva de inteligencia de JARVIS.

No crear etiquetas positivas artificiales ni asignar un porcentaje de
«precisión real» a partir de coincidencias de palabras.
