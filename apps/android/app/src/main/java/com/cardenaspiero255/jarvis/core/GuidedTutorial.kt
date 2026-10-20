package com.cardenaspiero255.jarvis.core

/**
 * Offline, display-only walkthroughs inspired by the Tutorial Mode pattern
 * in tokenarc/open-jarvis (MIT). No upstream source copied and no executable
 * action is represented by these plain-text steps.
 */
enum class TutorialTopic { OFFLINE_VOICE, OWNER_VOICE, SHIZUKU, TERMUX, GAMEHUB }

data class GuidedStep(
    val title: String,
    val instruction: String,
    val expectedResult: String
)

data class GuidedTutorial(
    val title: String,
    val steps: List<GuidedStep>,
    val canExecuteDeviceActions: Boolean = false
)

object GuidedTutorialCatalog {
    fun get(topic: TutorialTopic): GuidedTutorial = when (topic) {
        TutorialTopic.OFFLINE_VOICE -> GuidedTutorial(
            "Voz local sin Internet",
            listOf(
                GuidedStep("Permisos de Android",
                    "Abre JARVIS y autoriza micrófono y notificaciones solo cuando Android los solicite. La voz local no requiere la app de Google.",
                    "El sistema permite iniciar una prueba explícita de voz."),
                GuidedStep("Diagnóstico de 4 segundos",
                    "En JARVIS ejecuta la prueba de micrófono de 4 segundos y habla cerca del teléfono.",
                    "La prueba muestra muestras capturadas, no una transcripción."),
                GuidedStep("Preparar Vosk",
                    "Selecciona MANOS LIBRES 60 S con el motor español local. El primer arranque puede preparar archivos del modelo; espera el estado ASR_NATIVE_READY.",
                    "ASR_NATIVE_READY confirma que el motor se ha preparado, pero no garantiza reconocimiento acústico."),
                GuidedStep("Probar escucha",
                    "Di exactamente «Jarvis, dime la hora». Si falla, comprueba el estado de ASR y el nivel de micrófono. No concedas permisos adicionales a otras apps.",
                    "Una respuesta de hora verifica esta ruta limitada; no demuestra conversación libre."),
                GuidedStep("Finalizar",
                    "Pulsa DETENER para liberar micrófono y servicio. Comprueba que desaparezca el estado de escucha.",
                    "El control de parada es explícito y el permiso de micrófono sigue gobernado por Android.")
            )
        )
        TutorialTopic.OWNER_VOICE -> GuidedTutorial(
            "Activación y voz del propietario",
            listOf(
                GuidedStep("Separar voz e identidad",
                    "Detectar la palabra Jarvis no demuestra identidad. La escucha por voz no autoriza acciones sensibles.",
                    "Wake word e identidad siguen siendo evidencias distintas."),
                GuidedStep("Inscripción consentida",
                    "Usa la pantalla de inscripción de voz de JARVIS solo si deseas activar la verificación local del propietario. No grabes voces ajenas sin consentimiento.",
                    "La inscripción no sustituye una prueba contra repeticiones o imitaciones."),
                GuidedStep("Verificar resultado",
                    "Comprueba el estado real de Voice Match en la app. Si indica no disponible o incierto, no autoriza privilegios ni acciones sensibles.",
                    "Se conserva el control por confirmación táctil independiente."),
                GuidedStep("Condiciones difíciles",
                    "Prueba posteriormente con ruido y otras voces de forma consentida. Sin ensayos físicos de falsos positivos y replay, no se certifica autenticación.",
                    "La identidad del hablante no se declara certificada por estas instrucciones.")
            )
        )
        TutorialTopic.SHIZUKU -> GuidedTutorial(
            "Conexión de Shizuku",
            listOf(
                GuidedStep("Iniciar el servicio",
                    "Abre Shizuku e inicia su servicio con su método oficial. Tener la aplicación instalada no demuestra que el servicio esté activo.",
                    "Shizuku informa que su servicio se encuentra ejecutándose."),
                GuidedStep("Revisar autorización",
                    "Regresa a JARVIS, abre CONEXIONES y comprueba el estado del permiso de SDK de Shizuku.",
                    "JARVIS distingue aplicación detectada, servicio iniciado y permiso concedido."),
                GuidedStep("Autorizar solo si decides",
                    "Si el estado indica permiso pendiente, pulsa AUTORIZAR JARVIS EN SHIZUKU y acepta el diálogo oficial de Android/Shizuku.",
                    "La autorización se otorga en la interfaz oficial. Esta guía no ejecuta comandos privilegiados."),
                GuidedStep("Uso seguro",
                    "Vuelve al centro de conexiones. Que la API esté autorizada no ejecuta rutinas; cada operación sensible requiere confirmación separada.",
                    "La guía nunca elude las políticas de Android ni modifica permisos.")
            )
        )
        TutorialTopic.TERMUX -> GuidedTutorial(
            "Termux y comandos controlados",
            listOf(
                GuidedStep("Instalar el compañero",
                    "Comprueba que Termux sea visible en JARVIS. La detección de la APK no implica que acepte comandos de otra aplicación.",
                    "JARVIS puede abrir Termux cuando Android lo permite."),
                GuidedStep("Permiso externo",
                    "Revisa la configuración oficial de Termux:RUN_COMMAND y la autorización necesaria en Android. No actives ejecución arbitraria de comandos.",
                    "El permiso es separado de instalar Termux."),
                GuidedStep("Prueba fija de lectura",
                    "En CONEXIONES, selecciona TERMUX: PROBAR COMANDO LOCAL id y confirma una vez. La guía no ejecuta este comando.",
                    "Solo la prueba fija id, autorizada por el usuario, devuelve una respuesta."),
                GuidedStep("Comprobar respuesta",
                    "Pulsa TERMUX: CONSULTAR RESULTADO. Diferencia VERIFIED, FAILED y TIMED_OUT antes de decidir si funciona.",
                    "Un estado verificado confirma la prueba id, no acceso irrestricto al terminal.")
            )
        )
        TutorialTopic.GAMEHUB -> GuidedTutorial(
            "GameHub Ultra desde JARVIS",
            listOf(
                GuidedStep("Comprobar instalación",
                    "Asegúrate de que GameHub Ultra esté instalado y que Android permita abrir su actividad.",
                    "JARVIS detecta una aplicación iniciable."),
                GuidedStep("Abrir sin cambiar ajustes",
                    "Pulsa GAMEHUB ULTRA desde la sección de juegos. Lanzar la app no modifica perfiles X4 ni rendimiento.",
                    "GameHub abre sin acciones automáticas sobre el hardware."),
                GuidedStep("Confirmar solicitudes de voz",
                    "Para una solicitud de apertura por voz, revisa la confirmación separada de JARVIS antes de ejecutar.",
                    "El reconocimiento de voz no concede permisos ni acceso autónomo a GameHub."),
                GuidedStep("Volver a JARVIS",
                    "Regresa a JARVIS y consulta la evaluación local de juego. Para cambiar X4 utiliza únicamente los controles explícitos de GameHub.",
                    "No hay una ejecución encadenada automática de cambios del sistema.")
            )
        )
    }
}

/** Pure per-screen state: no permissions, storage, microphone or device action. */
class GuidedTutorialSession(initialTopic: TutorialTopic) {
    var topic: TutorialTopic = initialTopic
        private set
    var position: Int = 0
        private set

    val guide: GuidedTutorial get() = GuidedTutorialCatalog.get(topic)
    val step: GuidedStep get() = guide.steps[position]

    fun next(): Boolean {
        if (position >= guide.steps.lastIndex) return false
        position++
        return true
    }

    fun previous(): Boolean {
        if (position <= 0) return false
        position--
        return true
    }

    fun select(newTopic: TutorialTopic) {
        topic = newTopic
        position = 0
    }

    fun reset() { position = 0 }

    fun progress(): String = "${position + 1} / ${guide.steps.size}"
}
