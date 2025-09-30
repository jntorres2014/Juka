package com.example.juka.data.encuesta

import com.google.firebase.Timestamp
import java.util.*

/**
 * Representa una pregunta individual de la encuesta
 */
data class Pregunta(
    val id: Int,
    val texto: String,
    val tipo: TipoPregunta,
    val opciones: List<String> = emptyList(), // Para preguntas de opción múltiple
    val esObligatoria: Boolean = true,
    val placeholder: String = "" // Para preguntas de texto libre
)

/**
 * Tipos de preguntas soportadas
 */
enum class TipoPregunta {
    TEXTO_LIBRE,        // EditText
    OPCION_MULTIPLE,    // RadioButton group
    SELECCION_MULTIPLE, // CheckBox group
    ESCALA,            // SeekBar o RadioButtons 1-5
    SI_NO              // Switch o RadioButtons Si/No
}

/**
 * Respuesta a una pregunta específica
 */
data class RespuestaPregunta(
    val preguntaId: Int,
    val respuestaTexto: String? = null,        // Para texto libre
    val opcionSeleccionada: String? = null,    // Para opción múltiple
    val opcionesSeleccionadas: List<String> = emptyList(), // Para selección múltiple
    val valorEscala: Int? = null,              // Para escalas 1-5
    val respuestaSiNo: Boolean? = null,        // Para preguntas Si/No
    val timestamp: Timestamp = Timestamp.now()
)

/**
 * Datos completos de la encuesta de un usuario
 */
data class EncuestaData(
    val userId: String,
    val respuestas: List<RespuestaPregunta>,
    val fechaInicio: Timestamp,
    val fechaCompletado: Timestamp? = null,
    val completada: Boolean = false,
    val progreso: Int = 0, // Porcentaje 0-100
    val dispositivo: String = android.os.Build.MODEL,
    val versionApp: String = "1.0.0"
)

/**
 * Estado de navegación de la encuesta (para el ViewModel)
 */
data class EstadoEncuesta(
    val preguntaActual: Int = 0,
    val respuestasTemporales: MutableMap<Int, RespuestaPregunta> = mutableMapOf(),
    val puedeAvanzar: Boolean = false,
    val puedeRetroceder: Boolean = false,
    val estaCompleta: Boolean = false,
    val progresoPorcentaje: Int = 0
)

/**
 * Resultado de validación de una respuesta
 */
data class ValidacionRespuesta(
    val esValida: Boolean,
    val mensajeError: String? = null
)

/**
 * Constantes con las preguntas de la encuesta
 */
object PreguntasEncuesta {

    val PREGUNTAS = listOf(
        Pregunta(
            id = 1,
            texto = "¿Cuál es tu nivel de experiencia en pesca?",
            tipo = TipoPregunta.OPCION_MULTIPLE,
            opciones = listOf(
                "Principiante (menos de 1 año)",
                "Intermedio (1-5 años)",
                "Avanzado (5-10 años)",
                "Experto (más de 10 años)"
            )
        ),

        Pregunta(
            id = 2,
            texto = "¿Con qué frecuencia pescas?",
            tipo = TipoPregunta.OPCION_MULTIPLE,
            opciones = listOf(
                "Varias veces por semana",
                "Una vez por semana",
                "Algunas veces al mes",
                "Solo en vacaciones",
                "Muy raramente"
            )
        ),

        Pregunta(
            id = 3,
            texto = "¿Qué modalidades de pesca practicas? (puedes seleccionar varias)",
            tipo = TipoPregunta.SELECCION_MULTIPLE,
            opciones = listOf(
                "Pesca desde costa",
                "Pesca embarcada",
                "Pesca en ríos",
                "Pesca en lagos",
                "Pesca deportiva",
                "Pesca submarina"
            )
        ),

        Pregunta(
            id = 4,
            texto = "¿En qué provincias o regiones pescas habitualmente?",
            tipo = TipoPregunta.SELECCION_MULTIPLE,

            placeholder = "Ej: Buenos Aires, Chubut, Patagonia..."
        ),

        Pregunta(
            id = 5,
            texto = "¿Qué tan importante es para ti registrar tus jornadas de pesca?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Nada importante", "2", "3", "4", "5 - Muy importante")
        ),

        Pregunta(
            id = 6,
            texto = "¿Compartes tus experiencias de pesca en redes sociales?",
            tipo = TipoPregunta.SI_NO
        ),

        Pregunta(
            id = 7,
            texto = "¿Qué especies te interesan más capturar?",
            tipo = TipoPregunta.TEXTO_LIBRE,
            placeholder = "Ej: Salmón, pejerrey, trucha, dorado..."
        ),

        Pregunta(
            id = 8,
            texto = "¿Qué características te gustaría que tuviera una app de pesca? (selecciona las que te interesen)",
            tipo = TipoPregunta.SELECCION_MULTIPLE,
            opciones = listOf(
                "Registro automático de capturas",
                "Identificación de especies por foto",
                "Compartir reportes con amigos",
                "Estadísticas personales",
                "Mapas de lugares de pesca",
                "Pronóstico del tiempo",
                "Consejos y técnicas",
                "Comunidad de pescadores"
            )
        ),

        Pregunta(
            id = 9,
            texto = "¿Cómo calificarías tu interés en la tecnología aplicada a la pesca?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Poco interés", "2", "3", "4", "5 - Muy interesado")
        ),

        Pregunta(
            id = 10,
            texto = "¿Hay algo específico que te gustaría que mejoremos en esta aplicación?",
            tipo = TipoPregunta.SELECCION_MULTIPLE,
            placeholder = "Comparte tus ideas, sugerencias o comentarios...",
            esObligatoria = false
        )
    )

    fun obtenerPreguntaPorId(id: Int): Pregunta? {
        return PREGUNTAS.find { it.id == id }
    }

    fun obtenerTotalPreguntas(): Int = PREGUNTAS.size

    fun calcularProgreso(preguntaActual: Int): Int {
        return ((preguntaActual.toFloat() / obtenerTotalPreguntas()) * 100).toInt()
    }
}

/**
 * Utilidades para validar respuestas
 */
object ValidadorEncuesta {

    fun validarRespuesta(pregunta: Pregunta, respuesta: RespuestaPregunta): ValidacionRespuesta {
        if (!pregunta.esObligatoria) {
            return ValidacionRespuesta(true)
        }

        return when (pregunta.tipo) {
            TipoPregunta.TEXTO_LIBRE -> {
                if (respuesta.respuestaTexto.isNullOrBlank()) {
                    ValidacionRespuesta(false, "Este campo es obligatorio")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.OPCION_MULTIPLE -> {
                if (respuesta.opcionSeleccionada.isNullOrBlank()) {
                    ValidacionRespuesta(false, "Debes seleccionar una opción")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.SELECCION_MULTIPLE -> {
                if (respuesta.opcionesSeleccionadas.isEmpty()) {
                    ValidacionRespuesta(false, "Debes seleccionar al menos una opción")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.ESCALA -> {
                if (respuesta.valorEscala == null || respuesta.valorEscala !in 1..5) {
                    ValidacionRespuesta(false, "Debes seleccionar un valor entre 1 y 5")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.SI_NO -> {
                if (respuesta.respuestaSiNo == null) {
                    ValidacionRespuesta(false, "Debes seleccionar Sí o No")
                } else {
                    ValidacionRespuesta(true)
                }
            }
        }
    }

    fun validarEncuestaCompleta(respuestas: List<RespuestaPregunta>): ValidacionRespuesta {
        val preguntasObligatorias = PreguntasEncuesta.PREGUNTAS.filter { it.esObligatoria }
        val respuestasObligatorias = respuestas.filter { respuesta ->
            preguntasObligatorias.any { it.id == respuesta.preguntaId }
        }

        if (respuestasObligatorias.size < preguntasObligatorias.size) {
            val faltantes = preguntasObligatorias.size - respuestasObligatorias.size
            return ValidacionRespuesta(
                false,
                "Faltan $faltantes preguntas obligatorias por responder"
            )
        }

        // Validar cada respuesta individual
        preguntasObligatorias.forEach { pregunta ->
            val respuesta = respuestas.find { it.preguntaId == pregunta.id }
            if (respuesta != null) {
                val validacion = validarRespuesta(pregunta, respuesta)
                if (!validacion.esValida) {
                    return ValidacionRespuesta(
                        false,
                        "Error en pregunta ${pregunta.id}: ${validacion.mensajeError}"
                    )
                }
            }
        }

        return ValidacionRespuesta(true)
    }
}