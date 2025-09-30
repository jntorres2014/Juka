package com.example.juka.features.encuesta.data

/**
 * Resultado de validación de una respuesta
 */
data class ValidacionRespuesta(
    val esValida: Boolean,
    val mensajeError: String? = null
)