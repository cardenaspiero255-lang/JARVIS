package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Small, curated offline knowledge engine, inspired by Ultra Research's local
 * fallback. These are vetted static facts, NOT learned neural-model weights
 * and not live web research. Exact, bounded intent matching denies compound
 * commands, untrusted transcripts, invented live news and device actions.
 *
 * Session state contains only one topic enum; no microphone transcript.
 */
class OfflineKnowledgeEngine {
    private data class Topic(
        val id: String,
        val aliases: Set<String>,
        val intro: String,
        val detail: String,
        val summary: String
    )

    private var current: Topic? = null

    fun answer(request: String?): String? {
        val phrase = normalize(request) ?: return null
        when (phrase) {
            "cuentame mas", "explicame mas", "explicalo mejor",
            "y como funciona", "como funciona eso", "amplia el tema" ->
                return current?.detail
            "de que estabamos hablando", "de que hablabamos",
            "resumelo", "hazme un resumen" -> return current?.summary
            "olvida el tema", "cambiemos de tema" -> {
                if (current == null) return null
                current = null
                return TOPIC_CLEARED
            }
        }
        val selected = BY_ALIAS[phrase] ?: return null
        current = selected
        return selected.intro
    }

    /** A recognized FAQ or another knowledge domain switches off old context. */
    fun clear() { current = null }

    fun recognizesNewTopic(request: String?): Boolean =
        normalize(request)?.let { it in BY_ALIAS } == true

    /** Restore context only when a previously typed question matches a vetted alias. */
    fun restoreTopicHint(typedQuestion: String) {
        val phrase = normalize(typedQuestion) ?: return
        BY_ALIAS[phrase]?.let { current = it }
    }

    /** Trusted topic identifier only, never a user message or voice transcript. */
    fun contextToken(): String? = current?.id

    /** No arbitrary topic names or previous answers can be restored. */
    fun restoreContextToken(token: String) {
        current = if (token.length <= 32 && Regex("""[A-Z_]+""").matches(token))
            BY_ID[token] else null
    }

    private fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 160 ||
            raw.any { Character.isISOControl(it) }) return null
        val ascii = Normalizer.normalize(raw.lowercase(Locale.ROOT).trim(),
            Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[¿?¡!.,:;]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        return ascii.takeIf { Regex("""[a-z0-9 ]+""").matches(it) }
    }

    companion object {
        const val TOPIC_CLEARED =
            "Dejé de usar ese tema para continuar. No borré el historial escrito."

        private val TOPICS: List<Topic> = listOf(
            Topic("BLACK_HOLE",
                setOf("que es un agujero negro", "que son los agujeros negros",
                    "explicame un agujero negro", "explica los agujeros negros"),
                "Un agujero negro es una región del espacio cuya gravedad impide que la luz escape una vez cruza su horizonte de sucesos.",
                "El horizonte de sucesos es su límite de no retorno. La materia puede calentarse antes de caer y producir radiación; eso no significa que la luz escape desde dentro.",
                "Estábamos hablando de los agujeros negros y su horizonte de sucesos."),
            Topic("NEUTRON_STAR",
                setOf("que es una estrella de neutrones", "que son las estrellas de neutrones"),
                "Una estrella de neutrones es un objeto muy compacto que puede quedar tras el colapso del núcleo de una estrella masiva.",
                "Su gravedad comprime la materia de forma extrema. Algunas giran con rapidez y emiten haces de radiación; cuando estos pasan ante nosotros, las llamamos púlsares.",
                "Estábamos hablando de las estrellas de neutrones."),
            Topic("EXOPLANET",
                setOf("que es un exoplaneta", "que son los exoplanetas", "explicame los exoplanetas"),
                "Un exoplaneta es un planeta que orbita una estrella distinta del Sol. Se detectan, entre otros métodos, por tránsitos y por cambios en la velocidad de su estrella.",
                "En un tránsito, el planeta atenúa un poco la luz de su estrella. Detectarlo no demuestra que sea habitable o que tenga vida.",
                "Estábamos hablando de los exoplanetas y cómo se detectan."),
            Topic("GALAXY",
                setOf("que es una galaxia", "que son las galaxias"),
                "Una galaxia reúne estrellas, gas, polvo y materia oscura ligados por gravedad. La Vía Láctea es la galaxia donde se encuentra el sistema solar.",
                "Las galaxias pueden ser espirales, elípticas o irregulares. Su tamaño y forma cambian por formación estelar y por interacciones con otras galaxias.",
                "Estábamos hablando de las galaxias y la Vía Láctea."),
            Topic("SOLAR_SYSTEM",
                setOf("que es el sistema solar", "como es el sistema solar"),
                "El sistema solar contiene el Sol, ocho planetas, lunas y numerosos cuerpos menores que orbitan alrededor de nuestra estrella.",
                "Los cuatro planetas interiores son rocosos. Júpiter y Saturno son gigantes gaseosos; Urano y Neptuno se consideran gigantes helados.",
                "Estábamos hablando del sistema solar y sus planetas."),
            Topic("WIFI",
                setOf("como funciona el wifi", "que es wifi", "que es el wifi"),
                "Wi-Fi es una tecnología de red inalámbrica que utiliza ondas de radio para conectar dispositivos a una red local.",
                "El Wi-Fi conecta el teléfono con el router, pero no garantiza internet: también depende del proveedor, la congestión y la señal disponible.",
                "Estábamos hablando de Wi-Fi y redes inalámbricas."),
            Topic("ROUTER",
                setOf("que es un router", "para que sirve un router", "como funciona un router"),
                "Un router dirige el tráfico entre redes, por ejemplo entre los dispositivos de tu red local e internet.",
                "El router decide a dónde enviar paquetes de tráfico. Una conexión por cable puede evitar parte de la interferencia del Wi-Fi, pero no mejora por sí sola el proveedor.",
                "Estábamos hablando de los routers y el tráfico de red."),
            Topic("INTERNET",
                setOf("que es internet", "como funciona internet"),
                "Internet es una red mundial de redes que intercambian datos mediante protocolos como IP, TCP y UDP.",
                "Al abrir una página, el dispositivo resuelve su dominio y establece conexiones para recibir datos. La velocidad y latencia dependen de todo el recorrido.",
                "Estábamos hablando de internet y sus protocolos."),
            Topic("RAM",
                setOf("que es la ram", "para que sirve la ram", "que es memoria ram"),
                "La RAM es memoria temporal de acceso rápido que utiliza el sistema para mantener aplicaciones y datos mientras están en uso.",
                "Tener más RAM permite mantener más tareas disponibles, pero no vuelve automáticamente más rápido al procesador. Los datos normales de RAM se pierden al apagar.",
                "Estábamos hablando de la memoria RAM."),
            Topic("GPU",
                setOf("que es una gpu", "para que sirve una gpu", "que es una tarjeta grafica"),
                "La GPU es un procesador especializado en operaciones paralelas, especialmente gráficos y otros cálculos que pueden dividirse en muchas tareas.",
                "En juegos, la GPU influye en el renderizado, pero los fotogramas también dependen de la CPU, la memoria, la temperatura y la configuración gráfica.",
                "Estábamos hablando de la GPU y los gráficos de los juegos."),
            Topic("CPU",
                setOf("que es una cpu", "que es un procesador", "para que sirve el procesador"),
                "La CPU ejecuta instrucciones de programas y coordina numerosos cálculos y tareas del sistema.",
                "La frecuencia no es la única medida de rendimiento. También importan la arquitectura, los núcleos, la caché, la memoria y el consumo energético.",
                "Estábamos hablando de la CPU y cómo procesa instrucciones."),
            Topic("PYTHON",
                setOf("que es python", "para que sirve python", "que es el lenguaje python"),
                "Python es un lenguaje de programación usado en automatización, análisis de datos, desarrollo y aprendizaje automático.",
                "Python permite escribir código legible. Se pueden organizar programas en funciones y módulos, instalar bibliotecas y comprobar resultados con pruebas.",
                "Estábamos hablando de Python y programación."),
            Topic("LINUX",
                setOf("que es linux", "para que sirve linux"),
                "Linux es un núcleo de sistema operativo. Distribuciones como Ubuntu o Linux Mint lo combinan con herramientas y aplicaciones.",
                "Linux se utiliza en servidores, teléfonos Android y computadoras. Sus permisos y procesos aíslan tareas, pero también necesita actualizaciones.",
                "Estábamos hablando de Linux y sus distribuciones."),
            Topic("MACHINE_LEARNING",
                setOf("que es aprendizaje automatico", "que es machine learning",
                    "como funciona el machine learning"),
                "El aprendizaje automático ajusta modelos a partir de ejemplos para reconocer patrones y hacer predicciones sobre datos nuevos.",
                "Para evaluar un modelo se separan datos de entrenamiento y de prueba. Muchas pruebas repetidas no equivalen por sí solas a entrenar un modelo.",
                "Estábamos hablando de aprendizaje automático y evaluación de modelos."),
            Topic("LANGUAGE_MODEL",
                setOf("que es un modelo de lenguaje", "que es un modelo conversacional",
                    "que es un modelo de ia"),
                "Un modelo de lenguaje predice y genera texto usando patrones aprendidos durante el entrenamiento. Puede ser útil y también equivocarse.",
                "Un modelo local puede responder sin una API, pero requiere archivos de pesos compatibles, memoria y un motor de inferencia; ampliar respuestas fijas no lo entrena.",
                "Estábamos hablando de modelos de lenguaje y sus límites."),
            Topic("ENCRYPTION",
                setOf("que es cifrado", "que es el cifrado", "que significa cifrar"),
                "Cifrar transforma datos para que solo quienes tienen la clave adecuada puedan recuperar su contenido.",
                "Un cifrado autenticado como AES-GCM también permite detectar ciertas modificaciones. No sustituye el control de acceso ni protege claves publicadas.",
                "Estábamos hablando del cifrado y la protección de datos."),
            Topic("API",
                setOf("que es una api", "para que sirve una api", "que significa api"),
                "Una API es una interfaz que permite a programas comunicarse mediante operaciones y formatos definidos.",
                "Una API puede requerir autenticación y cobrar por uso. Nunca conviene incrustar claves privadas de servicios de pago en una APK distribuida.",
                "Estábamos hablando de las API y la comunicación entre programas."),
            Topic("PHOTOSYNTHESIS",
                setOf("que es la fotosintesis", "como funciona la fotosintesis"),
                "La fotosíntesis permite a plantas, algas y ciertos microorganismos convertir energía luminosa en energía química.",
                "En plantas, la luz impulsa reacciones que utilizan agua y dióxido de carbono para producir compuestos orgánicos y liberar oxígeno.",
                "Estábamos hablando de la fotosíntesis."),
            Topic("ELECTRICITY",
                setOf("como funciona la electricidad", "que es la electricidad"),
                "La electricidad describe fenómenos asociados a cargas eléctricas. La corriente es el movimiento ordenado de cargas en un material.",
                "El voltaje impulsa la corriente y la resistencia se opone a su paso. Trabajar con instalaciones eléctricas exige medidas de seguridad.",
                "Estábamos hablando de la electricidad y los circuitos."),
            Topic("FPS",
                setOf("que son los fps", "que significa fps en juegos",
                    "que son los fotogramas por segundo"),
                "En videojuegos, FPS significa fotogramas por segundo: cuántas imágenes produce el juego cada segundo.",
                "Un valor alto de FPS no garantiza fluidez. También importan la regularidad entre fotogramas, la frecuencia de pantalla y la latencia.",
                "Estábamos hablando de los FPS y la fluidez de los juegos."),
            Topic("CLOUD",
                setOf("que es la nube", "que es computacion en la nube",
                    "como funciona la nube"),
                "La computación en la nube permite utilizar servidores remotos para almacenar datos o ejecutar servicios a través de internet.",
                "La nube ofrece recursos bajo demanda, pero depende de conectividad, políticas de privacidad y costos del proveedor.",
                "Estábamos hablando de la computación en la nube.")
        )

        private val BY_ALIAS: Map<String, Topic> =
            TOPICS.flatMap { topic -> topic.aliases.map { alias -> alias to topic } }.toMap()

        private val BY_ID: Map<String, Topic> = TOPICS.associateBy { it.id }

        /** Only fixed, reviewed knowledge strings may be spoken by local TTS. */
        val SAFE_SPOKEN_REPLIES: Set<String> =
            TOPICS.flatMap { listOf(it.intro, it.detail, it.summary) }.toSet() +
                TOPIC_CLEARED
    }
}
