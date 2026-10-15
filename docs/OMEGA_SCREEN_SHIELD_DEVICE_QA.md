# Omega Privacy — QA instrumentada de la pantalla segura

El paquete de instrumentación de Android incluye ScreenShieldInstrumentedTest y PrivateConversationStoreInstrumentedTest. **Se compilan, pero no se han ejecutado todavía en un dispositivo/emulador** dentro de esta integración. La CI normal crea el APK de pruebas y realiza JUnit local, no ejecuta AndroidJUnit4 en un hardware real.

## Ejecución en Android real o emulador

1. Conecta un dispositivo Android de pruebas mediante ADB autorizado o inicia un emulador compatible.
2. Desde la raíz de apps/android, ejecuta: `./gradlew :app:connectedDebugAndroidTest --tests` NO está admitido para instrumentation; el comando correcto es `./gradlew :app:connectedDebugAndroidTest`.
3. Comprueba `ScreenShieldInstrumentedTest`: al abrir JARVIS, FLAG_SECURE activo; al pulsar PERMITIR CAPTURAS 60 S — DEPURACIÓN, desactivado; PROTEGER PANTALLA AHORA o BLOQUEAR SESIÓN deben reactivarlo.
4. Comprueba manualmente el selector de aplicaciones recientes y una captura de pantalla del dispositivo protegido; durante la excepción voluntaria revisa la posible exposición de chats y diagnósticos. La prueba automatizada de bit de ventana no demuestra por sí sola el comportamiento visual en todos los fabricantes.
5. Prueba la expiración real a los 60 segundos, el paso a segundo plano, Android 14+, rotación, abrir el reconocedor de voz, el selector de imágenes y el cierre forzado. Registra versión de sistema, modelo y resultados.

## Privacidad

Los tests solo inspeccionan el bit de WindowManager y pulsan controles visibles. No guardan screenshots, chat, tokens, audio ni datos privados, y no invocan funciones de red. El último test utiliza el botón PRIVACIDAD que también detiene Hands si existe: **ejecutar en entorno de pruebas controlado**.

## Evidencia y límites

Estado a la creación de PR #38: **pruebas físicas no ejecutadas**. El build Android y los contratos Python de CI no validan recents, exclusión de MediaProjection, fugas visuales OEM ni un móvil real. Estos puntos continúan siendo gates de release de JARVIS y del PR #27.