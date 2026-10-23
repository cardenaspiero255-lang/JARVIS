# JARVIS OMEGA — Auditoría de componentes reutilizables y plan de ejecución (18-10-2026)

**Estado:** investigación + diseño, NO implementación ni certificación.  
**Base observada:** `main` @ `2d5fbb4e9e86c5c7df5921579c69586159778b6a`.  
**Objetivo:** terminar JARVIS Android de forma fiable y añadir un agente Windows, reutilizando software libre verificado sin sustituir trabajo existente ni introducir permisos ocultos.

## 1. Reglas de integración

- Conservar el núcleo de contratos y seguridad de JARVIS; no clonar ni fusionar indiscriminadamente programas completos.
- Preferir adaptadores/versiones fijadas con SHA, SBOM, licencia de código + licencia de modelos + aviso de terceros.
- Ningún modelo, audio, pantalla ni token se envía a servicios externos sin configuración y consentimiento informados.
- Separar **planificación** (LLM no confiable) de **ejecución** (política de capacidades con consentimiento, límites de alcance y verificación posterior).
- Sin ejecución de shell arbitraria, bypass de permisos, root o fusiones automáticas de PR. Detención de emergencia y revocación obligatorias.
- No afirmar escucha de pantalla apagada, identificación de propietario o controles Windows sin pruebas físicas/E2E.
- Diseño sin VPS permanente. Windows y Android pueden funcionar por separado; sincronización opcional emparejada/cifrada.

## 2. Inventario de candidatos verificados

| Candidato / repositorio | Evidencia de utilidad | Licencia raíz verificada | Decisión inicial y límites |
| --- | --- | --- | --- |
| [Dodaios](https://github.com/Dodaios/Dodaios) | Agentes persistentes, IDE de agentes, plugins, MCP, voz, memoria, control del escritorio | Apache-2.0 | **EVALUAR COMO BASE DE WINDOWS**; benchmark de consumo RAM/CPU, tamaño, seguridad y viabilidad de reutilización de módulos antes de seleccionar fork o adaptador |
| [OpenDex](https://github.com/wassgha/opendex) | Voz configurable, wake word, micrófono, TTS, temas, permiso de acciones, computer-use | MIT | **REFERENCIA/ADAPTADOR DE VOZ Y UX WINDOWS**; no importar Electron entero al Android; comprobar si el soporte MCP es actual o solo roadmap |
| [Microsoft UFO³](https://github.com/microsoft/UFO) | UI Automation Windows, agentes de aplicaciones y orquestación multidispositivo | MIT | **EVALUAR EJECUTOR WINDOWS** aislado; dependencia de modelos/hardware y autorización por tarea |
| [REA](https://github.com/morluto/rea) | Análisis de sitios/apps y reportes de ingeniería inversa | MIT | **ETAPA POSTERIOR, EN PROCESO AISLADO**; usar solo sobre software autorizado; informes, no código fuente original ni replicación de recursos protegidos |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Android ARM64, keyword spotting, speaker embeddings, ASR, TTS, VAD | Apache-2.0 | **PILOTO ANDROID** tras evaluar el ASR Vosk existente; modelos independientes requieren licencia, SHA, mediciones FAR/FRR, energía y tamaño |
| [GPTWake](https://github.com/suddenBook/GPTWake) | Patrón Android de activación offline con pantalla bloqueada | Apache-2.0 | **ESTUDIO, NO COPIA DIRECTA**; resolver restricciones Android 14 de foreground micrófono, arranque, OEM, consentimiento, latencia y versiones de idioma |
| [Vosk](https://github.com/alphacep/vosk-api) | Reconocimiento de voz offline | Apache-2.0 | **YA EN PR #65, NO DUPLICAR**; probar español real y reloj/cobertura antes de decidir si reemplazar |
| [openWakeWord](https://github.com/dscripka/openWakeWord) | Detección local de palabra de activación | Apache-2.0 | **ALTERNATIVA A BENCHMARK**; validar modelos, idioma, falsos positivos y consumo ARM64 |
| [ONEPUNCHMAN411/Jarvis](https://github.com/ONEPUNCHMAN411/Jarvis) | Arquitectura Windows de pywinauto, Silero VAD, Playwright, TTS y fallback LLM | MIT (README; revisar archivo de licencia antes de copiar) | **REFERENCIA**, no base: el proyecto documenta un instalador grande y requisito recomendado de 8 GB RAM |
| [OpenVoiceOS](https://github.com/OpenVoiceOS/OpenVoiceOS) | Servicios desacoplados de wake, ASR, TTS, habilidades, message bus | Revisar licencias por paquete | **REFERENCIA DE PROTOCOLOS**; orientación Linux impide usarlo como sustituto inmediato Windows/Android |

**Fuentes técnicas:** READMEs y licencias raíz consultados en los repositorios arriba. Contratos sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx/tree/master/android (demos KWS y speaker identification). No se han instalado ni ejecutado estos programas, sus builds ni pruebas.

## 3. Trabajo ya existente: evitar regresiones/duplicación

| Proyecto propio | Observación | Regla |
| --- | --- | --- |
| [PR #65](https://github.com/cardenaspiero255-lang/JARVIS/pull/65) | Vosk español, diagnóstico de micrófono/TTS, foreground 60 s y políticas de privacidad en preview | Prioridad **P0**: reproducir en Vivo V25 Pro encendido/apagado, evaluar fallos. CI no demuestra compatibilidad real |
| [PR #64](https://github.com/cardenaspiero255-lang/JARVIS/pull/64) | Comandos seguros y ChatGPT/GameHub bajo confirmación | Mantener separado del motor de voz hasta validar contratos |
| [PR #58–#62](https://github.com/cardenaspiero255-lang/JARVIS/pulls) | OS V3, Shizuku, Termux, diagnóstico y puente gaming, varios PR apilados | Resolver dependencias y CI en orden; no copiar otros frameworks sobre estas ramas |
| [PR #53](https://github.com/cardenaspiero255-lang/JARVIS/pull/53) | Modelo local opcional experimental | Conservar opción, medir RAM/tokens y evitar descarga automática costosa |
| `main` | Android MVP, núcleo Python/contratos, UI y TTS básicos; no certificado | No declarar funciones de ramas como integradas en main |

## 4. Orden de ejecución y puertas de aceptación

### Fase 0 — Android que escucha y habla (P0, antes que UI/ingeniería inversa)
1. Inspeccionar estado de PR #65 en HEAD inmutable y ejecutar checks Android/test/contract; registrar SHA.
2. Validación física **Vivo V25 Pro**: Vosk carga, reconoce español «Jarvis, dime la hora», voz audible, tiempos reales, micrófono bloqueado, fallo de modelo, cierre de sesión; usar estado registrado, no inferir desde la pantalla.
3. Comparar Vosk con *una* prueba aislada de KWS sherpa-onnx, no mezclar ambos en la misma prueba. Registrar falsos despertares/hora, falsos rechazos, RAM, energía y CPU con pantalla apagada 30–60 min.
4. Identidad de locutor: enrolamiento opt-in, embeddings cifrados localmente, eliminar/revocar, FAR/FRR, prueba con grabación y voz ajena; **voz sola nunca autoriza operación sensible**.
**Hecho solo si:** pruebas en dispositivo documentadas y salida auditable, y no degradación de privacidad.

### Fase 1 — Windows mínimo útil (P1)
1. Comparar una prueba acotada de Dodaios vs estructura Python/Electron ligera, midiendo inicio, RAM, CPU, dependencias, permisos y licencia, también en PC de recursos limitados.
2. Implementar `WindowsAgent` como proceso separado con API local autenticada, allowlist de capacidades, apertura de aplicaciones, lectura de estado y respuesta observada; el modelo solo propone.
3. Voz: reutilizar arquitectura de OpenDex (wake → VAD → STT → planner → policy → tool → TTS) con stop/interrupciones, sin habilitar ejecución de shell arbitraria.
**Hecho solo si:** Windows 10/11 arranca, oye, responde, abre una app autorizada, verifica resultado, se detiene, falla cerrado y pasa E2E.

### Fase 2 — Automatización de escritorio y programación (P1/P2)
1. Integración adaptadora con UFO Windows UI Automation para ventanas accesibles + fallback de visión consentido; no clic por coordenadas cuando exista control estructurado.
2. Agente programador en workspace aislado: plan → tests RED → implementación → tests GREEN → revisión de diff → CI → propuesta de PR; nunca push/merge automático sin la política aprobada.
3. Memoria SQLite cifrada o almacenamiento del sistema, retención/borrado, filtros de inyección desde páginas/PR.
**Hecho solo si:** tareas repetibles con trazas, cancelación y casos adversariales.

### Fase 3 — REA e ingeniería inversa (P2)
- Ejecutar REA en sandbox con objetivos permitidos, límites de red/archivos, control de herramientas, salida de informe con evidencias y pseudocódigo.
- Generar implementaciones propias sin copiar assets privados, branding, fuentes inaccesibles ni evadir DRM/licencias.
**Hecho solo si:** reconstrucción de una aplicación de muestra propia, comparación de comportamiento y pruebas automáticas reproducibles.

### Fase 4 — Windows ↔ Android (P2)
- Emparejamiento explícito con clave por dispositivo, TLS/mTLS según entorno, permisos por acción, versionado de protocolo, scopes, nonce/idempotencia y revocación.
- Android sigue operando sin PC; el PC nunca asume root ni Shizuku por el solo hecho de estar emparejado.
**Hecho solo si:** recuperación de desconexión, controles antifuga, manejo de sesión bloqueada y prueba en hardware real.

## 5. Métricas y criterios de liberación

- Voz: wake recall/false accepts/hora, latencia P50/P95, cancelación, reconocimiento español CL con ruido, FAR/FRR, consumo en reposo, RAM.
- Agentes: tasa de éxito de tareas reproducibles, acciones no autorizadas = **cero**, cobertura regresiones, logging redactado, recuperación tras interrupción.
- Windows: iniciar/abrir/cerrar aplicación y observar resultado; error de permisos causa rechazo, no fallback de privilegios.
- Dependencias: licencias de repo/modelos, artefactos y hashes fijados, SBOM, CVEs, integridad y cambios upstream.
- CI: tests + APK/installer en commit inmutable; badges verdes no equivalen a test físico.
- Política de fusión: evitar conflictos con PR apilados; no fusionar nuevos componentes mientras exista falla bloqueante o evidencia requerida pendiente.

## 6. Siguiente cambio de código recomendado

**Primer PR implementable tras esta auditoría:** instrumentación adicional del PR #65 / harness de medición de wake-voice para Vivo (sin alterar permisos) con tests de energía/recuperación; después preparar un *spike* Windows totalmente independiente y mínimo. Hasta verificar físicamente el Android, no declarar el «siempre despierto» terminado.

**No implementado en este PR:** importación de dependencias, modelos nuevos, permisos, ejecutor Windows, puente remoto, REA y cambios funcionales. Mantenerlo como borrador para inspección.
