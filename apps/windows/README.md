# JARVIS Windows — primer agente local real (experimental)

Esto es **código ejecutable**, no un mock de interfaz. Integra tres motores upstream sin copiarlos al repositorio:

- [pywinauto](https://github.com/pywinauto/pywinauto), para enumerar/enfocar ventanas de Windows mediante UI Automation.
- [Vosk](https://github.com/alphacep/vosk-api), para convertir una orden del micrófono a texto **sin Internet** y sin proveedor del sistema.
- [pyttsx3](https://github.com/nateshmbhat/pyttsx3), para reproducir la respuesta mediante la voz del sistema (SAPI5).
- `sounddevice` abre el micrófono **solo** después de `--listen` y lo cierra al terminar.

No es Dodaios/UFO/REA instalados. El motor es una primera integración independiente basada en patrones documentados; **no reproduce aún el JARVIS del vídeo**. No hay asistente permanente, wake-word en segundo plano, identificación biométrica, navegación arbitraria, IA generativa, PowerShell, REA, conexiones a GitHub ni permisos elevados.

## Requisitos e instalación (Windows 10/11)

Desde PowerShell:

```powershell
cd apps/windows
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-windows.txt
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\.venv\Scripts\python.exe -m jarvis_windows --command "Jarvis, qué hora es" --speak
.\.venv\Scripts\python.exe -m jarvis_windows --command "Jarvis ventanas abiertas"
.\.venv\Scripts\python.exe -m jarvis_windows --command "Jarvis abre Bloc de notas" --speak
```

## Reconocimiento del micrófono en español (opt-in)

Instala manualmente un modelo Vosk español en una carpeta privada. Puedes reutilizar el mismo modelo oficial previsto para el PR #65 Android: `vosk-model-small-es-0.42` desde https://alphacephei.com/vosk/models . Si usas el archivo ZIP oficial `vosk-model-small-es-0.42.zip`, verifica SHA-256 `09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f` **antes** de descomprimir. No se descarga modelo desde la aplicación.

```powershell
.\.venv\Scripts\python.exe -m jarvis_windows --listen --model "C:\Modelos\vosk-model-small-es-0.42" --speak
```

La escucha dura **máximo 12 segundos**, acepta una frase completa y solo ejecuta órdenes explícitas del conjunto autorizado: `Jarvis, qué hora es`, `Jarvis, ventanas abiertas`, `Jarvis enfoca <título exacto>`, `Jarvis abre Bloc de notas`, `Jarvis detente`. Las órdenes desconocidas o negadas no ejecutan acciones. El título de la ventana es una coincidencia estricta y, si existen varias, se rechaza el enfoque. Abrir Notepad solo declara la solicitud al proceso: no afirma que la ventana esté visible.

Para probar sin usar el micrófono usa `--command`. Para respuesta solo en texto omite `--speak`. El micrófono no permanece activo en pantalla bloqueada y **no** hay escucha 24/7. El modelo elegido y las voces de Windows determinan si el español funciona bien.

## Límites y seguridad

- No es una ruta remota ni un servicio HTTP: recibe entrada local, no expone puerto ni token.
- Ningún comando puede ejecutar shell libre, modificar archivos, comprar, instalar ni elevar privilegios.
- Una frase reconocida no verifica la identidad del hablante. La aplicación no almacena muestras de audio, transcripciones ni claves. Para usarla con acciones sensibles hace falta enrolamiento, consentimiento y política adicional.
- Una petición de enfoque `FOCUS_REQUESTED` no demuestra que Windows la haya completado. Se requiere validación de UI en una sesión interactiva real.
- Las dependencias Python están fijadas para la prueba, pero hay que inventariar sus dependencias transitivas, avisos y licencias antes de distribuir un instalador.
- En laptops con 4 GB RAM evaluar consumo de modelo, consumo en reposo y latencia; no prometer ejecución de LLM local grande.

## Pruebas y CI

```powershell
cd apps/windows
python -m unittest discover -s tests -v
```

Las pruebas usan inyección de ventanas/procesos falsos para verificar políticas sin manipular la máquina de CI. La acción GitHub `windows-agent.yml` instala las bibliotecas en Windows y ejecuta los tests; `--command "Jarvis dime la hora"` prueba el CLI. **No** sustituye pruebas físicas de micrófono/TTS, UI Automation en sesión real ni pruebas Android del PR #65.

## Continuación en orden

1. Terminar la validación física Vosk y pantalla bloqueada en el Vivo (PR #65) por separado.
2. Medir y extender Windows con wake-word KWS (sherpa-onnx), diarización/Voice Match y interrupción de voz; no habilitar autenticación sensible solo por biometría de voz.
3. Evaluar un adaptador de Dodaios y UFO³ cuando haya un criterio reproducible de memoria/CPU, aislamiento y permisos. No mezclar sus runtimes completos sin auditoría.
4. Incorporar REA en sandbox para análisis de software autorizado, después de tener auditoría y agentes de programación con pruebas.
5. Emparejar Android y Windows con autorización, TLS y revocación; cada app sigue siendo útil de forma independiente.


## Sesión de voz experimental (PR complementario)

En una sesión de Windows abierta manualmente, Vosk ahora puede procesar varias frases sin abrir el micrófono para cada pregunta:

```powershell
.\.venv\Scripts\python.exe -m jarvis_windows --wake-session --model "C:\\Modelos\\vosk-model-small-es-0.42" --session-seconds 60
```

Di `Jarvis`, espera una pausa, y luego `dime la hora` antes de ocho segundos. También puedes decir `Jarvis, dime la hora` en una sola frase. `Jarvis, detente` cierra la sesión. La lista exacta de comandos permitidos no cambia. La sesión tiene **máximo 60 s** (configurable a menos tiempo), no arranca automáticamente ni acepta comandos de shell o permisos privilegiados. `--speak` permite voz del sistema si está instalada; cuidado con realimentación acústica al usar altavoces y micrófono juntos.

**Limitación importante:** Vosk no detecta la voz del propietario. Esta opción es un proceso local iniciado deliberadamente, no hotword 24/7 ni un asistente de Windows de sistema. Primero hay que validar micrófono, batería/CPU y seguridad en hardware real.
