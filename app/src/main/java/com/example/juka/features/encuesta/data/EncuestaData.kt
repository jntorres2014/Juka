package com.example.juka.features.encuesta.data

import com.google.firebase.Timestamp

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