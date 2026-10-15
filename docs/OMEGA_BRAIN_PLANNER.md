# Omega Brain — planificador determinista (PR #7)

Componente: jarvis_contracts/task_plan.py (Python puro).

## Entrada

Un objeto estricto con \"steps\" (1..50). Cada paso contiene exactamente \"id\", \"action\" y \"dependsOn\" (lista de referencias a IDs). Campos de permisos añadidos en mensajes no confiables se rechazan. No acepta payloads ni texto libre ejecutable.

## Comportamiento

- Orden topológico reproducible y estable; dependencias inexistentes o ciclos se rechazan.
- Las acciones conocidas de lectura se clasifican READ_ONLY; mutaciones conocidas REQUIRES_APPROVAL; acciones desconocidas o peligrosas BLOCKED.
- La salida es una **previsualización**, can_execute siempre falso, sin función execute ni transporte, sin abrir herramientas.
- No decide permisos por sí sola: un futuro ejecutor deberá validar identidad, consentimiento vigente, capacidad, plazo, contexto y postcondiciones.

## Fuera de alcance

No descompone lenguaje natural, no llama modelos, no ejecuta comandos, no resuelve consecuencias reales, no programa tareas para el futuro ni gestiona concurrencia distribuida. La clasificación de riesgo es preliminar y no reemplaza políticas dinámicas.

Pruebas: python -m unittest discover -s tests -v. Relacionado issue #1 y seguimiento de 50 mejoras.
