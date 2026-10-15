# JARVIS OMEGA Android 0.1 — first on-device milestone

This is a deliberately limited, installable Android shell (min SDK 26;
target SDK 36). It uses the Java/Kotlin Android SDK without privileged
automation or additional runtime permissions.

## Implemented
- Red/black interface with an explicit Observer mode.
- User-triggered Android speech recognition activity in Spanish (es-CL).
- Deterministic on-device interpretation of the recognized text (no LLM).
- Offline local status command and user-triggered accessibility settings.
- Fail-closed execution policy: cannot tap, send messages, merge PRs,
  launch other apps or invoke Shizuku.
- Privacy disclosure: Android's speech provider may use its own online
  service. JARVIS itself has no continuous microphone service or storage.
- JVM unit tests of command classification and device-action gating.

## Build

Run Android Shell GitHub Actions. The job builds a debug APK and uploads it
as artifact jarvis-omega-debug-apk. The artifact is test software only:
not a signed production release, and not evidence of device E2E success.

Local prerequisites: Android SDK 36, Build Tools 36, Gradle 8.13 and JDK 17.
Command: gradle -p apps/android :app:testDebugUnitTest :app:assembleDebug

## Still not implemented
No wake-word engine, always-listening service, general-purpose accessibility
executor, screen/vision recognition, memory, LLM orchestration, remote auth, automatic
GitHub actions, background service, Android instrumentation tests, or
authenticated Ultra Sentinel / GameHub link.

Android permissions and component enablement must be user-visible and
verified at runtime before any future app-control capability can be enabled.
The repository owner can approve engineering changes alone; operating-system
permissions are separately controlled by Android and the device owner.

## Omega Hands — opt-in y parada de emergencia

El PR #6 añade un servicio de accesibilidad protegido por Android para la
acción HOME solicitada desde un botón visible, con autorización efímera y
parada de emergencia. No extrae contenido de pantalla ni realiza gestos.
Consultar [README-HANDS](../apps/android/README-HANDS.md) y verificar en
un dispositivo real antes de reclamar soporte de producción.
