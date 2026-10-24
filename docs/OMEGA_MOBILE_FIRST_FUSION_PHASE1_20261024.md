# JARVIS OMEGA · Mobile-first · Fase 1 (vista previa, no control remoto)

## Alcance comprobable

- La base sigue siendo JARVIS Android. No se sobrescriben los PR #98/#99 ni se mezclan binarios, SDKs o modelos de Windows en el APK.
- Se agrega `OmegaPortableTaskPlanner`: portabilidad de un contrato de planificación acotado (máximo 50 pasos) con orden topológico estable, rechazo de dependencias inexistentes/duplicadas/cíclicas y propagación de riesgo (`READ_ONLY < REQUIRES_APPROVAL < BLOCKED`). Las acciones desconocidas permanecen `BLOCKED`.
- En el chat local, una orden **escrita y exacta**, por ejemplo `planifica desarrollo jarvis windows`, produce un resumen **simulado** del flujo inspección → CI → revisión → autorización. También existen vistas previas para `jarvis android`, `gamehub ultra` y `ultra sentinel`.
- La vista previa **no** consulta GitHub, no se conecta a PC, no fusiona PR, no ejecuta comandos de shell y no proporciona una autorización reutilizable. `canExecute` es siempre `false`.
- No se añaden permisos de Android, sockets, claves o exportaciones; la voz y los modelos offline no cambian en esta fase.

## Límites

Esto es solo el **primer componente** de una convergencia Android/Windows, no la fusión completa. Antes de transmitir tareas entre dispositivos hacen falta identidad criptográfica, emparejamiento explícito, TLS/Tailscale, firmas de mensajes, antirreplay, revocación, autorización por acción y pruebas E2E auténticas. No se intentará reutilizar el puerto WebSocket de Windows sin esas protecciones.

## Pruebas y compuertas

- Kotlin/JUnit: `OmegaPortableTaskPlannerTest` y `LocalDialogueTest` cubren precedencia, ciclos, bloqueo, consentimiento y entrada adversarial.
- Reproducibilidad local: `kotlinc` de la unidad de planificación (sin Android ni red), con verificaciones funcionales de entrada/salida.
- Antes de fusionar: `gradle -p apps/android :app:testDebugUnitTest :app:assembleDebug` y el resto de contratos/QA/Android14 del SHA exacto; conservar las reglas de seguridad existentes.
- La prueba real de voz, pantalla apagada, micrófono, GGUF y dispositivos físicos sigue **NO CERTIFICADA** en esta fase.

## Ruta posterior (Android prioritario)

1. Certificar el circuito de voz y GGUF de las ramas Android pendientes, sin absorber cambios sin pruebas.
2. Usar el contrato de vista previa para una pantalla de tareas con aprobación visible; sin conexión PC por defecto.
3. Diseñar un protocolo de emparejamiento interoperable con el backend de Windows y prueba de denegación/revocación.
4. Validar una tarea de consulta de estado **read-only** entre Android y Windows, seguida de una acción simulada con aprobación. Solo después ampliar capacidades.

## Procedencia

Inspirado en el flujo de diagnóstico/revisión de `Jarvis-Windows-` (análisis → pruebas → revisión → aplicación autorizada) y en las restricciones del compilador Python `jarvis_contracts/task_plan.py` del repositorio Android. Es adaptación Kotlin propia, no una importación de código binario de Windows.
