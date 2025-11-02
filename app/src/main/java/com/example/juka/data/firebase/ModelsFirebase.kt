package com.example.juka.data.firebase


import com.google.firebase.Timestamp

data class PezCapturado @JvmOverloads constructor(
    val especie: String = "",
    val cantidad: Int = 0,
    val observaciones: String? = null
)

data class UbicacionPesca @JvmOverloads constructor(
    val nombre: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val zona: String? = null
)

data class DeviceInfo @JvmOverloads constructor(
    val modelo: String = "",
    val marca: String = "",
    val versionAndroid: String = "",
    val versionApp: String = "1.0"
)

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
    val observaciones: String? = null,
    val estado: String = "completado"

)

sealed class FirebaseResult {
    object Success : FirebaseResult()
    data class Error(val message: String, val exception: Exception? = null) : FirebaseResult()
    object Loading : FirebaseResult()
}