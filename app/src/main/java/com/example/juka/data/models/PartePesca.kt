package com.example.juka.data.models

import com.google.firebase.Timestamp

data class PartePesca @JvmOverloads constructor(
    val id: String = "",
    val userId: String = "",
    val fecha: String = "",
    val horaInicio: String? = null,
    val horaFin: String? = null,
    val duracionHoras: String? = null,
    val peces: List<PezCapturado> = emptyList(),
    val cantidadTotal: Int = 0,
    val tipo: String? = null,
    val canas: Int? = null,
    val ubicacion: UbicacionPesca? = null,
    val fotos: List<String> = emptyList(),
    val transcripcionOriginal: String? = null,
    val userInfo: Map<String, Any> = emptyMap(),
    val timestamp: Timestamp? = null,
    val estado: String = "completado"
)
