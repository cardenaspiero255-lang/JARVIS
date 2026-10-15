# Omega Vision — OCR local sobre imágenes elegidas

## Función real implementada

1. En JARVIS Android, pulsa **ANALIZAR IMAGEN SELECCIONADA**.
2. Android abre el selector de documentos con filtro de imágenes.
3. Selecciona expresamente una captura o fotografía.
4. JARVIS analiza la imagen con **ML Kit Text Recognition v2 (latino) empaquetado dentro de la APK**, sin solicitar permisos de cámara, lectura permanente del almacenamiento, captura global ni acceso a Internet.
5. Se muestra una previsualización limitada a 320 caracteres.

El texto reconocido **se considera datos no confiables**: jamás autoriza clics, pagos, comandos, acciones de accesibilidad, envío de información ni merges de GitHub. No se persisten transcripciones OCR ni copias de la imagen. El módulo deja de aceptar resultados cuando se destruye la Activity.

## Recursos y seguridad

- Imágenes máximo 12.000 px por dimensión y 24 megapíxeles de resolución declarada.
- Lectura comprimida limitada a 16 MiB por pasada, imágenes decodificadas con muestreo para un máximo aproximado de 2048 px por eje.
- El límite de bytes cubre `read()`, lecturas por bloques, `skip()` y lecturas repetidas tras `reset()`; un proveedor no puede recuperar presupuesto reubicando el cursor. Al consumir exactamente el tope, una petición adicional se rechaza de forma conservadora. Se crea un lector nuevo para cada uno de los dos pases del decodificador.
- Decodificación en un hilo de trabajo, OCR asíncrono en dispositivo.
- Errores de proveedor, formatos inválidos y falta de memoria se convierten en estados visibles sin ejecutar instrucciones.
- Los documentos son seleccionados desde una app del sistema/proveedor; JARVIS no inicia vigilancia permanente de pantalla.
- La selección usa códigos de solicitud distintos por pulsación y autorización de un solo uso, con caducidad a los cinco minutos. El bloqueo de privacidad, la destrucción de la Activity o el fallo de lanzamiento revocan la solicitud. Resultados de selecciones antiguas o duplicadas no pueden reactivar OCR; si se agota el espacio reservado de códigos en una Activity, se deniegan nuevos intentos hasta reiniciar la pantalla. La salida del primer plano durante el selector del sistema no concede nuevos permisos.

## Diferencia con Omega Vision completo

Esta fase **sí realiza OCR local de imágenes que seleccionas**. Todavía no incluye MediaProjection, reconocimiento continuo de elementos UI, detección de botones en pantalla, seguimiento de ventanas, comprensión multimodal por LLM ni automatización mediante visión. Cada capacidad posterior requiere consentimiento, límites y pruebas independientes.

Las pruebas en CI validan contratos, compilación y unit tests. Un dispositivo real sigue siendo necesario para verificar selección de documentos, tasa de reconocimiento y rendimiento. Deben evitarse capturas con contraseñas o información privada cuando no se desee mostrar su texto en pantalla.

Modelo de terceros: `com.google.mlkit:text-recognition:16.0.1`, variante empaquetada especificada en documentación oficial de Google ML Kit.
