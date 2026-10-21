# JARVIS — inventario de componentes de otros proyectos y primera adaptación

## Verificación de REA

**REA NO EJECUTADO**. Se verificó el proyecto oficial `morluto/rea`
(https://github.com/morluto/rea), pero esta sesión no tiene un conector
REA/servidor MCP registrado, la versión local de Node es 22.16.0
(inferior a 22.19+ requerida) y npm no pudo resolver
`registry.npmjs.org` (`EAI_AGAIN`). Además no hay dispositivo remoto
autorizado conectado. Por tanto NO se ha usado JADX, Ghidra ni
descompilación de APK ajena. **La evidencia es lectura del código fuente
público** por GitHub; jamás atribuir funciones, comportamiento en teléfonos
ni rutas de ejecución verificadas a una supuesta auditoría binaria.

El flujo deseable cuando REA esté disponible en un host compatible:
auditar APK y código distribuido de una versión concreta, registrar
sus firmas, localizar las clases Kotlin/JNI y el flujo de llamadas,
comprobar versión/licencia, recrear lo pertinente en una rama JARVIS y
comparar ejecución original y adaptación con pruebas. No usar REA
para copiar código cerrado ni afirmar que la inferencia está certificada.

## Inventario de aplicaciones JARVIS externas

| Original | Licencia inspeccionada | Componente identificado | Decisión |
| --- | --- | --- | --- |
| https://github.com/alwin123098/jarvis-android | MIT, copyright Codotype 2026, LICENSE | `rag/KnowledgeBase.kt`: índice BM25 de pasajes sin embeddings ni red; modelo de catálogo GGUF; downloads reanudables | **Primera adaptación**: BM25 español local, notas TXT desde Android |
| https://github.com/judejulius/jarvis | MIT, copyright Mohammad Joud Julius 2026, LICENSE | Wake local, interrupción/barge-in, continuación conversacional, modelos de respaldo, accesibilidad | **Solo investigación**: revisar interrupción y buffer/eco antes de reutilizar; nunca copiar control irrestricto |
| https://github.com/Bwarhness/jarvis-assistant | Apache-2.0, LICENSE | `VoiceInteractionService`, asistente del sistema; separación de cliente, voz y wake word | **Solo investigación**: requiere revisar restricciones Android 14/Funtouch, permisos y confirmación explícita |

Los paquetes no incorporados conservan su estado **NO IMPLEMENTADO**;
sus características en README son declaraciones de autores, no
mediciones propias. Tampoco se importan modelos con licencias separadas.

## Primera adaptación: notas TXT privadas sin Internet

Origen: `alwin123098/jarvis-android`, archivo
`app/src/main/java/com/codotype/jarvis/rag/KnowledgeBase.kt`.
Se estudió su división en pasajes, frecuencias documentales y BM25
(k1=1.5, b=0.75), y se creó una versión para JARVIS en
`core/PrivateKnowledgePack.kt`. Se conserva su licencia MIT en
`apps/android/app/src/main/assets/legal/CODOTYPE_JARVIS_MIT_NOTICE.txt`.

**Cómo usarlo en el Vivo V25 Pro 5G:**

1. En JARVIS, sección OMEGA CHAT, tocar
   **IMPORTAR NOTA LOCAL · TXT**. Solo elegir manualmente
   un archivo `.txt` de hasta 64 KiB con el selector de Android.
2. El contenido se valida como UTF-8, sin caracteres de control
   inapropiados, se fragmenta e indexa en memoria. No se pide
   `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`,
   permisos de red, acceso persistente a `content://` ni
   descargas automáticas.
3. Escribir en el chat, por ejemplo,
   `busca en mis notas configuración del router`
   o `consulta mis notas shizuku`.
4. JARVIS devuelve un **fragmento literal** marcado
   `Dato de una nota local, no verificado:`.
   **No es un hecho verificado**, no se usa como comando,
   no concede permisos ni desencadena acciones Android.
5. **BLOQUEAR SESIÓN — PRIVACIDAD** y salir de JARVIS
   olvidan el índice en memoria. La lectura y los callbacks
   comprueban la sesión para rechazar operaciones anteriores.

Privacidad: el *archivo completo* no se copia a almacenamiento interno
ni se sube a servidores; sus fragmentos sí pueden aparecer en la
pantalla y en el historial escrito. Si el usuario habilitó
explícitamente **memoria cifrada**, un fragmento que aparezca
como respuesta de chat puede guardarse en esa memoria AES-GCM.
Esto no se oculta: para evitarlo, mantener la memoria desactivada
o usar el botón de eliminación/revocación.

La consulta de notas se permite **solo por chat escrito explícito**.
No existe inyección de contexto importado en el modelo GGUF, ni
lectura TTS de documentos arbitrarios, wake word para lanzar importación
o entrenamiento automático de una inteligencia. La base de hechos
curados de JARVIS conserva prioridad salvo consultas dirigidas
expresamente a las notas.

## Próximos componentes (sin copiar antes de pruebas)

- **Interrupción / barge-in** desde `judejulius/jarvis`: mapear el
  tratamiento del eco de TTS, interrupción y reanudación; evaluar
  pantalla apagada, consumo y cancelación nativa en Android 14.
- **Asistente digital predeterminado** desde
  `Bwarhness/jarvis-assistant`: comprobar interfaz Android,
  permisos, políticas de privacidad y compatibilidad Vivo antes
  de alterar `AndroidManifest.xml`.
- **Descargas de GGUF reanudables** desde
  `alwin123098/jarvis-android`: adoptar solo si se puede
  garantizar procedencia, SHA-256, privacidad y cero descargas
  involuntarias en el teléfono.

**Criterio de entrada al producto:** derechos de reutilización,
interfaz compatible, prueba RED→GREEN, verificación de todos los
gates y ningún privilegio nuevo sin consentimiento. Una función
que solo aparece en un README ajeno no se da por integrada.
