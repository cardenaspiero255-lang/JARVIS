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
