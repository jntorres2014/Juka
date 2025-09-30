package com.example.juka.features.encuesta.data

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