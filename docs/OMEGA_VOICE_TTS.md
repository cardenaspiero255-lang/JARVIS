# Omega Voice — respuestas en español con parada inmediata

## Alcance de la fase

- Reproducción de respuestas locales **solo bajo pulsación explícita**.
- Habilitación de voz por sesión; al reiniciar JARVIS vuelve a estar detenida.
- Motor TextToSpeech del propio Android, selección de idioma es-CL y aviso
  cuando faltan datos de voz.
- Botón **DETENER RESPUESTA DE VOZ** y cierre del motor en el ciclo de vida
  de la Activity. Salir de la app detiene la voz.
- Mensajes permitidos de una lista fija, para que texto de páginas, otros
  programas o modelos no se recite sin aprobación.
- Descubrimiento del servicio TTS por el mecanismo oficial de Android, sin
  pedir permisos de grabación continua.

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

- En Android aparecen los botones **PERFIL: ESPAÑOL LOCAL** y **PERFIL: ASISTENTE BRITÁNICO LOCAL**. Cambiar de perfil detiene la síntesis y exige pulsar de nuevo ACTIVAR RESPUESTAS DE VOZ.
- El perfil británico selecciona **una voz de Android `en-GB` que no requiera conexión**. El timbre concreto depende del motor TTS y de las voces instaladas en el teléfono; puede no tener voz británica offline disponible.
- Las frases de prueba están predefinidas y son en inglés británico; el reconocedor para comandos sigue usando `es-CL`. La voz no imita, clona ni garantiza reproducir a Paul Bettany ni ninguna voz de una película.
- Si no se encuentra una voz local compatible, la aplicación **no cambia en secreto a una voz online** ni envía texto a un proveedor remoto. Informa que faltan datos TTS y no permite hablar con ese perfil.
- El usuario mantiene los controles **ACTIVAR**, **LEER ESTADO** y **DETENER**. No se introduce micrófono permanente, grabaciones, clonación de voz, respuestas arbitrarias ni automatización privilegiada.

## Validación pendiente en dispositivo

La selección de voces y la parada inmediata deben verificarse en un Android real con voz británica `en-GB` instalada. Los tests JVM cubren la política de selección, pero no prueban que el Vivo V25 Pro ofrezca una voz offline de calidad ni que el audio se reproduzca correctamente.
