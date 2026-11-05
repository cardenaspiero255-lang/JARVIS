# JARVIS — fase 3: modelo Instruct y medición factual exploratoria

## Hipótesis y límites

El modelo original del benchmark (#85/#86) `SmolLM2-135M-GGUF` NO está
etiquetado como la variante Instruct. En el emulador hubo 1/4 coincidencias
léxicas en preguntas españolas, que NO son 25 % de precisión semántica.

Esta fase integra el formato de conversación adecuado para UNA variante
Instruct comprobada mediante SHA-256, sin tocar los modelos importados que
no hayan sido reconocidos. Como los modelos SmolLM2 indican principalmente
inglés en su tarjeta oficial, la variante Instruct NO garantiza una
mejora del español ni reemplaza un futuro modelo multilingüe.

## Fuente exacta del fixture opcional

- Repositorio: https://huggingface.co/unsloth/SmolLM2-135M-Instruct-GGUF
- GGUF: `SmolLM2-135M-Instruct-Q4_K_M.gguf`.
- SHA-256 de los pesos: `ed5fa30c487b282ec156c29062f1222e5c20875a944ac98289dbd242e947f747`.
- Licencia publicada: Apache-2.0.
- Fuente de plantilla de roles: configuración de HuggingFaceTB
  `SmolLM2-135M-Instruct`, `<|im_start|>role\n...<|im_end|>`.
- La selección e importación al teléfono continúa siendo voluntaria;
  el CI descarga el modelo solo a un APK INSTRUMENTAL efímero.

## Implementación

`LocalGgufPromptFormatter` usa `system/user/assistant` en ChatML
ÚNICAMENTE para el SHA fijado. Los demás pesos conservan el prompt
anterior en texto simple. `LocalGgufPolicy` rechaza delimitadores
reservados aportados por usuarios para evitar suplantación superficial de
roles. Nada del LLM activa permisos, herramientas, Shizuku, Termux, red o
TTS automáticamente.

El workflow `Android GGUF Instruct Spanish QA` recopila cuatro respuestas
reales, con el mismo conjunto corto de preguntas del benchmark original:
capital de Chile, 2+2, planeta rojo y definición de procesador.
Solo persiste ID, coincidencia de palabra clave, latencia y longitud;
no persiste texto generado ni audio. El test FALLA si faltan pesos/hash,
inferencia o datos de las cuatro preguntas. No exige un umbral numérico
arbitrario antes de observar el modelo.

## Criterios de aceptación

- CI del mismo HEAD: contratos, QA50, Android build, Android14 y E2E
  de generación base ya existentes, más Instruct E2E independiente.
- Contar sin red ni servicios de pago las cuatro respuestas originales
  del modelo Instruct, con SHA original verificado.
- Informar comparativa con #85/#86, **aclarando** que la coincidencia léxica
  puede subir por patrones espurios. No certificar exactitud semántica sin
  un corpus más amplio y una revisión verdaderamente independiente.
- No elegir automáticamente el nuevo modelo ni declarar mejoras de voz.
- Prueba física del Vivo V25 Pro, temperatura, batería y ASR→LLM→TTS
  permanecen pendientes hasta una sola campaña final autorizada.
