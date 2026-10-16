# JARVIS OS V2 — Companion Bridge V1 (experimental)

Base: PR #55 (`feat/jarvis-os-v2-compact-hub`). Depende de #54 y #55.
No fusionar sobre `main` hasta que ambas dependencias estén integradas y los checks
del HEAD exacto sean verdes.

## Alcance real de esta versión

- Una entrada dentro de **SISTEMA** abre `CompanionCenterActivity` sin alterar
  la pantalla de inicio Vivo.
- Visibilidad acotada a cuatro paquetes conocidos; no se solicita
  `QUERY_ALL_PACKAGES`.
- **Shizuku:** API oficial 13.1.5, inspección del Binder y del permiso real.
  La petición de permiso se produce únicamente con una pulsación explícita.
  Un resultado READY significa *servicio + permiso*; **no** que un comando
  ADB haya sido probado ni que la ejecución privilegiada esté habilitada.
- **Dhizuku, OwnDroid y Termux:** se detecta instalación y se puede abrir
  su propia pantalla de configuración, pero no se afirma que los permisos
  administrativos, la clave de OwnDroid o `RUN_COMMAND` estén configurados.
- No hay automatización oculta, ejecución de scripts, Device Owner, solicitudes
  ADB de alto privilegio, cambios de aplicaciones ni borrado de datos.
- Solo hay una nueva entrada de navegación en el módulo SISTEMA. Los cuatro
  controles principales del launcher permanecen iguales.

## Integraciones a implementar en etapas auditables

1. **Shizuku adapter:** binder y permiso ya comprobados; diseñar API
   orientada a operaciones, lista blanca y pruebas Android antes de
   habilitar cambios de ajustes o paquetes.
2. **Motor de automatización interno:** acciones idempotentes con rollback
   cuando corresponda, vista previa, aprobación por tarea, registro local
   y botón STOP. No depende de Tasker.
3. **Termux adapter:** comprobar `RUN_COMMAND` concedido por Android, la
   opción `allow-external-apps` y rutas de scripts previamente aprobadas.
   Prohibir shell arbitraria generada por modelos de lenguaje.
4. **OwnDroid adapter:** llave de API en almacenamiento protegido y
   confirmación de cada comando administrativo; rechazar inicialmente WIPE,
   borrado de almacenamiento, cambios de cuentas y bloqueo de acceso.
5. **Dhizuku adapter:** verificar Device Owner real y API SDK; no activar
   Device Owner automáticamente. Android solo admite un propietario del
   dispositivo y habilitarlo puede requerir retirar cuentas.
6. Integrar comandos de voz con planificador y comprobación del estado
   **solo después** de validar las operaciones permitidas.

La integración de Dhizuku y OwnDroid puede requerir sus apps instaladas.
No es técnicamente equivalente copiar funciones de esas apps dentro de
JARVIS. Su implementación interna necesitaría revisar las licencias y
el modelo de privilegios de Android.

## Pruebas

- Contrato Kotlin: `CompanionConnectionPolicyTest`.
- Contrato Python: `tests/test_jarvis_os_companion_bridge_contract.py`.
- CI: `gradle -p apps/android :app:testDebugUnitTest :app:assembleDebug`.
- Pendiente: prueba física en Vivo V25 Pro, salida/restauración de Funtouch,
  Shizuku sin servicio, Shizuku sin permiso, Shizuku concedido y revocado,
  reinicio del teléfono, ausencia de apps, paquetes suspendidos y uso offline.

**No certificada para producción hasta CI y pruebas físicas en el Vivo.**
