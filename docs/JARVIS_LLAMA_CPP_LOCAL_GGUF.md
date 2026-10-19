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
