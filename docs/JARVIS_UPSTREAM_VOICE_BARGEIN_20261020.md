# JARVIS — interrupción de voz con supresión de eco (PR #97)

**REA NO EJECUTADO.** Esta adaptación procede de inspección del código
abierto `judejulius/jarvis`, no de descompilar una APK. La lógica de
`VoiceInput.kt` usa cancelación de eco, comparación de texto TTS frente
a transcripción y cancelación de audio. Se conserva MIT y copyright del
autor en `apps/android/app/src/main/assets/legal/JUDEJULIUS_JARVIS_MIT_NOTICE.txt`.

## Alcance integrado

La intervención se limita a la sesión explícita **ACTIVAR ESCUCHA LOCAL ·
30 MIN** de JARVIS, mientras Android sintetiza una respuesta local corta.
Se puede intentar interrumpir diciendo **«Jarvis, para»** o
**«Jarvis, detente»**. La acción reconocida exclusivamente cancela TTS
y termina la sesión local, como el botón DETENER de la notificación.
No abre aplicaciones, no envía mensajes, no interpreta instrucciones,
no reabre una sesión nueva y no da permisos de voz del propietario.

Se usa el micrófono de la sesión preexistente y **un segundo Vosk
Recognizer, pero NO otro AudioRecord**. Se intenta activar
`AcousticEchoCanceler` al construir `AudioRecord` con fuente
`VOICE_COMMUNICATION`. Si Android no acredita AEC activo, no se
permite análisis de micrófono durante TTS: prevalece el aislamiento
de audio anterior y DETENER en la notificación.

Si AEC está activo, Vosk recibe muestras de audio sin grabarlas en
disco y la rama BARGE-IN **solo** observa frases completas. La política
`BargeInStopPolicy` exige coincidir exactamente con una de las frases
STOP, rechaza transcripciones largas/con caracteres de control y
bloquea STOP cuando el texto pronunciado por JARVIS contiene términos
susceptibles de producir ese eco (por ejemplo «Jarvis» o «para»).
Esta decisión es conservadora: sacrifica falsos negativos antes que
generar falsas interrupciones. No hay detección de locutor, anti-replay
ni autenticación del dueño.

Los resultados de voz durante TTS no entran jamás en
`PreviewWakeTurnGate` ni `OfflineVoiceQuestionGate`; se descartan
los PCM temporales. Los recognizers se cancelan/liberan en STOP, al
cambiar de TTS, cuando expira la sesión y por la orden DETENER.
Un identificador de generación de reproducción impide reusar un estado
Vosk de una respuesta anterior; el temporizador pos-TTS no desactiva la
protección de eco de un turno más reciente.

**La conversación GGUF de una sola pregunta no se modifica**:
cierra el micrófono antes de inferir y responder, por lo que no promete
barge-in. Los comandos por voz durante una respuesta siguen bloqueados.
La escucha de 30 min continúa siendo voluntaria y lleva aviso persistente.

## Pruebas y límites

Los tests Kotlin de `BargeInStopPolicyTest` comprueban coincidencia
exacta en español, supresión del propio texto TTS, rechazo de frases
compuestas con comandos, requisito AEC activo y recepción de frase
completa. Los tests Python de contrato confirman que STOP solo corta
TTS y no concede permisos. Android shell y el emulador verifican
compilación y las pruebas disponibles. **Estos tests no prueban
precisión acústica con la voz del propietario ni con altavoz real.**

Falta un ensayo físico voluntario en Vivo V25 Pro 5G Android 14 con
reproducción TTS, frases «Jarvis, para», silencio, ruido,
propia voz desde altavoz y TTS pronunciando «Jarvis, para», además de
pantalla bloqueada, notificación y cambios rápidos de sesión. Una
única sesión o la disponibilidad de AEC no certifican que el sistema
sea inmune a falsas activaciones o ataques replay.

## Por qué no se copió el resto de judejulius/jarvis

Su aplicación ofrece interrupciones de conversación general y control
de múltiples aplicaciones. Nuestro JARVIS no dispone aún de evaluación
independiente de liveness, FAR/FRR, diarización y riesgo acústico.
Copiar esos privilegios o habilitar cualquier frase como instrucción
sería contrario a los controles existentes. Se reutiliza únicamente
la idea acotada de **supresión de eco + STOP sin autoridad**.
