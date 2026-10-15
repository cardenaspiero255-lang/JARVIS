# Omega Voice — confirmación explícita de solicitudes sensibles

## Qué cambió

El resultado del reconocedor de voz es **entrada no confiable**, aunque el
usuario haya pulsado el botón para hablar. Una orden como «JARVIS abre
configuración» ya no abre directamente los ajustes de accesibilidad.

1. La transcripción solo ofrece una solicitud de apertura.
2. La pantalla muestra un mensaje para pulsar el botón
   **CONFIRMAR AJUSTES SOLICITADOS POR VOZ**.
3. Solo la pulsación visible puede confirmar una solicitud vigente.
4. La autorización es de un solo uso, dura menos de 30 segundos y se
   cancela al salir de la pantalla, comenzar otra escucha o reiniciar.
5. El botón **ABRIR AJUSTES DE ACCESIBILIDAD** sigue siendo una orden
   directa del usuario y no necesita una solicitud por voz.

Además, JARVIS detiene su salida TTS antes de abrir el reconocedor externo
para evitar realimentación de audio y desautorizar la reproducción previa.

## Límites y pruebas

- La confirmación no habilita ni concede permisos de AccessibilityService.
  Android mantiene el control de esos permisos.
- Tampoco equivale a autorizar clics, texto o gestos del teléfono.
- El consentimiento se conserva solo en memoria durante una sesión; no se
  guarda la transcripción ni el texto completo del usuario.
- JVM/JUnit valida caducidad, monotonicidad, revocación y consumo único.
- Las pruebas de seguridad de integración comprueban que la rama del
  comando hablado no invoca el lanzamiento de ajustes.

Se requiere una prueba manual en dispositivo para confirmar que cancelar
el reconocimiento, girar el teléfono, cambiar de aplicación y abrir la
pantalla de ajustes no deja solicitudes latentes.
