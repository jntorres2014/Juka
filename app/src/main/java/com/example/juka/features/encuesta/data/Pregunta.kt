package com.example.juka.features.encuesta.data

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