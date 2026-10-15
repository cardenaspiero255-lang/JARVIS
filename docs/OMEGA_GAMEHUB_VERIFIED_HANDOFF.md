# Omega GameHub — inicio asistido con verificación doble

La función del PR #37 es opcional y **no sustituye** el botón original ABRIR GAMEHUB ULTRA. El usuario sigue teniendo control total de la apertura.

1. Pulsa **PREPARAR APERTURA SEGURA DE GAMEHUB**. JARVIS evalúa en ese momento batería, estado térmico y RAM con GameReadinessAdvisor. Si el nivel resulta READY o CAUTION, se habilita una autorización local de 30 segundos vinculada al perfil actual. Si es POSTPONE o UNKNOWN, no se prepara el inicio asistido.
2. Pulsa **CONFIRMAR APERTURA SEGURA DE GAMEHUB** antes de 30 segundos. La autorización se consume una sola vez; JARVIS vuelve a consultar las señales Android. Si ahora detecta POSTPONE/UNKNOWN, cancela la apertura. Si sigue READY/CAUTION, intenta abrir GameHub Ultra mediante el GameHubLauncher explícito y restringido al paquete esperado.

La evaluación no puede certificar FPS ni anticipar todas las condiciones reales. Una advertencia CAUTION no impide confirmar con un segundo toque, dado que el usuario la ha recibido. El botón normal de apertura permanece manual y separado.

**Seguridad:** cambiar de perfil, salir de la app, bloquear la sesión, caducar o consumir la autorización cancela el preflight. No hay comandos a GameHub, IPC autenticado, token remoto, cambios de CPU/GPU, permisos nuevos, telemetría ni acciones programadas. Cualquier integración autenticada futura requiere un contrato en ambos repositorios y pruebas E2E reales.

**Pruebas:** JUnit para niveles, repetición, caducidad, perfil, cancelación y rollover; Python comprueba los dos botones, orden de la nueva lectura antes del lanzamiento, ausencia de app launch durante preparación y revocación por el ciclo de vida. Debe pasar Android shell + Contract tests sobre SHA final, pero aún falta comprobar físicamente el lanzamiento de GameHub en un Android real.