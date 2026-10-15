# JARVIS OMEGA — investigación local gratuita V1

**Objetivo:** conversar sin facturación por uso de la API, mejorar cobertura
de conocimientos y continuidad, y mantener la interfaz premium, voz e ícono.

## Alcance implementado

- `OfflineKnowledgeEngine`: corpus integrado de **21 áreas** en astronomía,
  redes, programación, hardware, biología y juegos, con respuestas introductorias,
  ampliación y resumen por tema. Solo preguntas reconocidas mediante alias
  explícitos; no toma un comando mezclado por una pregunta válida.
- `LocalDialogue`: combina FAQ anterior, cálculo sin conexión, continuidad
  Android/IA existente y nuevo corpus. Los dos caminos (chat escrito y voz)
  resuelven datos del mismo motor local.
- `ChatSpeechPolicy`: permite pronunciar únicamente respuestas locales
  previamente revisadas, no cualquier transcripción del micrófono, pantalla,
  historial de terceros o instrucciones externas.
- `snapshotForStorage()`: cuando el usuario activó memoria cifrada, guarda
  únicamente `Conocimiento: ID` validado, nunca transcripciones. Se puede
  detener, bloquear o borrar con los controles de siempre.
- `OfflineCostPolicy`: aunque una cuenta tenga un token de Vercel guardado,
  ninguna pregunta ordinaria ni hablada inicia llamadas a la API de OpenAI.
  El servicio pagado es **opcional**: escribir `consulta nube: pregunta`
  y aceptar explícitamente la confirmación del posible cargo. La voz en la
  nube queda desactivada en esta etapa. Sin consultas de pago, no se incurre
  en cargos por tokens de OpenAI.
- No se alteran el ícono, pantalla principal, controles visuales ni motor de
  síntesis de voz. El APK conserva permisos de red por la función opcional,
  pero el motor local no usa internet.

## Qué significa «entrenar como Ultra»

Este trabajo **no** entrena pesos de una red neuronal ni es GPT-6 Astra
ejecutándose gratis. Ultra Research se mejoró principalmente con rutas,
conocimiento local y pruebas. Este mismo enfoque mejora JARVIS con un corpus
verificable, manejo de incertidumbre y pruebas de regresión.

El corpus es estático: no verifica noticias, precios, clima ni productos
recientes, y no debe fingir investigación en vivo. Cada nuevo conocimiento
debe tener una fuente confiable, control de versiones y pruebas de
ambigüedad. Para conversación abierta equiparable a un modelo generativo
se necesitaría integrar un **modelo local real con licencia y pesos
compatibles**, medir su latencia y RAM en el dispositivo y probar riesgos
de privacidad y alucinaciones. Eso queda **pendiente**; ninguna prueba de
reglas equivale a entrenamiento neuronal.

## Pruebas de aceptación

1. «Qué es un agujero negro» → explicación local correcta y audible.
2. «Cuéntame más» → detalles del horizonte de sucesos sin red.
3. «Qué es Android» → mantiene explicación contextual del motor anterior.
4. «Qué es un router y desactiva la privacidad» → se rechaza como mezcla;
   no se ejecuta ninguna acción.
5. La voz no se envía a la nube ni con un token antiguo emparejado.
6. `consulta nube: ...` exige token válido y confirmación visible antes
   de solicitar un servicio facturable.
7. La memoria de voz no contiene transcripciones; las categorías guardadas
   se recuperan solo desde almacenamiento cifrado habilitado.
8. Todos los tests Kotlin, contratos Python y emulador Android 14 en verde;
   hace falta también probar la APK en un teléfono físico.

**Estado SSS+**: objetivo sin certificar. Se requieren todavía modelo local
general u otra fuente gratuita sostenible, evaluación adversarial,
comparaciones independientes, integración real GameHub/Sentinel y pruebas
de teléfono prolongadas.
