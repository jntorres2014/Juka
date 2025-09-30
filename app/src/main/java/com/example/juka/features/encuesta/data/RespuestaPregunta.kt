package com.example.juka.features.encuesta.data

import com.google.firebase.Timestamp

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