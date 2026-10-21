# JARVIS — Importación de tres WAV en Vivo V25 Pro 5G

**Estado:** funcionalidad incluida en el código de la rama; NO certifica identidad.
El usuario debe elegir conscientemente sus tres muestras. Ningún audio,
contenido ni identidad se recopila silenciosamente.

## Uso desde el teléfono (Android 14)

1. Descarga tu paquete **privado** `JARVIS_Voice_Match_3_muestras_privadas.zip`
   en el Vivo. Descomprímelo con Archivos. No lo subas a GitHub, Google Drive
   público ni servicios de reconocimiento externo.
2. Instala una APK compilada desde este PR solo después de que todos sus
   checks hayan concluido correctamente.
3. En JARVIS pulsa **REGISTRAR MI VOZ · 3 MUESTRAS**.
4. Marca la casilla de **consentimiento local**.
5. Pulsa **IMPORTAR 3 WAV · 16 KHZ PCM16**. En el selector del sistema Android
   selecciona a la vez los tres archivos WAV descomprimidos. El selector
   puede necesitar mantener presionado el primer archivo para selección
   múltiple. No acepta el ZIP ni los M4A originales.
6. Mantén la pantalla de inscripción visible hasta ver el resultado. El
   importador verifica el formato y las tres huellas de archivo distintas,
   pasa los PCM a sherpa-onnx con el modelo ERes2Net ya incluido y genera una
   **plantilla cifrada** vinculada al SHA del modelo.
7. Si las muestras superan la coherencia interna, se muestra la **mínima
   similitud coseno entre los tres embeddings reales** y se indica que
   el perfil se guardó. Si son incompatibles, se rechazan sin autorizar.
8. Puedes pulsar **PROBAR MI VOZ · DIAGNÓSTICO** y grabar una frase NUEVA
   para observar una similitud orientativa. Esa prueba tampoco autentica.
   **BORRAR PERFIL Y REVOCAR** borra la plantilla y cancela lo que esté activo.

## Controles implementados

- Solo el **selector oficial de Android** con `ACTION_OPEN_DOCUMENT`,
  `CATEGORY_OPENABLE`, `EXTRA_ALLOW_MULTIPLE` y `EXTRA_LOCAL_ONLY`.
  Solo se aceptan exactamente tres `content://` distintos.
- No se piden permisos de almacenamiento adicionales ni se conserva
  el acceso a los documentos del sistema tras procesarlos.
- WAV RIFF PCM **mono, 16 kHz, 16-bit, 1,5–10 s** y hasta 500 kB. Se
  rechazan archivos truncados, chunks malformados, silencios y duplicados
  (SHA-256 del archivo completo). No hay transcodificación oculta.
- Decodificación **en RAM** con borrado de buffers al finalizar, fallar o
  cancelar. La única persistencia autorizada es la plantilla cifrada
  con Android Keystore; no se guarda un WAV ni un embedding en GitHub.
- El selector puede pausar/detener temporalmente la Activity. No conserva
  audio previo durante esa transición. Fuera de él, salir de pantalla
  cancela inmediatamente las capturas y la importación.
- Un contador de **época de cancelación** impide que un worker antiguo
  vuelva a habilitarse o actualice la interfaz después de retirar permiso,
  reiniciar inscripción o cambiar de pantalla.
- `SpeakerEnrollmentPolicy` aplica la consistencia >= 0,78 entre las
  tres embeddings, pero **esa cifra no es un umbral de autenticación**.
  El estado de autorización por voz continúa `NOT_CERTIFIED` y
  `VoiceMatchDecisionPolicy` no concede `MATCHED`.

## Límites que NO debe ocultar la APK

Este paso **no** prueba que alguien sea el propietario. Las tres muestras
enviadas para inscripción no pueden reutilizarse para medir FRR/FAR.
Faltan ensayos reales independientes: propietario en sesiones nuevas,
impostores con consentimiento, fuentes de reproducción, anti-replay,
holdout sellado y certificación física en Vivo V25 Pro. No se deben
inventar puntuaciones de CI ni usar el audio de otra persona sin permiso.

Los tests unitarios usan PCM sintético como datos de entrada para comprobar
parsing; las suites Android ejecutan el motor nativo según su propio
contrato. **No** afirman haber ejecutado tus tres grabaciones reales.
