# JARVIS ↔ Ultra Sentinel: observador serverless de estado

## Qué existe
- GitHub Actions en **JARVIS/main**, cada 30 minutos aproximadamente
  (minutos 27 y 57), inicio manual o al actualizar la implementación.
- Consulta HTTPS **GET**, con `GITHUB_TOKEN` efímero de Actions, datos públicos
  de `cardenaspiero255-lang/Ultra-Sentinel` y su monitor real
  `.github/workflows/serverless-github-monitor.yml`.
- Comprueba **ID numérico del repositorio 1418338512** y **del propietario
  322222186**, rama main, workflow permitido, SHA, evento, fecha, estado,
  resultado, caducidad máxima de dos horas y forma de la respuesta.
- Publica un JSON mínimo sin token, sin cuerpo de PR ni secretos, con estados
  `RECENT_READONLY_SUCCESS`, `RECENT_FAILURE`, `SKIPPED`,
  `STALE`, `NO_VERIFIED_RUN` o `UNVERIFIED`.
- Si no puede verificar una ejecución reciente, el job falla para
  mostrar la advertencia en GitHub Actions; nunca inventa estado verde.
- No ejecuta código de PR, no fusiona, no repara y no solicita permisos
  de escritura. Solo usa infraestructura GitHub, sin VPS ni tarjeta.

## Límites de la garantía
Una ejecución reciente verde significa únicamente que **ese workflow** terminó
con éxito. No prueba que Ultra Sentinel sea un servicio API vivo, no garantiza
24/7, ni confirma paridad de auditoría, seguridad independiente o
integración con el motor interno de GameHub.

Los cron de GitHub pueden retrasarse, omitirse o desactivarse por inactividad.
La lista de runs es una ventana pública con un límite de 30 registros, no
inventario completo. Un token válido de JARVIS puede carecer de acceso a
Actions de otros repositorios y producir estado `UNVERIFIED`; no eludir
la comprobación sustituyéndolo por tokens de escritura o credenciales
compartidas.

## Para verificar funcionamiento real
Tras fusionar el PR, revisar
`JARVIS → Actions → JARVIS — Ultra Sentinel read-only health`.
Confirmar resultado de un run en `main`, contenido del artifact público
`jarvis-sentinel-readonly-status`, caducidad, alertas y salida al provocar
un run remoto fallido. Es un proceso programado en GitHub, **no una tarea
asíncrona de ChatGPT**.

La implementación de un proveedor autentificado con capacidad de
recomendaciones/acciones necesitaría instalar un servicio explícito en Ultra
Sentinel, identidades y pruebas E2E aparte; está pendiente.
