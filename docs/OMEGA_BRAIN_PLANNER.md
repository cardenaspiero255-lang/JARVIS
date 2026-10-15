# Omega Brain — planificador determinista (PR #7)

Componente: jarvis_contracts/task_plan.py (Python puro).

## Entrada

Un objeto estricto con \"steps\" (1..50). Cada paso contiene exactamente \"id\", \"action\" y \"dependsOn\" (lista de referencias a IDs). Campos de permisos añadidos en mensajes no confiables se rechazan. No acepta payloads ni texto libre ejecutable.

## Comportamiento

- Orden topológico reproducible y estable; dependencias inexistentes o ciclos se rechazan.
- Las acciones conocidas de lectura se clasifican READ_ONLY; mutaciones conocidas REQUIRES_APPROVAL; acciones desconocidas o peligrosas BLOCKED.
- `PlanPreview.direct_risks` conserva el riesgo individual; `PlanPreview.risks` comunica el riesgo **efectivo**, heredando BLOCKED o REQUIRES_APPROVAL de todas las dependencias directas e indirectas. Un paso READ_ONLY que dependa de un paso bloqueado queda también BLOCKED.
- `PlanPreview.risk_origins` identifica los IDs de los pasos que originaron el nivel de riesgo dominante, ordenados y sin entradas duplicadas. Los tres mapas son inmutables y los resultados siguen siendo **solo simulación**.
- La salida es una **previsualización**, can_execute siempre falso, sin función execute ni transporte, sin abrir herramientas.
- No decide permisos por sí sola: un futuro ejecutor deberá validar identidad, consentimiento vigente, capacidad, plazo, contexto y postcondiciones.

## Fuera de alcance

No descompone lenguaje natural, no llama modelos, no ejecuta comandos, no resuelve consecuencias reales, no programa tareas para el futuro ni gestiona concurrencia distribuida. La clasificación de riesgo es preliminar y no reemplaza políticas dinámicas.

Pruebas: python -m unittest discover -s tests -v. Relacionado issue #1 y seguimiento de 50 mejoras.
