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




/**
 * Modelo de datos unificado para los reportes de pesca.
 * Coincide con lo que FirebaseManager espera guardar y MisReportesScreen espera mostrar.
 */
data class PartePesca(
    val id: String? = null,
    val userId: String? = null,

    // ✅ CAMPOS DE TIEMPO (Los que daban error)
    val fecha: String? = null,        // Ej: "2023-10-25"
    val horaInicio: String? = null,   // Ej: "08:00"
    val horaFin: String? = null,      // Ej: "12:30"
    val timestamp: Timestamp? = null,
    val duracionHoras: String? = null,
    val deviceInfo: DeviceInfo? = null,
    val tipo: String? = null,
    val modalidad: String? = null,
    val cantidadTotal: Int = 0,
    val observaciones: String? = null,
    val transcripcionOriginal: String? = null,
    val numeroCanas: Int? = 0,
    val userInfo: Map<String, Any>? = null,
    val estado: String? = null,

    // ✅ OBJETOS COMPLEJOS
    val ubicacion: UbicacionParte? = null,
    val peces: List<Captura> = emptyList(),
    val fotos: List<String> = emptyList()
)

// Sub-clase para ubicación
data class UbicacionParte(
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val nombre: String? = null
)

// Sub-clase para cada especie capturada
data class Captura(
    val especie: String = "",
    val cantidad: Int = 0,
    val pesoAproximado: Double = 0.0
)
sealed class FirebaseResult {
    object Success : FirebaseResult()
    data class Error(val message: String, val exception: Exception? = null) : FirebaseResult()
    object Loading : FirebaseResult()
}