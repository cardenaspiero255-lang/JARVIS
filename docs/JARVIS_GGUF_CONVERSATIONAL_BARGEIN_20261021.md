# JARVIS — conversación GGUF interrumpible, fase experimental

## Comportamiento

Implementación adicional a #97, sobre el mismo micrófono Vosk ya empaquetado y
la protección AEC/STOP anterior. Mantiene intacta la sesión **una pregunta**.
Solo el botón explícito **CONVERSACIÓN GGUF · INTERRUMPIBLE · 30 MIN**
habilita el nuevo modo de prueba. La notificación foreground incluye
**DETENER**, incluso si la pantalla está bloqueada, hasta que Android
o Funtouch detenga el servicio.

Después de confirmar, decir «Jarvis, ¿qué es un satélite?» abre una
pregunta GGUF local, limitada por `LocalGgufPolicy`. Mientras el modelo
está procesando, el micrófono permanece abierto pero su entrada no puede
convertirse en órdenes ni nuevas preguntas. Cuando JARVIS responde,
la misma instancia de `AudioRecord` puede detectar otra frase como
«Jarvis, ¿cómo funciona la energía solar?» y cortar el TTS actual
para formularla al modelo local. Esto requiere
`AcousticEchoCanceler.isAvailable()` y que Android haya activado
realmente `AcousticEchoCanceler`; de lo contrario se mantiene el
bloqueo del audio durante TTS y el usuario debe esperar al fin
de la respuesta o pulsar DETENER.

**Límite de sesión:** 30 min como máximo, seis turnos como máximo;
la identidad por voz permanece UNAVAILABLE. Ningún turno ejecuta
herramientas, acciones de Android, Shell, Shizuku, mensajes ni URLs.
Los textos del usuario son transcripciones no confiables que entran
solo en una pregunta de un motor GGUF privado. STOP cancela TTS y
cierra la sesión con prioridad sobre la pregunta.

## Defensa en profundidad contra eco y carreras

- El motor de Barge-in solo lee segmentos **completos** de Vosk;
  las hipótesis parciales nunca causan interrupción.
- Requiere despertar con «Jarvis» en la **misma frase**,
  AEC activo y TTS reproduciéndose.
- Si el texto sintetizado contiene «Jarvis» o si la mayoría de
  términos de la pregunta están en la respuesta actual, se
  ignora por prudencia. Esto produce falsos negativos previstos
  y **no demuestra** separación acústica ni identificación del hablante.
- El cuerpo de la pregunta se vuelve a comprobar con
  `OfflineVoiceQuestionGate`, que excluye comandos del teléfono
  y stop; `BargeInStopPolicy` sigue teniendo prioridad.
- Cada TTS usa un identificador de generación y los callbacks
  anteriores no pueden quitar la protección contra eco de uno nuevo.
- Cada inferencia GGUF tiene un identificador `conversationGeneration`,
  watchdog y cancelación local; su respuesta tardía no reemplaza
  una pregunta posterior ni revive una sesión revocada.
- El modo de una sola pregunta mantiene su contrato original:
  liberar el micrófono antes de generar el texto con llama.cpp.
- Solo se conservan contadores y códigos de estado en diagnósticos.
  No se guarda ni distribuye PCM, transcripciones, embeddings o voces.

## Pruebas

`BargeInQuestionPolicyTest`: preguntas legítimas en español,
TTS reproducido como eco, cancelación sin AEC, hipótesis parciales,
ordenes compuestas maliciosas, silencio y longitud adversarial.
`tests/test_conversational_bargein_contract.py`: consentimiento,
límite de turnos, STOP prioritario, aislamiento GGUF y generaciones.
CI Android: prueba JVM, APK y 25 instrumentaciones en el emulador
cuando el commit correspondiente termine con todos los gates verdes.

**Pendiente obligatorio** para habilitarlo como función certificada:
evaluar acústica real en Vivo V25 Pro 5G con altavoz a distintos
volúmenes, habitación silenciosa y ruidosa, TTS que pronuncie «Jarvis»,
eco residual, auriculares/Bluetooth, activación falsa, batería,
calor, revocación inmediata, STOP sin AEC y pantalla apagada.
Una prueba de emulador y una cancelación acústica solicitada a Android
**no certifican** FAR, FRR ni inmunidad a reproducción.

## Procedencia

Conserva la licencia MIT y atribución original
`judejulius/jarvis`, especialmente la comparación de transcripción
frente al habla sintetizada implementada en `VoiceInput.kt`.
No se copiaron sus modelos de ASR ni autorizaciones de accesibilidad.
REA NO EJECUTADO: inspección de fuente abierta, no descompilación binaria.
