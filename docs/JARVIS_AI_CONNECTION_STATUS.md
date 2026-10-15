# JARVIS OMEGA: conexión de IA sin volver a introducir la URL

## Estado y diagnóstico

El servidor público es **https://jarvis-ai-gateway.vercel.app/v1/chat**.
Esta URL está incorporada en la app Android y no es una credencial.
Nunca se incluye en el APK una clave OpenAI ni un `JARVIS_GATEWAY_TOKEN`.

Si JARVIS muestra «No tengo una conexión a un modelo conversacional...», significa
que respondió el motor **local**; no demuestra que GPT-6 Astra haya fallado.
Antes de esta corrección el cliente entraba silenciosamente al motor local
cuando el emparejamiento no se guardaba o no podía descifrarse.

En esta versión, desde el chat de la misma interfaz, escribe **`estado ia`**
y toca ENVIAR MENSAJE. No hace ninguna solicitud a OpenAI y no revela claves.
Posibles respuestas:

- **IA emparejada**: la configuración cifrada se pudo abrir en este Android.
  Envía una pregunta escrita para probar la conectividad real; no significa
  por sí sola que el modelo remoto funcione o que exista crédito API.
- **IA no emparejada**: aún no se guardó token; escribe **`conectar ia`**.
- **Configuración cifrada antigua o ilegible**: vuelve a emparejar.
  Esto puede ocurrir al reinstalar una APK de depuración y perder datos
  locales o una clave AndroidKeyStore.
- **401/403**: el token personal difiere del configurado en Vercel.
- **502**: el backend no pudo obtener una respuesta del modelo; revisar
  saldo y acceso al modelo de API sin mostrar secretos.
- **503**: el backend no tiene los secretos configurados.
- **429**: límite temporal de solicitudes.

## Emparejamiento seguro una sola vez

1. El propietario ya desplegó **JARVIS AI Gateway** en Vercel, con
   `OPENAI_API_KEY` y una variable distinta `JARVIS_GATEWAY_TOKEN`.
2. Escribe **`conectar ia`** en el chat actual. Ya **no hay campo HTTPS**.
3. Introduce **solo** el valor de `JARVIS_GATEWAY_TOKEN` (32–256 caracteres
   sin espacios). Nunca la clave API OpenAI.
4. Marca la casilla de voz en la nube **solo si quieres** que las preguntas
   habladas se transmitan a OpenAI. Pulsa **Conectar**.
5. Comprueba **`estado ia`** y, a continuación, envía una pregunta nueva.
   El emparejamiento se cifra usando AndroidKeyStore AES-GCM y puede
   reutilizarse al abrir la app sin volver a introducir datos.
6. Para quitar el acceso de **este dispositivo**, escribe
   **`desconectar ia`**. Para revocar también una copia filtrada del
   token, rótalo en Vercel.

La aplicación mantiene intactos los botones, el ícono y los controles
visuales existentes. No existe escucha continua ni autorización implícita
de audio en la nube.

## ¿Y no introducir ningún token?

Para que una app descargable no necesite **ninguna** credencial de
emparejamiento, hay que incorporar autenticación individual (por ejemplo,
inicio de sesión con identidad comprobada), emisión de tokens cortos
en el servidor, revocación, límites de gasto por usuario y defensas
contra abuso. **No** es seguro quitar la autenticación ni distribuir
la clave del servidor en el APK. Este trabajo está pendiente.

La build es de depuración: si una APK nueva no permite actualizar la
anterior debido a una firma diferente y Android pide desinstalar,
desinstalar elimina datos y emparejamientos previos. Hace falta firma
estable de actualización para garantizar conservación de credenciales.
No se debe recurrir a incrustar tokens como solución.

El estado **SSS+** no está certificado por estas mejoras.
