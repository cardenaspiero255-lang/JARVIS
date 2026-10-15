# JARVIS OMEGA — prototipo Android y núcleo de referencia Python

**Estado: objetivo SSS+ en desarrollo, SIN CERTIFICACIÓN de producto.**
Existe una aplicación Android compilable y un núcleo de referencia Python.
La compilación de un APK y los tests de CI **no** demuestran que funcione de extremo a extremo en un teléfono real.

## Funciones implementadas en `main`

- **Omega Chat / Memory:** chat local determinista en español, cálculo offline y continuaciones numéricas; memoria privada AES-GCM con activación explícita y borrado.
- **Omega Voice:** voz instalada en Android para respuestas predefinidas y del chat local, con activación manual, parada y reconocimiento iniciado por pulsación. El servicio de reconocimiento del sistema puede depender de un tercero. Después de iniciar el reconocimiento con el botón, la orden exacta «JARVIS, detente» bloquea y revoca las funciones locales de la sesión igual que el botón de privacidad; no habilita escucha continua ni borra la memoria cifrada en disco.
- **Omega Hands:** servicio de accesibilidad opcional para la acción HOME, con botón de autorización y parada de emergencia; no navegación universal.
- **Omega Vision:** OCR local de imágenes elegidas por el usuario y sugerencias heurísticas de botones, sin pulsarlos. **PR #16 de MediaProjection sigue en borrador**, no está integrado a `main`.
- **GameHub Ultra:** botón para abrir el paquete exacto y solicitud por voz que necesita una confirmación táctil independiente de un solo uso. No hay conexión autenticada al motor interno.
- **Omega Performance:** botón de lectura instantánea del porcentaje de batería, estado térmico del sistema y RAM disponible; no almacena ni transmite telemetría.
- **Omega Guardian / Recovery / Brain:** políticas deterministas, diario de auditoría, recuperación de estado y previsualización de planes; la orquestación autónoma real sigue pendiente.
- **Ultra Sentinel:** monitor serverless de GitHub Actions, autenticado y de solo lectura. No es un servicio externo permanente ni un certificador independiente.
- **Núcleo Python:** sobres de contratos versionados, allowlists de capacidades, consentimiento limitado, idempotencia SQLite y adaptador GitHub de solo lectura.

Las 50 mejoras SSS+ son el objetivo de [seguimiento](docs/SSS_PLUS_50_TRACKER.md). Una implementación parcial no se marca como completa o certificada.

## Pruebas reproducibles

- Python y contratos: `python -m unittest discover -s tests -v`.
- Android en CI: `gradle -p apps/android --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (JDK 17, SDK Android 36, Gradle 8.13).
- Los workflows publican APK de depuración y APK instrumentado en sus artefactos cuando pasan.
- **Siguen pendientes pruebas físicas** de accesibilidad, voz, OCR, MediaProjection con consentimiento, cifrado en AndroidKeyStore y navegación/integración real en dispositivos compatibles.

## Bloqueos antes de producción

Integración autenticada y desplegada con GameHub Ultra y Ultra Sentinel, identidad y permisos de proveedores, observabilidad de fallos, pruebas instrumentadas en dispositivo, privacidad, pruebas adversariales y políticas de distribución. JARVIS **no está certificado SSS+**, no puede automatizar fusiones a partir de texto de un modelo ni tratar una respuesta remota como autorización.

Consulta [arquitectura](docs/architecture/SSS_PLUS_ARCHITECTURE.md), [estado de implementación](docs/IMPLEMENTATION_STATUS.md) y [plan maestro](https://github.com/cardenaspiero255-lang/JARVIS/issues/1).
