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

## Lectura opcional de respuestas locales (Omega Voice)
- El botón **LEER ÚLTIMA RESPUESTA DEL CHAT** solo habla después de que el usuario haya activado una voz **offline** instalada y autorizado una lectura por pulsación.
- El origen permitido es la respuesta generada localmente durante la sesión actual: frases de estado y cálculos acotados. Una allowlist en `ChatSpeechPolicy` rechaza transcripciones arbitrarias, texto del usuario, OCR, historial restaurado y resultados de modelos externos.
- Al pasar a segundo plano, detener la voz, cambiar perfil o borrar el historial, se cancela la sesión de habla o se invalida la última respuesta; nunca hay autolectura.
- **Pendiente de validar:** audio audible en teléfono real, interrupción exacta durante cambios de actividad y compatibilidad de voces offline disponibles. CI/JUnit demuestra políticas y compilación, no acústica física.

## Cálculo por voz iniciado manualmente
- El botón **HABLAR CON JARVIS** abre el reconocedor de voz del sistema Android (que puede utilizar un proveedor externo, según el dispositivo). No se activa escucha continua.
- Para una transcripción que no sea un comando conocido, `VoiceMathGate` intenta **solo aritmética local y acotada**. Por ejemplo, «¿cuánto es un millón por quinientos?» no está soportado como números escritos con palabras; «¿cuánto es 1 millón por 500?» sí lo está.
- Las órdenes no matemáticas siguen rechazadas. Ninguna transcripción crea permisos, toca controles Android o llama a servicios.
- El reconocedor entrega una transcripción efímera: el fallback no almacena texto de voz ni lo añade al historial cifrado. Por ello **no conserva** resultados de matemáticas habladas entre invocaciones.
- Después de mostrar el resultado se puede usar voluntariamente el botón **LEER ÚLTIMA RESPUESTA DEL CHAT**, sujeto al consentimiento y la voz offline del bloque Omega Voice.
- La CI valida semántica y seguridad, **no** reconoce voz realmente en un teléfono. Siguen pendientes pruebas físicas del proveedor de voz, ciclo de vida y TTS.

## Expresiones numéricas habladas (bloque nuevo)

La calculadora local también reconoce cardinales españoles acotados en expresiones explícitas o con operadores: «un millón por quinientos», «dos mil quinientos más veintitrés», «cien dividido por cuatro» y «treinta y cuatro por dos». Estos ejemplos se calculan sin modelo remoto y no habilitan comandos sobre Android.

Se usa una gramática cerrada de unidades, decenas, centenas, miles y millones. Las palabras ajenas a esta gramática se rechazan, en vez de interpretarse como instrucciones. Se conservan los límites de longitud, magnitud, profundidad y ausencia de contexto persistente en voz. No se garantiza reconocimiento de frases arbitrarias, dictado natural general ni todas las variantes regionales. Las pruebas en teléfono físico del proveedor de transcripción Android siguen pendientes.

## Omega Voice: respuestas explicativas locales por pulsación

Desde **HABLAR CON JARVIS**, una transcripción que no corresponda a un comando permitido ni a aritmética puede responder ahora a un conjunto cerrado de preguntas en español: ayuda, identidad, privacidad, OCR de imágenes seleccionadas, cómo abrir GameHub y cómo detener la sesión. No interpreta preguntas generales como instrucciones, no almacena el texto reconocido y no accede a redes para generar respuestas.

Las respuestas pueden leerse posteriormente con **LEER ÚLTIMA RESPUESTA DEL CHAT**, sujeto a activación TTS local y pulsación independiente. No se habilita voz permanente. El proveedor de reconocimiento de Android puede usar conexión remota o no estar instalado; la lógica de JARVIS que procesa las transcripciones es local.

Los mensajes desconocidos, compuestos con otras órdenes o demasiado extensos se rechazan. Las explicaciones indican solo funciones efectivamente fusionadas en main; la visión continua MediaProjection sigue en PR #27 borrador, no está disponible en la APK main.

**Validaciones:** JUnit para expresiones, acentos, puntuación, composición maliciosa y TTS; contratos Python para impedir acciones Android, registro de transcripciones y respuestas remotas. Sigue pendiente la prueba de voz real en un dispositivo.

## Omega Voice: cálculos consecutivos de una sesión (PR #31)

La ruta de voz puede continuar un resultado numérico con «y por tres», «y menos dos» o «y dividido por dos» después de un cálculo previo. Por ejemplo: «dos más seis» → 8; «y por tres» → 24. Sigue utilizando `OfflineArithmetic` y su gramática local acotada; no ejecuta acciones.

La continuidad mantiene **únicamente un BigDecimal** y la marca de tiempo monotónica en memoria durante un máximo de **45 segundos entre operaciones válidas**. No conserva las frases dictadas, no escribe este estado en la memoria cifrada ni lo restaura al reiniciar. Cualquier comando distinto, frase inválida, error matemático o caducidad cancela el resultado. El botón **BLOQUEAR SESIÓN — PRIVACIDAD** y los cierres del Activity cancelan el contexto; únicamente se conserva a través del breve cambio a la interfaz externa de reconocimiento que el usuario inició explícitamente. Ese proveedor Android podría usar red, sin que JARVIS reciba privilegios adicionales.

A diferencia de una IA conversacional general, este bloque solo recuerda un número temporal; la conversación libre sigue limitada a respuestas locales verificables. La compilación y pruebas unitarias no prueban todavía voz física ni la continuidad de ciclo de vida de todos los fabricantes Android.
