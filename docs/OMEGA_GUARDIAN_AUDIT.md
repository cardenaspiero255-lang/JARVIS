# Omega Guardian — registro de operaciones con cadena HMAC

## Objetivo

Permitir conservar **metadatos mínimos** de intentos de operación y detectar
alteraciones accidentales o de registros individuales sin exponer acciones,
resultados, credenciales o contenido de conversación en bruto.

Componente Python: `jarvis_contracts/integrity_audit.py`.

Cada entrada conserva únicamente: secuencia, hash SHA-256 del principal,
identificador de acción de formato restringido, resultado enum
(STARTED, SUCCEEDED, FAILED, DENIED, UNCERTAIN), hash del recurso,
marca temporal y huellas HMAC del registro/registro anterior.

## Modelo de seguridad

- Clave de integridad HMAC SHA-256 **inyectada desde un almacén externo**.
  Nunca se escribe en SQLite, logs ni código de producción.
- Transacciones SQLite IMMEDIATE para ordenar escrituras concurrentes.
- Lectura/append verifican continuidad y HMAC de todos los registros.
- Historia adulterada: cualquier escritura posterior es rechazada.
- Sin correo, número de teléfono, mensaje, transcripción, token, rutas
  confidenciales ni stacktrace persistidos por la interfaz.
- No convierte las decisiones del llamador en evidencia de acción real:
  su identidad y autoridad deben validarse fuera del módulo.

## Límites importantes

No es una fuente de verdad externa: quien puede borrar o reemplazar todo
el archivo SQLite o robar la clave HMAC puede eliminar evidencia sin que
quede una raíz de confianza independiente. Para producción hace falta
anclar periódicamente el último digest fuera del dispositivo, mantener
copias inmutables, claves protegidas por sistema y rotación de claves.

La cadena detecta *integridad local*, **no es una auditoría certificada ni
un registro inviolable**. No existe aún conexión con los flujos Android,
GitHub o Sentinel; debe integrarse a los puntos de ejecución con identidad
real y consentimiento de usuario.

Pruebas: SQLite persistente, reinicios, tampering, huecos de secuencia,
concurrencia, confidencialidad de metadatos y claves inválidas.
