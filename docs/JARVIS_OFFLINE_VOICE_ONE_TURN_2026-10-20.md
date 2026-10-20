# JARVIS — conversación offline de una pregunta (Vosk → GGUF → TTS)

**Alcance:** integración funcional detrás de consentimiento explícito, sin
declarar conversaciones continuas, identificación del propietario ni
rendimiento certificado en Vivo V25 Pro.

## Cómo se activa

En OMEGA CHAT, importa voluntariamente un GGUF compatible mediante el
selector Android. Esta importación guarda una copia privada y verifica SHA-256.
El modelo **no se descarga de la red** en el dispositivo.

Pulsa **CONVERSAR OFFLINE CON JARVIS · 1 PREGUNTA** y confirma el diálogo.
Solo entonces se solicita una sesión foreground de micrófono de hasta
60 segundos. El servicio no puede ser lanzado por otras aplicaciones.
Di `Jarvis, ¿qué es un procesador?`, o primero `Jarvis` y dentro
de ocho segundos una pregunta. La escucha se inicia después de un tap
visible, nunca desde el arranque de Android.

## Orden real de operaciones

1. `NativeSpanishSpeechRunner` reutiliza `AudioRecord` y Vosk español
   incluido, con decodificación no restringida solo en esta sesión. En otras
   sesiones mantiene la gramática existente de hora/batería/STOP.
2. `OfflineVoiceQuestionGate` admite una pregunta corta tras el wake, rechaza
   órdenes de control del dispositivo, negaciones, comandos encadenados,
   caracteres de control y textos mayores que su límite; **NO identifica
   al usuario**. Detener permanece disponible.
3. El worker suelta `AudioRecord` y libera el reconocedor **antes** de
   entregar la transcripción temporal al servicio. No se guarda audio ni
   el texto de la pregunta en preferencias, archivos o registros.
4. `LocalGgufInference` verifica hash y modelo privado, obtiene una respuesta
   mediante llama.cpp/JNI en un worker y evita acciones del teléfono.
   Tiempo máximo de espera en servicio: 90 s; STOP revoca la sesión e
   inicia la cancelación cooperativa. No hay llamadas de red ni APIs de pago.
5. `OfflineVoiceAnswerPolicy` limita longitud, controles y textos de error;
   `TextToSpeech` selecciona solo voces españolas instaladas que no requieren
   red. El texto del modelo es **no verificado** y se reproduce, si está listo
   el motor local, sin invocar otras aplicaciones ni automatizaciones.
6. Al terminar o cancelar se destruyen los recursos, se retira la
   notificación de foreground y se libera el motor de inferencia.

**Límite de las pruebas:** CI valida el parser, compilación Android,
instrumentación del emulador y contratos de seguridad. Una ejecución de
Android 14 no acredita precisión del ASR, calidad del texto generado con un
modelo real, audio TTS audible, autonomía ni continuidad con pantalla
bloqueada en el Vivo V25 Pro. Esas mediciones exigen campaña E2E posterior.

## Implementación futura y bloqueos

- Conversación multivuelta y barge-in: pendientes; mantener STOP durante
  generación/TTS por notificación y botón, sin fingir cancelación por voz
  mientras el micrófono está cerrado.
- Voice Match: la inscripción sherpa-onnx y el almacén cifrado existen, pero
  NO habilitar `MATCHED` antes de calibrar FAR/FRR, falsos aceptados y replay
  en conjuntos de audio consentidos e independientes.
- Control del teléfono: conservar confirmación por cada operación y
  autoridades separadas de la transcripción/LLM. Nunca conectar texto
  generado directamente a Shizuku, Termux, Accessibility ni GameHub.
- Android 14+: el servicio de micrófono tiene que arrancar desde una
  actividad visible con permisos; su ejecución en segundo plano no es
  una garantía 24/7.
