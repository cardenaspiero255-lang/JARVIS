# Omega Recovery — inspección conservadora tras reinicios

La capa de recuperación utiliza **DurableStore** existente. No añade una
segunda base de datos ni duplica el journal de idempotencia.

## Estados de reinicio

| Estado | Comportamiento |
|---|---|
| Sin registro | UNSEEN: debe validarse el permiso y la intención antes de comenzar |
| Iniciado, sin resultado confirmado | RECONCILE: requiere conciliación humana/proveedor |
| Resultado completado, misma huella | COMPLETED: solo devuelve resultado persistido |
| Plazo vencido | EXPIRED: no devuelve un permiso ni reenvía nada |
| Misma clave, huella diferente | IdempotencyConflict: prohibido reutilizar |
| Estado de almacenamiento inesperado | InvalidRecoveryInput: bloqueado |

La salida **nunca autoriza ejecución**: `can_execute` permanece falsa,
incluso cuando no exista una orden previa. Una inspección no modifica
el journal ni decide si un proveedor realizó el efecto externo.

## Pruebas

Se comprueban reinicios simulados con SQLite, operaciones inciertas, replay
exacto de resultado, almacenamiento de `null`, vencimiento en el límite,
colisiones de idempotencia y entradas mal formadas.

La fase siguiente debe obtener pruebas independientes del proveedor,
credenciales autorizadas y una autorización para conciliar; no se debe
simular éxito ni reintentar operaciones mutadoras por suposiciones.
