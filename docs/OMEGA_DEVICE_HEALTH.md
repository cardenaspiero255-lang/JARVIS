# Omega Performance — telemetría instantánea del dispositivo

**Objetivo:** mostrar batería, estado térmico y memoria disponible del Android que ejecuta JARVIS sin permisos sensibles.

## Funcionamiento
- El usuario pulsa **CONSULTAR BATERÍA, ESTADO TÉRMICO Y RAM** en la interfaz principal.
- JARVIS consulta una sola vez el intent fijo `ACTION_BATTERY_CHANGED` (porcentaje/cargando), el estado térmico de `PowerManager` cuando Android 10+ lo permite y `ActivityManager.MemoryInfo` para RAM disponible.
- Los estados ausentes o físicamente inconsistentes se muestran como **no disponible**. Los estados térmicos severos y la alerta de memoria baja se muestran como advertencias.
- No se programa muestreo continuo, ni se guardan o envían registros. El estado térmico es **una clasificación Android**, no grados Celsius; no se garantiza una temperatura del procesador.

## Restricciones y verificación
- No añade permisos de Internet, Battery Stats, uso de otras aplicaciones, teléfono o sensores peligrosos.
- Los diagnósticos no dan autoridad para modificar frecuencia de CPU/GPU, vaciar memoria de otras aplicaciones ni alterar el sistema.
- Kotlin/JUnit verifica casos normales, extremos, errores y alertas. Python garantiza los límites del manifiesto y el origen manual.
- GitHub Actions compila la app y APK instrumentada, pero la precisión de las lecturas y comportamiento ante calor real requieren validación física.

## Consulta hablada y lectura TTS local

El botón **HABLAR CON JARVIS** puede reconocer frases exactas como «cuánta batería tengo», «Jarvis batería», «estado de batería», «Jarvis estado térmico» y «cuánta RAM libre tengo». En todos esos casos JARVIS lee una instantánea local de las APIs públicas Android y la **muestra en pantalla**, sin alterar la batería, CPU, GPU, memoria ni otros procesos.

**LEER SALUD DEL TELÉFONO EN VOZ ALTA** obtiene una instantánea nueva y, únicamente tras activar previamente el motor TTS offline, intenta leer sus valores por pulsación. El texto de lectura se genera desde campos numéricos validados y etiquetas fijas, nunca directamente desde la transcripción, OCR o texto externo.

La lectura no se guarda ni se transmite. Un proveedor externo de reconocimiento de voz de Android podría usar red; JARVIS no controla el funcionamiento interno de ese proveedor y no escucha continuamente. El estado térmico continúa siendo una clasificación del sistema, no una temperatura CPU en grados. Que Android acepte la síntesis no garantiza que se escuche: sigue pendiente verificación en teléfono real.
