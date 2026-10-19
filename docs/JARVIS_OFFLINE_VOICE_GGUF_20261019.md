# JARVIS – voz offline → GGUF → TTS (etapa experimental)

## Implementación real y alcance limitado

- Nueva opción visible **CONVERSAR OFFLINE CON IA · 30 MIN**. Requiere un GGUF previamente importado; la app no solicita Internet ni descarga un modelo.
- Solo inicia el servicio foreground de micrófono desde una Activity visible, tras un diálogo de consentimiento y los permisos de micrófono/notificaciones. Mantiene acción **DETENER**.
- Captura PCM con Vosk español local; en este modo emplea reconocimiento de texto libre, no la gramática restringida a hora/STOP.
- Preguntas completas que empiezan por **Jarvis** (o llegan dentro de una ventana de 8 s tras la palabra) pasan una política de interrogativas en español, longitud acotada y bloqueos de órdenes. Únicamente se envían al GGUF privado como **texto no confiable**.
- El motor llama.cpp genera en el worker existente y el servicio remite esa respuesta **solo al TextToSpeech local**. El reconocimiento se silencia durante la respuesta para evitar autoactivación; la captura recupera su escucha con protección breve contra eco.
- El texto generado jamás se convierte en comandos Android, permisos, Shizuku, Termux, acciones de GameHub ni solicitudes de red. No se guardan preguntas, respuestas ni audio por esta ruta.
- Un modelo Qwen2.5-0.5B-Instruct-Q4_K_M importado manualmente, con el SHA-256 oficial fijado, ahora recibe plantilla ChatML `system/user/assistant`. Los demás GGUF mantienen exactamente su formato anterior, salvo el SmolLM2-Instruct ya reconocido.

## Continuidad tras quitar la Activity

`stopWithTask=false` y `onTaskRemoved()` preservan **solo** la sesión extendida y visible, acotada a 30 minutos; las pruebas de 60 s siguen cerrándose. Android 14 exige iniciar el foreground micrófono mientras la Activity es visible y la plataforma/OEM puede terminar la captura. No existe autoarranque al reinicio ni escucha 24/7 oculta.

## Criterios de validación que faltan

1. Unit Kotlin: preguntas normales, dos turnos y bloqueo de órdenes, roles específicos por SHA.
2. Contratos Python y CI: builds Android shell, Android14 instrumentado, QA50, E2E GGUF base e Instruct sin regresiones.
3. **Falta prueba E2E real de audio de usuario → Vosk → GGUF → TTS**: el test unitario y la compilación no certifican latencia, audio reproducible ni corrección semántica.
4. **Falta prueba física Vivo V25 Pro**: micrófono con pantalla apagada y tarea retirada, permisos Funtouch, interrupciones, memoria y temperatura.
5. La biometría de propietario sigue **no calibrada**. El modo de preguntas solo consulta conocimiento público y no valida identidad.
6. Modelos mayores a 300 MB se descargan de la RAM al finalizar cada inferencia, para proteger dispositivos de memoria limitada. La latencia Qwen real todavía no está medida.
7. El PR anterior #87 mantiene fallo externo por límite diario de despliegues Vercel; no adjudicarlo a Android.

## Referencias

- Qwen oficial: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
- Huella del Q4_K_M en la revisión `df5bf01389a39c743ab467d734bf501681e041c5`: `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`
- Android 14 microphone FGS: https://developer.android.com/develop/background-work/services/fgs/service-types
