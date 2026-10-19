# JARVIS SSS+ — 17 lagunas de cobertura y mejoras parciales

Fecha de ejecución: 2026-10-18. El estado es **desarrollo / no certificado**.

## Primera prioridad: 17 de 17 controles de seguridad asociados

Los IDs 3, 4, 6, 7, 9, 10, 11, 13, 14, 16, 19, 29, 36, 43, 44, 45 y 46 tenían
cero tests asociados en el inventario del PR #81. Este PR introduce exactamente
**17 tests de denegación / no atribución**; cada uno exige evidencia verificable
de que la funcionalidad aún pendiente no se presente como implementada o no se
active sin el permiso correspondiente.

**Importante:** son `NEGATIVE_GUARD_ONLY`. No demuestran la funcionalidad
de las 17 mejoras, ni las convierten en completas. La matriz conserva
`features_without_positive_functional_tests: 17`, separa las 33 mejoras con
algún test funcional definido de estas 17, y mantiene `NOT_CERTIFIED`.

Para cerrar cada ID funcionalmente, implementar el comportamiento permitido,
escribir pruebas positivas (y no solo denegaciones), ensayar casos adversariales
e integrar la función en el flujo real de la APK. El reporte debe cambiarse
a prueba funcional solo cuando exista esa evidencia.

## Segunda prioridad: endurecimiento de funcionalidades parciales

Se abordan en esta entrega los controles de:
- #18 órdenes encadenadas: la misma rutina no puede duplicarse con confirmación concurrente.
- #31 autorización según riesgo: preparar una rutina distinta durante otra activa
  devuelve un bloqueo y no sustituye la ejecución en curso.
- #35 parada de emergencia: la acción de parada sigue impidiendo pasos futuros.
- #37 validación pre/post: el `SafeAutomationReport` declara
  `externalOutcomeVerified=false` y `rollbackVerified=false` cuando solo
  existe confirmación del gateway local. Una acción `COMPLETED` no es prueba
  de que otra app realizó cambios ni de que hayan sido revertidos.

Estas son correcciones puntuales, **no certifican las 32 mejoras parciales**.

## Pendientes por subsistema

1. Inteligencia: funciones 3, 4, 6, 7, 9, 10; modelos offline con
   evaluación real, verificador con evidencia, objetivos persistentes, memoria
   de rutinas opt-in y resultados de investigación verificables.
2. Control Android: 11, 13, 14, 16, 19; comportamiento de gestos y notificaciones
   consentido, cambio visual privado, rutinas offline reales sin shell arbitrario.
3. Build: 29; benchmark CI de duración/variabilidad en máquina comparable,
   p50/p95 repetido y no un tiempo de un solo job.
4. Rollback: 36; primero definir qué acciones son reversibles; diseñar
   compensaciones E2E y comprobar rollback real; STOP no implica rollback.
5. Agentes/dispositivos: 43, 44, 45, 46; no sincronizar ni controlar PC
   sin permisos, backend autenticado, revocación y cobertura E2E.
6. Voz y seguridad de las otras 33 mejoras: FAR/FRR/replay con audio
   real consentido, continuidad física Vivo/Funtouch y ensayos E2E.

## Criterios antes de fusionar

- `Contract tests`, `JARVIS QA 50 coverage` y `Android shell` verdes
  sobre el HEAD del PR.
- Tests de regresión `SafeAutomationEngineTest` incluyen un escenario con
  barrera de sincronización entre dos hilos; pasan junto al resto.
- Mantener sin cambios el estado de autenticación biométrica y
  las restricciones de inicio de micrófono de Android.
- `JARVIS QA 50 coverage` informa 50 test files *asociados*, **17 negativos
  solamente**, 33 con pruebas funcionales definidas, y cero certificados.
- Ninguna afirmación de integración externa Shizuku/Termux/PC,
  rollback, calidad de voz o sincronización sin evidencia física específica.

Este documento aplica evidencia honesta y el patrón de TDD:
falla reproducible -> corrección localizada -> tests del SHA actualizado.
