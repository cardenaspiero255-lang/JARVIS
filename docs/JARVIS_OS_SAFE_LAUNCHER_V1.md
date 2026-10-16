# JARVIS OS V1 — Launcher reversible para el Vivo V25 Pro 5G

## Qué es
Una **pantalla de inicio opcional** (HOME launcher) integrada en la misma APK de JARVIS.
No es una ROM, un segundo Android ni un nuevo kernel. Conserva **Funtouch OS 14,
Android 14, aplicaciones, datos, particiones y bootloader** sin cambios.

La interfaz inicial es roja/negra, con un reactor estático de bajo consumo,
entrada al asistente OMEGA, botón de GameHub Ultra, buscador y listado de
aplicaciones instaladas que Android expone como lanzables. Usa componentes
nativos de Android, sin servicios residentes adicionales.

## Cómo probar sin cambiar la pantalla de inicio

1. Instala el APK **de prueba** desde el workflow de Android shell.
2. Abre la aplicación **JARVIS OMEGA** desde su icono.
3. Toca **PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH**.
4. Recorre la interfaz, busca una aplicación y comprueba el botón JARVIS.
5. Usa Atrás para abandonar la vista previa. Funtouch sigue siendo el launcher.

Para utilizar JARVIS OS como launcher opcional, ve a **ELEGIR PANTALLA DE INICIO**,
que abre la pantalla de inicio predeterminada de Android si el fabricante la
expone. Selecciona JARVIS OS **solo si lo quieres**. El sistema operativo nunca
cambia de forma silenciosa esa selección.

## Cómo volver al inicio original

1. En JARVIS OS pulsa **ELEGIR PANTALLA DE INICIO**.
2. En los ajustes que abra Vivo elige la aplicación original de inicio
   (el nombre depende de la versión de Funtouch OS).
3. Si Vivo no abre ese panel directo, usa **AJUSTES ORIGINALES DE ANDROID** y
   localiza Apps > Apps predeterminadas > App de inicio.
4. Desinstalar la APK *también* elimina este launcher, pero no lo recomendamos
   como primer método porque la aplicación contiene el asistente y memoria local.

## Límites técnicos

- JARVIS OS no recibe root ni privilegios de sistema, no puede quitar permisos,
  acceder a archivos privados de otras apps ni modificar CPU/GPU del kernel.
- El catálogo muestra únicamente actividades `MAIN/LAUNCHER` que Android
  hace visibles con un filtro de consultas limitado, sin `QUERY_ALL_PACKAGES`.
- Solo ejecuta aplicaciones tras pulsación explícita del usuario.
- El botón GameHub solo intenta abrir el paquete conocido, si está disponible.
- No configura un launcher predeterminado automáticamente, ni instala drivers,
  ROMs, recovery o un sistema alternativo.
- IA local avanzada/Qwen permanece en trabajo aparte (PR #53): no se duplica
  ni se introduce código del PR #53 dentro del launcher V1.

## Validación

- Contrato estático: `python -m unittest discover -s tests -v`.
- Tests unitarios Android: `gradle -p apps/android --no-daemon :app:testDebugUnitTest`.
- APK e instrumentación compiladas en Android shell CI.
- Smoke `JarvisHomeInstrumentedTest` registra las acciones visibles sin tocar
  las preferencias HOME del sistema; ejecutar en emulador o dispositivo.
- **Prueba física de Vivo V25 Pro pendiente:** compatibilidad del selector HOME,
  comportamiento de Atrás, fluidez de scroll, abrir apps, volver a Funtouch,
  pausas/reanudación, consumo de batería, rotación y funcionamiento con llamadas.

No afirmar certificación o compatibilidad física hasta realizar los ensayos.
Nunca fusionar este prototipo con CI roja o defectos de privacidad pendientes.
