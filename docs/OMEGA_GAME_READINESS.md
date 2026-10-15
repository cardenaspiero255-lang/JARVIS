# Omega Game Readiness — evaluación local antes de jugar

**Función nueva:** el usuario elige un objetivo (equilibrado, competitivo o calidad visual) y pulsa **EVALUAR ANTES DE JUGAR**. JARVIS obtiene una única instantánea de batería, carga, estado térmico de Android y memoria mediante las APIs que ya existían. La política GameReadinessAdvisor produce sugerencias locales y transparentes: LISTO CON RESERVAS, PRECAUCIÓN, RECOMENDACIÓN: POSPONER o DATOS INSUFICIENTES.

- Estados térmicos críticos/emergencia/apagado próximo (Android 4–6) tienen prioridad y recomiendan posponer; estado alto (3), memoria baja reportada o batería inferior a 15% sin carga generan precaución.
- Si faltan señales básicas o son físicamente imposibles y no hay advertencias prioritarias, informa DATOS INSUFICIENTES. No inventa grados ni FPS.
- El perfil competitivo prioriza estabilidad de FPS en el consejo, no altera tasa de refresco. Calidad visual sugiere bajar efectos. Equilibrado prima batería/calidad.
- **No modifica** CPU, GPU, termales, RAM, red, configuración del sistema o GameHub; no lanza apps. La decisión final de abrir un juego continúa siendo una pulsación manual e independiente.
- No hay permisos nuevos, telemetría continua, almacenamiento, transmisiones ni acceso a otras apps. Las lecturas son de JARVIS y no equivalen a medir FPS de GameHub.

**Verificación:** Kotlin/JUnit evalúa lectura normal, umbrales extremos, prioridad crítica en cada perfil, memoria insuficiente y datos desconocidos; contratos Python impiden autoridad Android, servicios de red o lanzamiento silencioso. La generación de APK no sustituye ensayos térmicos o rendimiento físico. La clasificación SSS+ no está certificada.