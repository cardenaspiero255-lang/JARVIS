# JARVIS — reutilización responsable de asistentes Android (20-10-2026)

## Fuentes verificadas

| Proyecto | Licencia encontrada en su archivo LICENSE | Capacidades relevantes | Decisión |
| --- | --- | --- | --- |
| [judejulius/jarvis](https://github.com/judejulius/jarvis) | MIT (2026 Mohammad Joud Julius) | Wake word local, barge-in, continuidad de diálogo, Accessibility | **Estudiar** el control de turnos; no trasplantar agente de accesibilidad sin gates |
| [tokenarc/open-jarvis](https://github.com/tokenarc/open-jarvis) | MIT (2024 TokenArc) | Tutorial Mode, proveedores de LLM, memoria local | **Reutilizar el patrón** tutorial, con código nuevo y funcionamiento de solo lectura |
| [alwin123098/jarvis-android](https://github.com/alwin123098/jarvis-android) | MIT (2026 Codotype) | llama.cpp/whisper.cpp, descargas optativas de pesos, TTS local | **Comparar** con GGUF/Vosk existentes antes de agregar dependencias |
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | KWS, ASR, speaker embeddings | Ya integrado como AAR en JARVIS; continuar pruebas físicas sin duplicar motor |
| [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Apache-2.0 | STT local | Ya usado para ASR español dentro de APK |

La tabla documenta los **archivos LICENSE consultados**, no audita automáticamente
las licencias de todos los pesos, modelos ni dependencias transitivas. Antes de
copiar código, incluir avisos, versión/commit y revisar dependencias; aquí no
se ha copiado código de terceros: el tutorial es una implementación original.

## Primera función implementada: guías táctiles offline

Ruta: `GUÍAS JARVIS — VOZ, SHIZUKU, TERMUX` en la pantalla principal.

El usuario puede elegir cinco recorridos y avanzar/retroceder:
- voz española local con diagnóstico de micrófono,
- activación y separación de identificación del propietario,
- conexión del SDK Shizuku,
- prueba controlada de Termux,
- apertura segura de GameHub Ultra.

Propiedades: sin Internet, sin nuevas dependencias ni permisos, sin
autoinicio, sin pantalla flotante, sin Accessibility de terceros, sin
comandos shell, sin activación automática del micrófono y sin escribir datos.
La pantalla está marcada `android:exported=false`; la navegación vive
solo en memoria y se restaura tras recreación de Activity.
**No reemplaza una prueba física** de cada servicio. Las instrucciones
describen lo que el usuario debe verificar y no dan certificados.

Prueba JVM:
`gradle -p apps/android :app:testDebugUnitTest --tests com.cardenaspiero255.jarvis.core.GuidedTutorialTest`.

## Segunda función implementada: consulta de batería por voz

El motor Vosk y la ventana explícitamente autorizada de voz ya existían.
Se amplía la clasificación de comandos **exactos**, no el conjunto de
capacidades del teléfono:

- `Jarvis, batería`;
- `Jarvis, cuánta batería tengo`;
- `Jarvis, dime la batería`;
- tras `Jarvis`, seguimiento dentro de ocho segundos: `batería` o
  `cuánta batería tengo`.

Solo se consulta el broadcast local protegido
`Intent.ACTION_BATTERY_CHANGED` mediante la API oficial Android. No requiere
permisos Android nuevos, no navega por otras apps y no cambia ajustes.
Si el porcentaje no es válido se comunica que no está disponible.
Las negaciones y órdenes concatenadas se rechazan, y la identidad no se
presume verificada. Las frases `Jarvis, para` y `Jarvis, detente` siguen
deteniendo la sesión. Las pruebas locales verifican el parser y los límites
del seguimiento, pero **no certifican precisión acústica real**.

El reconocedor de gramática restringida exige ahora vocabulario de batería.
Si el diccionario instalado no lo soporta, se usa Vosk offline no
restringido en vez de aceptar un diccionario obsoleto que impida esta función.

## Prioridades posteriores — no implementadas en este PR

1. **Pipeline ASR → GGUF → TTS con cancelación**: reutilizar motor local ya
   integrado, añadir límites/consentimiento y pruebas E2E reales de audio.
2. **KWS bajo pantalla bloqueada**: evolucionar el puente sherpa-onnx,
   medir wake y batería con hardware real; las políticas de Android 14+
   limitan micrófono en segundo plano.
3. **Voice Match**: validación acústica independiente, inscripción opt-in,
   FAR/FRR/replay y rechazo de identidad incierta. Nunca convertir wake
   word en autenticación.
4. **Agente de accesibilidad estilo judejulius**: comandos declarativos
   restringidos, dry-run y confirmación por paso, verificación posterior
   y STOP incondicional. No copiar un agente con permisos universales.
5. **Modelo optativo de ASR alternativo**: medir costes de memoria, idioma
   es-CL y rendimiento antes de añadir Whisper a un APK que ya usa Vosk.

## No afirmar

Esta implementación **no** habilita escuchar permanentemente, reconocer
automáticamente al propietario, controlar cualquier app, ni sustituye
una certificación del producto. La presencia de APIs/modelos no acredita
calidad acústica ni compatibilidad con el Vivo V25 Pro.

La licencia MIT exige conservar el aviso de copyright y licencia al
redistribuir *código de esos repositorios*. No se han importado archivos
de terceros en este PR; solo se atribuyen conceptos e interfaces públicos.
