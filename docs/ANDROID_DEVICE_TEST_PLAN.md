# JARVIS — protocolo de validación Android física

**Dispositivo objetivo:** un Android compatible API 26+ (por ejemplo, el teléfono habitual de pruebas). Pruebas con el propietario, sin root, Shizuku ni acceso remoto oculto. No recopilar contraseñas, pantallas bancarias ni datos de terceros.

## Artefactos
En la ejecución verde de **Android shell**, descargar:
- `jarvis-omega-debug-apk`: `app-debug.apk`.
- `jarvis-omega-android-test-apk`: `app-debug-androidTest.apk`.

Comandos desde un computador con Android Platform Tools instalado, tras autorizar depuración USB:
```bash
adb devices
adb install -r app-debug.apk
adb install -r app-debug-androidTest.apk
adb shell am instrument -w com.cardenaspiero255.jarvis.test/androidx.test.runner.AndroidJUnitRunner
```
Conservar el código de salida y el resumen de pruebas. Si se usa un emulador en vez del teléfono físico, registrarlo por separado.

## Matriz manual antes de declarar producción
1. **Inicio y rotación:** abrir/cerrar y rotar sin bloqueos; no se concede control de pantalla automáticamente.
2. **Voz:** probar TTS y detenerlo; iniciar el reconocedor mientras habla y confirmar que TTS se corta; abandonar la app durante un resultado tardío (no debe quedar consentimiento válido).
3. **Hands:** comprobar el permiso Android, el botón Inicio, la parada de emergencia, el reinicio del proceso y la revocación del servicio. Nunca habilitar acciones automáticas fuera del alcance.
4. **Vision:** elegir una imagen, OCR correcto en idioma español, imagen dañada, imagen grande, cambiar de app durante OCR; un resultado caducado no debe mostrarse.
5. **GameHub:** probar instalado y ausente; confirmar que solo se abre al pulsar el botón.
6. **Chat privado:** memoria desactivada por defecto; activar, guardar, cerrar/abrir, reautorizar lectura, borrar, confirmar que ya no hay historial y que la clave es nueva.
7. **Offline:** desactivar Internet; el chat local y recuperación del historial deben seguir funcionando. El reconocimiento de voz Android no se presume offline.
8. **Recursos:** batería, RAM, tiempo de arranque y uso de CPU con OCR repetido; no debe crecer memoria sin límite.
9. **Permisos y privacidad:** inspeccionar permisos concedidos, almacenamiento y tráfico con consentimiento; no afirmar ausencia total de tráfico de terceros sin medición.

Para cada paso registrar: modelo, versión Android, número de compilación, acciones, esperado, observado, logcat filtrado (sin PII), éxito/fallo y repetición. **No marcar como test físico aprobado por el simple hecho de construir la APK.**
