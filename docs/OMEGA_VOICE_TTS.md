# Omega Voice — respuestas en español con parada inmediata

## Alcance de la fase

- Reproducción de respuestas locales **solo bajo pulsación explícita**.
- El TTS local se prepara al abrir la app sin pedir permiso de micrófono ni reproducir automáticamente; el usuario pulsa PROBAR AUDIO o LEER ESTADO para escuchar una respuesta.
- Motor TextToSpeech del propio Android, selección de idioma es-CL y aviso
  cuando faltan datos de voz.
- Botón **DETENER RESPUESTA DE VOZ** y cierre del motor en el ciclo de vida
  de la Activity. Salir de la app detiene la voz.
- Mensajes permitidos de una lista fija, para que texto de páginas, otros
  programas o modelos no se recite sin aprobación.
- Reconocimiento de voz mediante pulsación: la app solicita RECORD_AUDIO solo la primera vez que se abre (si está pendiente), y permite volver a pedirlo desde HABLAR si fue denegado; concederlo no inicia ninguna escucha.
- Reconocimiento integrado mediante SpeechRecognizer con alternativa explícita de ventana del proveedor Android; cualquier intento se cancela al salir o bloquear la sesión.
- Los motores ASR configurados por Android pueden requerir red, según el proveedor; nunca hay servicio de micrófono permanente.

## Límites y privacidad

No contiene wake-word, escucha permanente, TTS sin conexión garantizado,
streaming del modelo, conversaciones en segundo plano ni voz libre. El
reconocedor y sintetizador configurados en Android pueden usar servicios
externos según su motor y sus ajustes.

La compilación de APK y las pruebas Kotlin no sustituyen una verificación
manual en un dispositivo real, con motor TTS instalado y opción de salida
de audio disponible. El botón de parada se debe probar también con pantalla
bloqueada, cambio de app y llamadas entrantes.

## Pruebas

`gradle -p apps/android --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
El workflow Android shell genera el artefacto de debug solo si la compilación
y las pruebas terminan correctamente.

Siguiente fase: TTS de resultados verificados, ASR/TTS offline compatible
cuando exista, y cancelación integrada con la orquestación. No autorizar
reproducción automática de texto de terceros.

## Perfil adicional: asistente británico de voz local

- En Android aparecen los botones **PERFIL: ESPAÑOL LOCAL** y **PERFIL: ASISTENTE BRITÁNICO LOCAL**. Cambiar de perfil detiene la síntesis y prepara la nueva voz local con la pulsación del perfil; la reproducción sigue siendo manual.
- El perfil británico selecciona **una voz de Android `en-GB` que no requiera conexión**. El timbre concreto depende del motor TTS y de las voces instaladas en el teléfono; puede no tener voz británica offline disponible.
- Las frases de prueba están predefinidas y son en inglés británico; el reconocedor para comandos sigue usando `es-CL`. La voz no imita, clona ni garantiza reproducir a Paul Bettany ni ninguna voz de una película.
- Si no se encuentra una voz local compatible, la aplicación **no cambia en secreto a una voz online** ni envía texto a un proveedor remoto. Informa que faltan datos TTS y no permite hablar con ese perfil.
- El usuario mantiene los controles **HABLAR**, **PROBAR AUDIO**, **LEER ESTADO** y **DETENER**. No se introduce micrófono permanente, grabaciones, clonación de voz, respuestas arbitrarias ni automatización privilegiada.

## Validación pendiente en dispositivo

La selección de voces y la parada inmediata deben verificarse en un Android real con voz británica `en-GB` instalada. Los tests JVM cubren la política de selección, pero no prueban que el Vivo V25 Pro ofrezca una voz offline de calidad ni que el audio se reproduzca correctamente.

## Indicadores de Android

- La captura de pantalla por MediaProjection sigue solicitando autorización del sistema en cada sesión; no se inician capturas de pantalla al abrir la app. Su notificación y los indicadores del sistema no se ocultan.
- El icono azul de accesibilidad puede proceder del acceso directo flotante configurado en Android. El usuario puede quitar el acceso directo desde Ajustes > Accesibilidad sin convertir Omega Hands en un servicio oculto. La app no activa accesibilidad por sí sola.
