# JARVIS OS — Companion Bridge V2 (PR #56, draft)

## Alcance del desarrollo

Base: PR #55 sobre #54. Sin ROM personalizada, root ni sustitución de Funtouch.
La APK V2 preview usa un identificador diferente del JARVIS instalado.

### Motor de automatizaciones propio

`SafeAutomationEngine` tiene un catálogo cerrado `SafeRoutine`. No acepta
scripts, paquetes o comandos generados por una LLM. Cada solicitud:

1. Se convierte a un plan permitido y muestra una vista previa de sus acciones.
2. Necesita pulsación de **AUTORIZAR UNA VEZ**. La autorización no persiste.
3. Ejecuta pasos en orden, sin reintentar ni continuar si falla un requisito.
4. Detiene los pasos pendientes al pulsar **PARADA DE EMERGENCIA**.
5. Anula cualquier plan si la Activity se destruye. El resultado de un
   `Intent` o broadcast **no prueba** que la operación remota finalizó.

La coincidencia de voz está limitada a cinco frases explícitas en
`SafeAutomationVoiceRouter`, que SOLO devuelve una propuesta. Todavía **no**
está cableada al reconocedor de voz ni ejecuta acciones desde una transcripción.
No hay automatizaciones en segundo plano ni tareas programadas.

### Integraciones reales (sin elevar privilegios)

| Servicio | Comprobación | Acción solicitada |
| --- | --- | --- |
| Shizuku | Binder vivo + permiso SDK | Abrir su panel tras verificar permiso |
| Dhizuku | SDK 2.5.4, `init` + permiso | Solicitar consentimiento de SDK o abrir app |
| Termux | `RUN_COMMAND` concedido por Android | Ejecutar ÚNICAMENTE `id` local, sin argumentos ni stdin |
| OwnDroid | APK presente | Broadcast LOCK con clave API ingresada una sola vez + doble confirmación |
| Android | Ajustes accesibles | Abrir Ajustes sin cambiar la preferencia HOME |

Para Termux el usuario también debe activar `allow-external-apps=true` en
`~/.termux/termux.properties`. El primer intento puede fallar por reglas de
servicios en segundo plano. Solo se afirma que el Intent fue enviado, nunca que
el comando terminó; no se recoge stdout/stderr ni conversaciones.

OwnDroid requiere activar manualmente su API en ajustes y proporcionar su
propia clave. Esta versión no guarda la clave, no permite HIDE, SUSPEND, WIPE,
REBOOT ni operaciones de políticas de seguridad. La única operación administrativa
expuesta es bloqueo de pantalla, autorizada para cada intento. No es lo mismo
enviar el broadcast que comprobar el bloqueo.

Dhizuku NO se convierte automáticamente en Device Owner. Configurarlo puede
requerir retirar cuentas existentes; no debe hacerse sobre el Vivo principal
sin copia de seguridad y plan de reversión. Android no admite dos Device Owners
simultáneos, y OwnDroid puede funcionar como cliente Dhizuku solo si su modo
admite esta topología.

## Pruebas y criterios de salida

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
  en `apps/android` (CI utiliza `gradle -p apps/android`).
- `python -m unittest discover -s tests -v`.
- Instrumentación Android 14 incluyendo `CompanionCenterInstrumentedTest`.
- En el Vivo V25 Pro: probar sin apps, con apps instaladas y sin permiso,
  permisos revocados, reinicio, STOP, launcher original, comando Termux autorizado,
  petición Dhizuku y bloqueo OwnDroid con consentimiento.

No fusionar el PR sin checks verdes en el SHA actual, revisión de seguridad y
sin resolver dependencias #54 y #55. No afirmar «100 % operativo», «24/7»,
«pruebas físicas aprobadas» ni «sin errores» sin evidencia específica.

## Referencias técnicas

- Shizuku: https://github.com/RikkaApps/Shizuku-API
- Dhizuku API: https://github.com/iamr0s/Dhizuku-API
- OwnDroid: https://github.com/BinTianqi/OwnDroid
- Termux RUN_COMMAND: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
