# JARVIS: fase 1 — generación local opcional por llama.cpp + GGUF

## Trabajo integrado en esta rama

1. `tools/prepare_llama_cpp.py` descarga **solo en compilación** la fuente
   oficial fijada en SHA `31288c9c66e59377f7788d920678a565bb89dd37`.
   Se verifica el HEAD y que el código no haya cambiado; no se usa `master`
   como dependencia flotante. Licencia MIT íntegra en el APK. Fuente:
   https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
2. Gradle + Android NDK compilan `jarvis_llm` para `arm64-v8a` (Vivo)
   y `x86_64` (emulador); CMake llama.cpp CPU-only, sin descarga de modelos.
3. `LocalGgufModelStore`: importación **opcional** mediante
   `ACTION_OPEN_DOCUMENT` y consentimiento visible, valida cabecera GGUF v2/v3,
   tamaño <=1,6 GB y SHA-256, copia el archivo a `filesDir/private_gguf`,
   detecta modificaciones antes de generar; botón para eliminar la copia.
   La importación puede requerir almacenamiento libre de 2x tamaño del archivo.
4. `LocalGgufInference` invoca JNI `llama_model_load_from_file`,
   `llama_tokenize`, `llama_decode`, `llama_sampler_sample`,
   `llama_token_to_piece`, desde un trabajador dedicado (nunca en hilo UI).
   Límites: contexto 1.024 tokens, 96 tokens de respuesta, CPU dos hilos,
   salida 2.000 caracteres en Kotlin, una consulta activa y cancelación
   cooperativa entre iteraciones.
5. En el chat hay acciones separadas **importar**, **preguntar local** y
   **eliminar modelo**. El diálogo determinista previo sigue funcionando sin
   modelo; no hay fallback de pago. Respuestas generadas se presentan como
   **texto no verificado**, solo tras un tap del usuario. No se envían a
   `SafeAutomationEngine`, `Shizuku`, `Termux`, `GameHub` o comandos.
   Se anula inferencia activa cuando se sale de la Activity y no se persiste
   automáticamente la respuesta generada.

## Seguridad y privacidad

- Sin permiso `INTERNET`, backend remoto, API key, cuenta ni suscripción.
- El modelo NO se descarga ni se selecciona automáticamente.
- El usuario debe elegir un GGUF cuyo **derecho de uso y distribución**
  haya comprobado. El runtime MIT no otorga licencias sobre los pesos.
- Modelos GGUF son entradas no confiables a código nativo; cabecera+SHA-256
  solo detectan formato básico e integridad, no autentican procedencia ni
  garantizan que todo archivo sea seguro. Priorizar modelos de fuente oficial.
- Limitaciones de RAM/tiempo del Vivo V25 Pro aún necesitan medir latencia,
  PSS pico, memoria del modelo, térmicas, fallos y consumo en el teléfono.
- Prompt no concede privilegios. No hay tool calling ni ejecución de
  instrucciones generadas. Android conserva Funtouch OS sin cambios.
- Cancelar durante una decodificación pesada no garantiza interrupción
  instantánea de la biblioteca nativa; se comprueba cada iteración.

## Evidencia antes de marcar completada

- `Contract tests`, `QA 50`, `Android shell`, `Android14 instrumentation`
  en verde con el **mismo HEAD**.
- `LocalGgufNativeInstrumentedTest` comprueba que la librería de JNI realmente
  se carga en Android14 y rechaza un modelo inexistente, y el camino de cancelación.
  Una prueba negativa **no es evidencia de generación correcta**.
- Hito adicional: E2E con un **GGUF pequeño, auténtico, con licencia y SHA
  fijados**, medir primera respuesta UTF-8 completa sin red en emulador.
- Ensayo físico posterior en el Vivo con mínimo dos modelos compatibles,
  prompts reales en español, latencia P50/P95 y RAM pico; cerrar fallos
  de arranque/agotamiento antes de habilitarlo como selección preferida.
- No certificar rendimiento, inteligencia, seguridad de cualquier GGUF,
  integración de herramientas ni la fase completa hasta cumplir los hitos.

## Compilar

```bash
python3 tools/prepare_llama_cpp.py \
  --dest apps/android/app/src/main/cpp/third_party/llama.cpp
sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"
gradle -p apps/android --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Nunca subir pesos GGUF, archivos privados o grabaciones a GitHub.


## Correcciones de concurrencia y validación auténtica (PR #84)

**P1: reserva del motor nativo.** `LocalGgufRunState` impide comenzar otra
generación hasta la salida definitiva del `finally` del trabajador. Cancelar
ahora señaliza `AtomicBoolean` sin usar el estado potencialmente prematuro
de `Future.cancel(true)`/`Future.isDone` como permiso para comenzar otro
trabajo. Se mantiene la cancelación cooperativa del JNI, que no promete
interrumpir instantáneamente un bloque de cómputo nativo.

**P1: vida del archivo modelo.** `LocalGgufModelStore.withVerifiedModel`
mantiene el monitor del modelo desde la validación SHA hasta que
`llama_model_free` haya concluido. Importar o borrar desde otro trabajador
espera a que ese lease termine. Es deliberadamente conservador: el botón
para borrar puede tardar si una generación aún está desmontando el modelo.

**Test de carreras:** `LocalGgufRunStateTest` comprueba que la cancelación
no concede una segunda ranura antes del fin real; y
`LocalGgufModelLeaseInstrumentedTest` verifica que un hilo que intenta
eliminar un archivo modelo queda bloqueado hasta liberar la lectura.

**Test E2E real, separado del APK de distribución:**
`Android GGUF real generation E2E` descarga exclusivamente **en el runner
de GitHub Actions** el GGUF `ggml-org/SmolLM2-135M-GGUF`
(`SmolLM2-135M-Q4_K_M.gguf`, aprox. 101 MB; licencia Apache-2.0),
comprueba SHA-256:
`e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5`
(publicado en https://huggingface.co/ggml-org/SmolLM2-135M-GGUF/blob/main/SmolLM2-135M-Q4_K_M.gguf).
Incorpora el archivo solo en el **APK instrumental temporal** mediante
`-PggufE2e=true`, y comprueba en el emulador Android 14:

1. Que los bytes copiados a almacenamiento privado tengan ese hash.
2. Que `LocalGgufModelStore` verifique de nuevo el archivo.
3. Que la API real `LocalGgufInference.ask()` invoque JNI y devuelva
   texto de tokens decodificados, no un mensaje de error o resultado fabricado.
4. Que el resultado se recoja mediante callback y se registre la duración.
5. Que falte cualquiera de esas condiciones, falle CI: el caso no puede ser
   omitido o reemplazado por una prueba de ruta inexistente.

No se guarda el GGUF en el repositorio, no se entrega a usuarios con JARVIS
y el workflow no adjunta el APK instrumental que contiene el modelo. El
origen del modelo se fija por checksum, pero el hash no es una garantía
de ausencia de vulnerabilidades del parser nativo.

**Alcance de la evidencia:** la prueba E2E confirma decodificación funcional,
NO que SmolLM2-135M sea un asistente preciso en español, ni el rendimiento
ni los falsos positivos de voz en el Vivo V25 Pro. Es una prueba de integración,
no un benchmark cognitivo ni una certificación física.
