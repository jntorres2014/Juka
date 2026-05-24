// PescadexData.kt - Clases adaptadas a tu estructura Firebase
package com.example.juka

import com.google.firebase.Timestamp

// Extender tu PartePesca existente con datos de Pescadex
// NOTA: NO usamos @PropertyName en estas clases. La razón:
// `@PropertyName` en Kotlin data class `val` se aplica al field por default,
// no al getter (que es donde Firestore lee). Con `@get:PropertyName` la
// serialización (escritura) funciona, pero la deserialización (lectura, via
// constructor) usa los nombres camelCase del param. Resultado: escribe
// snake_case, lee buscando camelCase → no encuentra → devuelve emptyMap.
// Optamos por dejar todo en camelCase (default natural de Kotlin): consistente
// para ambas direcciones, predecible, sin sorpresas.
data class EspecieDescubierta(
    val especieId: String = "",
    val nombreComun: String = "",
    val nombreCientifico: String = "",
    val fechaDescubrimiento: Timestamp? = null,
    val totalCapturas: Int = 0,
    // pesoRecord y primeraFoto se editan manualmente desde la UI del Pescadex
    // (sección "Mi Récord"), no se llenan automáticamente desde el parte.
    val pesoRecord: Double? = null,
    val primeraFoto: String? = null,
    val locaciones: List<String> = emptyList(),
    val rareza: String = "comun",
    // "Mejor día": la jornada en la que el usuario pescó más ejemplares de
    // esta especie en un mismo parte. Se actualiza automáticamente al guardar
    // un parte si la cantidad supera (o iguala) la marca anterior.
    val mejorDiaFecha: String? = null,
    val mejorDiaCantidad: Int = 0
)

data class PescadexUsuario(
    val deviceId: String = "",
    val especiesDescubiertas: Map<String, EspecieDescubierta> = emptyMap(),
    val fechaInicio: Timestamp? = null,
    val ultimaActividad: Timestamp? = null,
    val logrosDesbloqueados: List<String> = emptyList()
)

data class LogroPescadex(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val icono: String,
    val condicion: String, // "primera_captura", "5_especies", "especie_rara"
    val desbloqueado: Boolean = false,
    val fechaDesbloqueo: Timestamp? = null
)

/**
 * DTO chico que usa `PescadexManager.registrarEspeciesDeParte` para no
 * acoplarse al modelo del parte (que vive en otro paquete). Equivale a un
 * tuple (nombre, cantidad) por cada especie de un parte.
 */
data class EspecieDelParte(
    val nombre: String,
    val cantidad: Int
)

// NOTA: el catálogo legacy `EspeciesArgentinas` (10 especies hardcoded) fue
// eliminado. La fuente única de verdad ahora es `FishDatabase`, que lee el
// JSON `peces_argentinos1.json` desde assets. `PescadexManager.catalogoActual()`
// construye un `Map<String, EspecieInfo>` a partir de ahí en cada consulta.
// Se conserva el data class `EspecieInfo` porque sigue siendo el modelo que
// consume la UI del Pescadex.

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