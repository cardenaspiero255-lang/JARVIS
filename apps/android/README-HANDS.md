# Omega Hands — primera capacidad Android (fase 2)

La versión inicial es **voluntaria, limitada y reversible**. No habilita
automatización general ni lectura de pantalla.

## Uso

1. Abre JARVIS Android y pulsa **Abrir ajustes de accesibilidad**.
2. Busca **JARVIS OMEGA Hands (control limitado)** y habilítalo mediante Android.
3. Regresa a JARVIS, pulsa **Consultar estado Hands** y después
   **Autorizar Hands — Inicio y Atrás**.
4. Pulsa **Ir a Inicio** o **Volver Atrás** para solicitar una única acción global de Android por pulsación.
5. **Parada de emergencia Hands** detiene inmediatamente nuevas acciones
   desde JARVIS. El estado se escribe en preferencias y, de forma adicional,
   cualquier reinicio del proceso o del servicio exige volver a autorizar
   manualmente desde la app.

## Seguridad

- El servicio está protegido por BIND_ACCESSIBILITY_SERVICE; Android gestiona
  la activación o revocación del permiso del sistema.
- El estado de ejecución está **detenido por defecto** aunque el servicio
  esté instalado y habilitado. La autorización activa es exclusiva de la sesión
  actual; se revoca cuando el proceso o servicio se reinicia.
- Solo funcionan las acciones globales **HOME** y **BACK** al pulsar cada botón visible. No hay navegación automática.
- No se leen árboles de accesibilidad, texto, contraseñas ni capturas.
- No se permiten clics automáticos, escrituras, envío de mensajes, órdenes
  de voz que ejecuten acciones, gestos, compras ni operaciones de GitHub.
- No almacena ni transmite eventos de accesibilidad.
- Revocar el permiso en Android desactiva la acción incluso si se pulsó autorizar.

## Verificación y limitaciones

Gradle/JUnit prueba la política de rechazo y la CI valida el manifiesto y
las capacidades XML. Una compilación APK no sustituye las pruebas manuales
en un dispositivo Android real. El éxito de performGlobalAction() confirma
aceptación por Android, **no la observación independiente** de la pantalla
de inicio. Esta fase no incluye aún inspección de UI ni navegación entre apps.

Se debe verificar manualmente la activación/desactivación del servicio,
la persistencia del stop, el cambio de apps y las restricciones del
dispositivo antes de declarar la funcionalidad apta para producción.
