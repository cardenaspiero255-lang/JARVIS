# Omega Diagnostics — comprobación manual sin telemetría

## Función implementada
El botón **DIAGNÓSTICO LOCAL DE JARVIS** consulta una vez las APIs del teléfono y presenta un informe en pantalla, sin guardarlo ni transmitirlo.

Se indican:
- Versión API de Android.
- Si Android encuentra un launcher válido para el paquete esperado de GameHub Ultra (esto **no** acredita IPC).
- Si Omega Hands está conectado y si la sesión está detenida.
- Si el motor de síntesis TTS tiene una voz **local** preparada (esto no acredita audio audible).
- Si Android informa que existe un proveedor de reconocimiento de voz. Ese proveedor puede ser remoto, aunque JARVIS no abra automáticamente el micrófono.
- Si la memoria cifrada privada está accesible actualmente; **no** se lee su contenido.

## Seguridad y límites
No hay nuevos permisos, acceso a secretos, comandos Android, lectura de pantalla, actividades en segundo plano, monitorización recurrente ni acceso a redes. La consulta es de solo lectura por pulsación.

El informe incluye explícitamente **SIN CERTIFICACIÓN**: disponibilidad de APIs no equivale a pruebas físicas o autonomía. El sistema operativo puede cambiar el estado tras pulsar; no es un detector en tiempo real.

## Verificación
JUnit comprueba detección positiva, negativa y resultados de privacidad; Python comprueba que la consulta no invoque acciones y esté asociada al botón visible. CI compila el APK pero todavía se requiere validación de dispositivos para voz/accesibilidad.
