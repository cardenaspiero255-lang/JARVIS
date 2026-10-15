# Omega Vision — observación de pantalla con consentimiento Android

## Alcance implementado en el PR

La persona pulsa **INICIAR VISIÓN DE PANTALLA**, Android presenta el diálogo oficial de MediaProjection y JARVIS solicita un servicio en primer plano solamente tras recibir permiso. Cada permiso autoriza una sola sesión; no se reutiliza en la siguiente.

El servicio muestra una notificación persistente **JARVIS observa tu pantalla** con una acción visible **DETENER OBSERVACIÓN**. En la app también existe **DETENER VISIÓN DE PANTALLA**. Un callback del sistema detiene la sesión cuando Android revoca la proyección.

- Las muestras se limitan a **una cada 3 segundos** y la sesión caduca como máximo a los **10 minutos**, incluso si no llegan nuevos fotogramas.
- El análisis usa OCR ML Kit empaquetado, detecta solo la **cantidad** de posibles botones, y deja en memoria RAM solo ese conteo para mostrarlo en la UI.
- No escribe vídeos, capturas, texto OCR, contraseñas ni datos de pantalla en archivos, logs, reportes ni servicios externos.
- No ejecuta gestos, clics o instrucciones capturadas.
- El servicio no está exportado y usa el tipo de primer plano `mediaProjection`. Requiere los permisos de servicio declarados en el manifiesto para Android 14+.

## Advertencias
La captura puede incluir información sensible visible en pantalla. **No utilices esta función en apps bancarias, contraseñas ni chats privados**. Las ventanas marcadas FLAG_SECURE se protegen según las reglas de Android; no se puede garantizar filtrado perfecto de toda información privada. El permiso del sistema y una notificación visible son condiciones de uso, no equivalen a una auditoría de privacidad.

**Pruebas pendientes:** comprobar en un Android físico (API 26, 34, 35/36 según disponibilidad) la solicitud de permiso, llamada a `createVirtualDisplay`, inicio de notificación, paro desde notificación/barra del sistema/bloqueo de pantalla, cambio de rotación, RAM y ausencia de fugas. La compilación y las pruebas unitarias NO acreditan todavía operación física segura.

Fuentes: guía oficial Android MediaProjection y su documentación de requisitos Android 14.
