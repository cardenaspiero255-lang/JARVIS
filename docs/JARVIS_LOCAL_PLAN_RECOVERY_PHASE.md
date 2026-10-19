# JARVIS — planificación local de órdenes, consentimiento y recuperación

## Alcance implementado

Un único flujo de intención determinista `OfflineRoutinePlanner` se reutiliza en
el diálogo escrito y en el resultado de reconocimiento de voz iniciado por el
usuario. Reutiliza `SafeAutomationVoiceRouter` y `SafeRoutine`, sin instalar
otros asistentes, sin API de pago, sin Internet y sin añadir permisos Android.

Consultas reconocidas (solo ejemplos de texto):
- `Jarvis, revisa Shizuku y abre ajustes`.
- `Jarvis, revisa Dhizuku y abre ajustes`.
- `Jarvis abre Termux`, `Jarvis abre Ajustes`, etc., preservan su comportamiento
  original.
- `Jarvis comprueba Termux`: solicita mediante UI visible el único comando
  permitido `/data/data/com.termux/files/usr/bin/id`, sin argumentos.
  Un envío aceptado NO prueba que Termux lo haya ejecutado; el resultado
  se determina únicamente por el PendingIntent de un uso y código de salida
  validado con `CONSULTAR RESULTADO`.

Las nuevas rutinas son **previews acotados**:
1. comprobar que la API local correspondiente tiene permiso activo;
2. abrir Ajustes de Android SOLO si el paso anterior fue aceptado.

No se envían órdenes root, shell ni políticas administrativas. El usuario
debe revisar el plan, abrir el panel `CompanionCenterActivity` y confirmar
en pantalla cada ejecución. La voz y el texto **jamás autorizan**. Las
frases hipotéticas, negaciones, instrucciones múltiples desconocidas,
operadores y caracteres invisibles se rechazan por defecto.

## Motor de autorización

`SafeAutomationEngine` almacena un consentimiento en memoria con reloj
monotónico durante **menos de 30 s**. Tras expiración, reloj invertido,
cancelación, cierre de Activity o intento de sustitución de rutina, hace
`DENIED`. Una autorización no se reutiliza para una segunda ejecución.

El motor informa `failedStep`, `completedSteps` y el motivo para la
recuperación. `SafeRecoveryAdvice` distingue:
- volver a autorizar;
- reintento **manual** tras revisar la causa, con consentimiento nuevo;
- conciliación manual si ya se aceptaron pasos anteriores;
- verificación externa si el gateway solo despachó un Intent.

Los flags `externalOutcomeVerified` y `rollbackVerified` permanecen
`false`: abrir un panel no demuestra que la operación remota haya finalizado.
**No se implementa rollback de acciones externas ni reintentos automáticos**.
Una orden STOP impide pasos futuros, pero no deshace otros ya iniciados.
El Termux `id` con callback verificado y OwnDroid con comprobación
de estado permanecen separados del ejecutor genérico.

## Evidencia y límites

- `OfflineRoutinePlannerTest`: rutas conocidas, rechazo de negación,
  hipótesis, inyección y caracteres invisibles; sin autoridad.
- `SafeAutomationEngineTest`: expiración exacta de 30 s, reloj regresivo,
  rechazo de preflight y segunda autorización, reconciliación tras pasos
  parciales.
- `LocalDialogueTest`: explicación escrita del plan y restauración de
  historial sin ejecución.
- `test_local_plan_privacy_contract.py`: botones visibles, ausencia de
  permisos de red o arranque oculto, TTL y estado de efectos externos.
- `Contract tests`, `Android shell` y `Android 14 instrumentation`
  deben pasar en el mismo HEAD antes de considerar la rama integrable.

La mejora **no constituye una IA generativa local**: todavía se necesita
un runtime de LLM on-device, modelo con distribución legal revisada,
opción de descarga local, límites de RAM/energía, evaluación de calidad,
capacidad de degradación offline y pruebas en Vivo V25 Pro.
No declarar listo un modelo de lenguaje sin inferencia real y verificada.

Siguientes etapas en orden:
1. runtime de LLM offline opcional (consentimiento, hash, privacidad,
   presupuesto de memoria, sin activación de herramientas por texto del LLM);
2. capacidades adicionales del teléfono con permisos comprobados,
   precondiciones, pruebas en emulador y observación de resultados reales;
3. journal reversible para acciones **realmente compensables**, autorización
   para rollback y pruebas E2E (los intentos de apertura no lo son);
4. integraciones GameHub/Sentinel autenticadas, sin capacidad remota implícita;
5. finalizar y acreditar las funciones parciales con pruebas físicas.
