# Omega Voice → GameHub Ultra: lanzamiento confirmado

## Flujo disponible
1. En JARVIS Android, pulsar **HABLAR CON JARVIS** (reconocimiento de voz del sistema).
2. Decir «Jarvis abre GameHub Ultra» (frases permitidas exactas, no comandos generales).
3. JARVIS solo registra una **solicitud efímera**, sin abrir GameHub.
4. Pulsar **CONFIRMAR APERTURA DE GAMEHUB POR VOZ** antes de 30 segundos.
5. JARVIS consume el consentimiento una sola vez y usa el lanzador ya existente para abrir **exclusivamente** el paquete `com.cardenaspiero255.gamehubultra`.

## Límites de seguridad
- El contenido reconocido por voz nunca autoriza directamente abrir aplicaciones.
- Cada solicitud caduca, se invalida al pasar a segundo plano y no se guarda entre reinicios.
- Confirmar un tipo de acción diferente invalida la solicitud; las frases compuestas o desconocidas se rechazan.
- No hay lectura de pantalla, acceso a otras apps, IPC con el motor de GameHub, permisos ADB/Shizuku ni comandos de red.
- El reconocimiento de voz del sistema puede recurrir a un proveedor remoto; este bloque no añade audio permanente.
- El creador es el único aprobador humano, pero la CI y las autorizaciones Android siguen siendo obligatorias.

## Puertas de validación
Las pruebas Kotlin/JUnit cubren clasificación y consentimiento; los contratos Python prohíben la apertura directa desde una transcripción. Compilar APK en CI **no demuestra el recorrido físico de reconocimiento/confirmación**, que sigue pendiente en un teléfono con GameHub instalado.
