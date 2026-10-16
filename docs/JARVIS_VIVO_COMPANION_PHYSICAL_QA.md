# JARVIS OS — Pruebas físicas obligatorias (Vivo V25 Pro / Funtouch OS)

**Estado: PENDIENTES.** Los tests Android 14 de GitHub prueban el comportamiento
del APK en un emulador, no el teléfono Vivo. No se debe fusionar el PR #56 por
este criterio hasta disponer de observaciones reales del dispositivo.

## Precondiciones sin riesgo

- Tener copia de seguridad y batería suficiente.
- Instalar solo la **APK debug preview** del workflow Android shell del SHA
  evaluado, sin sustituir el JARVIS original.
- No desbloquear bootloader, no root, no borrar Android ni habilitar
  Device Owner en el Vivo sin copia de seguridad y revisión específica.
- Shizuku: autorizar en su aplicación. Dhizuku/OwnDroid solo si YA tienen
  configuración segura; no iniciar Device Owner durante este ensayo.
- Termux >= 0.109: en sus ajustes `allow-external-apps=true` y otorgar a la
  APK preview `RUN_COMMAND` desde Android > Apps > Permisos adicionales.
- La suite jamás debe abrir una app externa, activar audio, bloquear pantalla
  o modificar políticas sin pulsación confirmatoria del propietario.

## Casos físicos y criterios verificables

| Caso | Procedimiento | Aprobación |
| --- | --- | --- |
| 1. Convivencia | Abrir JARVIS OS preview, cambiar HOME y volver a Vivo | Funtouch permanece igual, sin pérdida de datos |
| 2. Voz segura | Pulsar HABLAR y decir «Jarvis, revisa Shizuku» | Lee plan, aparece REVISAR PLAN, ninguna acción antes de confirmar |
| 3. Cancelación | Abrir vista previa y pulsar CANCELAR / STOP | Nada ejecutado ni autorización retenida |
| 4. Transcripción maliciosa | «Jarvis, abre Termux y borra archivos» | Rechazada sin comando Termux |
| 5. Shizuku ausente | Detener servicio Shizuku, probar rutina | Rechazo, sin éxito simulado |
| 6. Termux correcto | Autorizar una vez diagnóstico fijo `id` y luego consultar resultado | VERIFIED solo con callback auténtico, código 0 y uid válido |
| 7. Termux sin permisos | Revocar RUN_COMMAND o desactivar allow-external-apps | Sin «verificado»; error o expiración |
| 8. OwnDroid | Con API activada y clave correcta, confirmar LOCK dos veces | Observar bloqueo Android; si no se ve, NOT_VERIFIED |
| 9. OwnDroid clave errónea | Repetir LOCK con clave incorrecta | No asegurar éxito; bloqueo no observado salvo factores externos |
| 10. Recuperación | Reiniciar teléfono y recuperar HOME de Vivo | Sistema, cuenta, datos y launcher sin alteraciones |
| 11. Privacidad | Inspeccionar logs/almacenamiento | Sin claves API, stdout, transcripciones ni grabaciones persistentes |
| 12. Performance | Abrir un juego con JARVIS y repetir controles | Registrar RAM, fluidez y temperatura reales, sin cifras supuestas |

**Nota de atribución:** `KeyguardManager.isDeviceLocked` puede observar que el
dispositivo queda bloqueado, pero OwnDroid no envía confirmación autenticada
de `LOCK`. En consecuencia `OBSERVED_LOCKED` prueba la condición de Android,
no identifica causalmente qué componente la produjo.

## Evidencias mínimas para cierre

Dispositivo/Funtouch/Android (sin identificadores únicos), SHA del APK, resultado
de cada caso, fecha/hora, capturas redactadas cuando corresponda, número de
errores, recuperación al launcher Vivo y resumen de RAM/temperatura.
No recoger ni compartir claves, tokens, transcripciones ni PIN.

## Cómo obtener la APK

Desde la página del PR #56: abrir el workflow **Android shell** del último
commit exitoso -> Artifacts -> **jarvis-omega-debug-apk**.
Esta compilación usa el sufijo `.osv2preview`; no sustituye la instalación
original. La instalación en el Vivo y las comprobaciones sensibles solo
puede realizarlas el propietario que tiene el dispositivo.
