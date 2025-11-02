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
    val opciones: List<String> = emptyList(),
    val esObligatoria: Boolean = true,
    val placeholder: String = "",
    val rangoNumero: IntRange = 0..150, // Para preguntas numéricas (ej: edad 0-150)
    val rangoEscala: IntRange = 1..5    // Para escalas (1-5 o 1-7)
)

/**
 * Tipos de preguntas soportadas
 */
enum class TipoPregunta {
    TEXTO_LIBRE,        // EditText
    NUMERO,             // EditText numérico (para edad)
    FECHA,              // DatePicker
    OPCION_MULTIPLE,    // RadioButton group
    SELECCION_MULTIPLE, // CheckBox group
    ESCALA,             // Slider 1-5 o 1-7
    SI_NO               // Botones Si/No
}

/**
 * Respuesta a una pregunta específica
 */
data class RespuestaPregunta(
    val preguntaId: Int,
    val respuestaTexto: String? = null,
    val respuestaNumero: Int? = null,          // Para números (edad)
    val respuestaFecha: String? = null,        // Formato "dd/MM/yyyy"
    val opcionSeleccionada: String? = null,
    val opcionesSeleccionadas: List<String> = emptyList(),
    val valorEscala: Int? = null,
    val respuestaSiNo: Boolean? = null,
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
    val progreso: Int = 0,
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
            texto = "¿En que año nació?",
            tipo = TipoPregunta.NUMERO,
            placeholder = "Selecciona tu año de nacimiento",
            rangoNumero = 1888..2025
        ),

        Pregunta(
            id = 2,
            texto = "Género",
            tipo = TipoPregunta.OPCION_MULTIPLE,
            opciones = listOf(
                "Masculino",
                "Femenino",
                "No binario",
                "Prefiero no contestar"
            )
        ),

        Pregunta(
            id = 3,
            texto = "¿Desde qué edad pesca?",
            tipo = TipoPregunta.NUMERO,
            placeholder = " ",
            rangoNumero = 0..150
        ),

        Pregunta(
            id = 4,
            texto = "¿Es socio/a de algún club de pesca recreativa?",
            tipo = TipoPregunta.SI_NO
        ),

        Pregunta(
            id = 5,
            texto = "¿Participó alguna vez en concurso de pesca?",
            tipo = TipoPregunta.SI_NO
        ),

        Pregunta(
            id = 6,
            texto = "¿Cómo calificaría su habilidad como pescador/a comparándose con otras personas que practican pesca recreativa en los sitios que usted frecuenta?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Mucho peor", "2", "3", "4", "5", "6", "7 - Mucho mejor"),
            rangoEscala = 1..7
        ),

        Pregunta(
            id = 7,
            texto = "¿Cuán altas son sus expectativas de capturar una pieza trofeo (de gran porte o especie en particular), durante una salida de pesca?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Nada altas", "2", "3", "4", "5", "6", "7 - Muy altas"),
            rangoEscala = 1..7
        ),

        Pregunta(
            id = 8,
            texto = "¿Cuán altas son sus expectativas de capturar un gran número de peces durante una salida de pesca?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Nada altas", "2", "3", "4", "5", "6", "7 - Muy altas"),
            rangoEscala = 1..7
        ),

        Pregunta(
            id = 9,
            texto = "¿Cuán importante es para usted el consumo de las capturas de la pesca recreativa?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Nada importante", "2", "3", "4", "5", "6", "7 - Muy importante"),
            rangoEscala = 1..7
        ),

        Pregunta(
            id = 10,
            texto = "¿La mayoría de sus amistades están vinculadas de alguna manera con la pesca recreativa?",
            tipo = TipoPregunta.SI_NO
        ),

        Pregunta(
            id = 11,
            texto = "¿Otras personas dirían que usted pasa mucho tiempo pescando?",
            tipo = TipoPregunta.SI_NO
        ),

        Pregunta(
            id = 12,
            texto = "¿La pesca es su actividad recreativa favorita?",
            tipo = TipoPregunta.SI_NO
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

            TipoPregunta.NUMERO -> {
                if (respuesta.respuestaNumero == null) {
                    ValidacionRespuesta(false, "Debes ingresar un número")
                } else if (respuesta.respuestaNumero !in pregunta.rangoNumero) {
                    ValidacionRespuesta(false, "El número debe estar entre ${pregunta.rangoNumero.first} y ${pregunta.rangoNumero.last}")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.FECHA -> {
                if (respuesta.respuestaFecha.isNullOrBlank()) {
                    ValidacionRespuesta(false, "Debes seleccionar una fecha")
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
                if (respuesta.valorEscala == null) {
                    ValidacionRespuesta(false, "Debes seleccionar un valor")
                } else if (respuesta.valorEscala !in pregunta.rangoEscala) {
                    ValidacionRespuesta(false, "El valor debe estar entre ${pregunta.rangoEscala.first} y ${pregunta.rangoEscala.last}")
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