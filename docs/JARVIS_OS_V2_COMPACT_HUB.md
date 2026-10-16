# JARVIS OS V2: pantalla de inicio compacta

**Dispositivo piloto:** Vivo V25 Pro 5G con Funtouch OS 14 y Android 14.
**Estado:** prototipo; no sustituye el firmware, los controladores, Android ni Funtouch OS.
**Rama:** `feat/jarvis-os-v2-compact-hub` a partir del PR #54, sin fusionar a `main`.

## Problema de V1

La interfaz del asistente contenía 37 acciones separadas y el launcher
mostraba por defecto hasta 60 entradas de aplicaciones instaladas.
Esto consumía demasiado espacio y dificultaba encontrar funciones.

## Cambio de interacción

### Inicio JARVIS OS V2 PREVIEW

| Módulo | Aparece al tocar | No hace automáticamente |
|---|---|---|
| JARVIS | Acceso al asistente local existente | Iniciar micrófono, guardar memoria, pedir permisos |
| APPS | Búsqueda y 12 apps iniciales; «VER TODAS LAS APLICACIONES» ofrece hasta 60; la búsqueda consulta la lista completa | Abrir ninguna app sin pulsarla |
| GAMING | Abrir GameHub Ultra o el asesor existente en JARVIS | Ajustar CPU/GPU, iniciar juegos, saltar verificaciones |
| SISTEMA | Ajustes originales de Android y selector opcional de inicio | Cambiar launcher predeterminado ni modificar Funtouch |

Siempre se muestra **un único panel**; los demás controles están
cerrados y los cuatro módulos siguen siendo visibles. La elección de
módulo se recuerda al rotar/recrear la actividad, nunca mediante un
servicio de fondo.

### Dentro de JARVIS OMEGA

Los botones existentes **no se eliminan** ni se combinan en cadenas
automáticas. Se reorganizan en cuatro categorías desplegables:

- **CHAT Y MEMORIA:** chat, leer respuesta, memoria cifrada y borrado.
- **JUEGOS Y RENDIMIENTO:** asesor gaming, perfiles, GameHub seguro,
  consultas de batería, RAM y estado térmico.
- **VISIÓN Y PANTALLA:** OCR de imágenes y controles de observación.
- **SISTEMA Y PRIVACIDAD:** diagnósticos, accesibilidad/Hands, ajustes
  de voz, visor JARVIS OS, permisos de capturas temporales.

**BLOQUEAR SESIÓN — PRIVACIDAD** y **HABLAR CON JARVIS** siguen
accesibles en la pantalla principal del asistente. Abrir un grupo
solo revela sus controles; no ejecuta acciones sensibles. En particular,
habilitar capturas, memoria, escucha o acciones de accesibilidad
requiere seguir pulsando su control específico y aceptar permisos
oficiales cuando corresponda.

## Convivencia y reversión

El debug APK está identificado como
`com.cardenaspiero255.jarvis.osv2preview`: se instala en paralelo tanto con **JARVIS OS V1 PREVIEW** (`com.cardenaspiero255.jarvis.ospreview`) como con la app original **JARVIS OMEGA** (`com.cardenaspiero255.jarvis`). No reemplaza ninguna de las dos ni comparte sus datos privados. En la selección HOME aparece como «JARVIS OS V2». Funtouch sigue instalado.

En el módulo **JARVIS**, «ABRIR JARVIS / IA Y VOZ» abre el asistente
**compacto V2** que está dentro de esta APK; su memoria local es separada
de la del JARVIS previamente instalado. El acceso secundario **JARVIS
ORIGINAL / HISTORIAL ANTERIOR** permite seguir usando la instalación y
la memoria originales, si existen. Jamás se migran ni borran sus datos
automáticamente.

En el módulo **GAMING**, «ABRIR ASESOR DE JUEGOS» abre solo la sección
«JUEGOS Y RENDIMIENTO» del asistente V2. Eso **no** inicia juegos, no
consulta permisos, no altera perfiles ni ejecuta un preflight sin pulsar.

Para volver al launcher Vivo: **SISTEMA → ELEGIR PANTALLA DE INICIO**,
elige la app de inicio original de Vivo. Si ese ajuste directo no existe,
**SISTEMA → AJUSTES ORIGINALES DE ANDROID**, luego apps predeterminadas.
No se requieren root, ADB, desbloqueo OEM o flasheo.

## Pruebas y criterios antes de fusionar

- `python -m unittest discover -s tests -v`
- `gradle -p apps/android --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
- `gradle -p apps/android --no-daemon :app:connectedDebugAndroidTest`
- Confirmar test de 4 módulos y exclusividad de panel visible en emulador.
- Confirmar que los controles sensibles de OMEGA siguen detrás de sus
  autorizaciones existentes.
- **Prueba física Vivo:** alternar módulos, buscar apps más allá de las 12,
  abrir/cerrar GameHub, pulsar Inicio, giro/reanudación, scroll,
  memoria y privacidad, consumo de batería, regreso a Funtouch.

No marcar «certificado» ni fusionar con CI roja o problemas físicos
desconocidos; la compatibilidad de V1 no sustituye la prueba de V2.
