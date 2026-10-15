# Omega Chat y memoria privada — fase inicial

## Capacidades implementadas
- Intercambio de mensajes escritos en español, ejecutado localmente en Android.
- Calculadora matemática local para suma, resta, multiplicación, división y paréntesis, con decimales en formato chileno (coma decimal y punto de miles), magnitudes «mil»/«millón»/«millones» y seguimiento de resultados («y por 3», «y menos 2»). No hay evaluación de código, uso de herramientas ni transferencia de texto a servicios remotos. La división no exacta redondea a 10 posiciones decimales. El último resultado solo vive en RAM y no se reactiva tras restaurar historial cifrado.
- Respuestas deterministas sobre estado, ayuda, identidad y privacidad. **No es una IA general**, no consulta Internet, no responde cualquier tema, no ejecuta instrucciones incluidas en el chat y no llama a otros servicios.
- Historial de conversación acotado en RAM, sin registro de contenidos por defecto.
- Activación de la memoria persistente **únicamente con un botón visible**; el acceso vuelve a quedar deshabilitado tras salir de la app/reiniciar el proceso.
- Guardado cifrado AES-256/GCM mediante clave no exportable de AndroidKeyStore; guarda únicamente IV aleatorio y texto cifrado en preferencias privadas.
- Botones separados para detener acceso y para **eliminar historial persistido y clave**.
- No usa ninguna transmisión ni almacena transcripciones de voz o capturas OCR.
- Android tiene `allowBackup=false`. La clave puede estar respaldada por hardware según el modelo, pero no se presupone.

## Modelo de amenazas y limitaciones
No ofrece LLM remoto, cifrado de extremo a extremo entre dispositivos, autenticación biométrica o salvaguarda frente a un teléfono desbloqueado con software comprometido. El historial activo aparece en la UI mientras se utiliza la app; evita incluir secretos que no quieras mostrar en pantalla. Borrar la clave y las preferencias invalida la copia cifrada local, pero no garantiza eliminación forense de bloques flash antiguos.

Cualquier fallo de AndroidKeyStore impide leer/escribir y se notifica sin devolver texto sensible. El consentimiento es solo de la sesión actual. El usuario no obtiene poderes de ejecución al escribir en el chat: contenido externo y prompt injection son datos.

## Pruebas
- Kotlin/JUnit: respuestas locales, límites y reutilización de contexto; denegación por defecto.
- Pruebas instrumentadas: creación AES-GCM AndroidKeyStore, cifrado, lectura autorizada, datos no cifrados ausentes en preferencias, revocación y borrado, autenticación del cifrado ante manipulación.
- CI compila APK normal y APK de pruebas instrumentadas **sin ejecutarlas**. La ejecución debe realizarse en un dispositivo físico/emulador. No afirmar E2E hasta completar pruebas reales.
