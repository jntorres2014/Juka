// PescadexData.kt - Clases de datos para la funcionalidad Pescadex
package com.example.juka.features.pescadex.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

// Representa una especie que el usuario ha descubierto.
data class EspecieDescubierta(
    @PropertyName("especie_id") val especieId: String = "",
    @PropertyName("nombre_comun") val nombreComun: String = "",
    @PropertyName("nombre_cientifico") val nombreCientifico: String = "",
    @PropertyName("fecha_descubrimiento") val fechaDescubrimiento: Timestamp? = null,
    @PropertyName("total_capturas") val totalCapturas: Int = 0,
    @PropertyName("peso_record") val pesoRecord: Double? = null,
    @PropertyName("primera_foto") val primeraFoto: String? = null,
    @PropertyName("locaciones") val locaciones: List<String> = emptyList(),
    @PropertyName("rareza") val rareza: String = "comun"
)

// Representa el Pescadex completo de un usuario.
data class PescadexUsuario(
    @PropertyName("device_id") val deviceId: String = "",
    @PropertyName("especies_descubiertas") val especiesDescubiertas: Map<String, EspecieDescubierta> = emptyMap(),
    @PropertyName("fecha_inicio") val fechaInicio: Timestamp? = null,
    @PropertyName("ultima_actividad") val ultimaActividad: Timestamp? = null,
    @PropertyName("logros_desbloqueados") val logrosDesbloqueados: List<String> = emptyList()
)

// Representa un logro que se puede desbloquear en el Pescadex.
data class LogroPescadex(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val icono: String,
    val condicion: String, // "primera_captura", "5_especies", "especie_rara"
    val desbloqueado: Boolean = false,
    val fechaDesbloqueo: Timestamp? = null
)

// Contiene información estática y detallada sobre las especies de peces.
object EspeciesArgentinas {
    val especies = mapOf(
        "dorado" to EspecieInfo(
            id = "dorado",
            nombreComun = "Dorado",
            nombreCientifico = "Salminus brasiliensis",
            habitat = "Ríos de corriente fuerte del Paraná y Uruguay",
            mejoresCarnadas = listOf("carnada viva", "cucharitas plateadas", "mojarritas"),
            mejorHorario = "Amanecer y atardecer",
            tecnica = "Spinning con recuperación irregular",
            tamaño = "3-8 kg",
            temporada = "Octubre a abril",
            rareza = "poco_comun",
            descripcion = "El tigre del río, más codiciado por pescadores deportivos",
            region = "Mesopotamia",
            consejoEspecial = "Buscar pozones después de correderas"
        ),
        "surubi" to EspecieInfo(
            id = "surubi",
            nombreComun = "Surubí",
            nombreCientifico = "Pseudoplatystoma corruscans",
            habitat = "Pozones profundos del Paraná",
            mejoresCarnadas = listOf("lombrices grandes", "bagre", "tararira cortada"),
            mejorHorario = "Noche",
            tecnica = "Pesca de fondo con plomada pesada",
            tamaño = "5-25 kg",
            temporada = "Todo el año",
            rareza = "raro",
            descripcion = "El gigante pintado del río",
            region = "Cuenca del Plata",
            consejoEspecial = "Paciencia y carnadas grandes son clave"
        ),
        // ... (resto de las especies omitidas por brevedad)
    )
}

// Información detallada de una especie.
data class EspecieInfo(
    val id: String,
    val nombreComun: String,
    val nombreCientifico: String,
    val habitat: String,
    val mejoresCarnadas: List<String>,
    val mejorHorario: String,
    val tecnica: String,
    val tamaño: String,
    val temporada: String,
    val rareza: String, // "comun", "poco_comun", "raro", "epico", "legendario"
    val descripcion: String,
    val region: String,
    val consejoEspecial: String
)