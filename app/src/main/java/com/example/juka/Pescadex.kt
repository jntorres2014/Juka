// PescadexData.kt - Clases adaptadas a tu estructura Firebase
package com.example.juka

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

// Extender tu PartePesca existente con datos de Pescadex
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

data class PescadexUsuario(
    @PropertyName("device_id") val deviceId: String = "",
    @PropertyName("especies_descubiertas") val especiesDescubiertas: Map<String, EspecieDescubierta> = emptyMap(),
    @PropertyName("fecha_inicio") val fechaInicio: Timestamp? = null,
    @PropertyName("ultima_actividad") val ultimaActividad: Timestamp? = null,
    @PropertyName("logros_desbloqueados") val logrosDesbloqueados: List<String> = emptyList()
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

// Información completa de especies argentinas (expandida de tu FishDatabase)
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
        
        "pejerrey" to EspecieInfo(
            id = "pejerrey",
            nombreComun = "Pejerrey",
            nombreCientifico = "Odontesthes bonariensis",
            habitat = "Lagunas pampeanas y embalses",
            mejoresCarnadas = listOf("lombriz", "cascarudos", "artificiales pequeños"),
            mejorHorario = "Todo el día",
            tecnica = "Pesca con boya o spinning liviano",
            tamaño = "200g-1.5kg",
            temporada = "Marzo a noviembre",
            rareza = "comun",
            descripcion = "El clásico de las lagunas pampeanas",
            region = "Región Pampeana",
            consejoEspecial = "Variar profundidad hasta encontrar cardumen"
        ),
        
        "pacu" to EspecieInfo(
            id = "pacu",
            nombreComun = "Pacú",
            nombreCientifico = "Piaractus mesopotamicus",
            habitat = "Remansos y lagunas del Paraná",
            mejoresCarnadas = listOf("frutas", "maíz", "pellets", "semillas"),
            mejorHorario = "Mañana temprano",
            tecnica = "Pesca con boya a media agua",
            tamaño = "2-10 kg",
            temporada = "Noviembre a marzo",
            rareza = "poco_comun",
            descripcion = "El vegetariano del río",
            region = "Mesopotamia",
            consejoEspecial = "Frutas dulces son irresistibles en verano"
        ),
        
        "tararira" to EspecieInfo(
            id = "tararira",
            nombreComun = "Tararira",
            nombreCientifico = "Hoplias malabaricus",
            habitat = "Juncales y vegetación acuática",
            mejoresCarnadas = listOf("carnada viva", "spinnerbaits", "poppers"),
            mejorHorario = "Amanecer y anochecer",
            tecnica = "Casting entre la vegetación",
            tamaño = "1-5 kg",
            temporada = "Septiembre a abril",
            rareza = "comun",
            descripcion = "El depredador emboscado",
            region = "Todo el país",
            consejoEspecial = "Lanzar cerca de vegetación sumergida"
        ),
        
        "sabalo" to EspecieInfo(
            id = "sabalo",
            nombreComun = "Sábalo",
            nombreCientifico = "Prochilodus lineatus",
            habitat = "Ríos y arroyos de todo el país",
            mejoresCarnadas = listOf("masa", "pan", "lombriz"),
            mejorHorario = "Mañana y tarde",
            tecnica = "Pesca de fondo liviana",
            tamaño = "1-4 kg",
            temporada = "Todo el año",
            rareza = "comun",
            descripcion = "El pez más abundante de nuestros ríos",
            region = "Todo el país",
            consejoEspecial = "Masas dulces funcionan mejor"
        ),
        
        "boga" to EspecieInfo(
            id = "boga",
            nombreComun = "Boga",
            nombreCientifico = "Leporinus obtusidens",
            habitat = "Correderas y pozones del Paraná",
            mejoresCarnadas = listOf("masa", "maíz", "lombrices"),
            mejorHorario = "Día completo",
            tecnica = "Pesca al correntino",
            tamaño = "500g-2.5kg",
            temporada = "Octubre a marzo",
            rareza = "comun",
            descripcion = "Combativa y abundante",
            region = "Cuenca del Plata",
            consejoEspecial = "Seguir cardúmenes en movimiento"
        ),
        
        "bagre" to EspecieInfo(
            id = "bagre",
            nombreComun = "Bagre Amarillo",
            nombreCientifico = "Pimelodus maculatus",
            habitat = "Fondos fangosos de ríos y arroyos",
            mejoresCarnadas = listOf("lombrices", "hígado", "mojarras"),
            mejorHorario = "Noche",
            tecnica = "Pesca de fondo",
            tamaño = "300g-2kg",
            temporada = "Todo el año",
            rareza = "comun",
            descripcion = "El barrendero nocturno",
            region = "Todo el país",
            consejoEspecial = "Mejor actividad en noches cálidas"
        ),
        
        // Especies raras/épicas
        "manguruyú" to EspecieInfo(
            id = "manguruyú",
            nombreComun = "Manguruyú",
            nombreCientifico = "Zungaro jahu",
            habitat = "Pozones muy profundos del Paraná",
            mejoresCarnadas = listOf("tararira entera", "bagre grande"),
            mejorHorario = "Noche profunda",
            tecnica = "Pesca de fondo pesada",
            tamaño = "15-50+ kg",
            temporada = "Verano",
            rareza = "epico",
            descripcion = "El gigante absoluto de agua dulce",
            region = "Alto Paraná",
            consejoEspecial = "Solo para pescadores muy experimentados"
        ),
        
        "piraña" to EspecieInfo(
            id = "piraña",
            nombreComun = "Piraña",
            nombreCientifico = "Pygocentrus nattereri",
            habitat = "Remansos del norte argentino",
            mejoresCarnadas = listOf("carne", "pescado", "vísceras"),
            mejorHorario = "Tarde calurosa",
            tecnica = "Anzuelo simple con plomada",
            tamaño = "200g-1kg",
            temporada = "Verano",
            rareza = "raro",
            descripcion = "La famosa dentuda sudamericana",
            region = "Norte argentino",
            consejoEspecial = "Cuidado con los dientes al desanzuelar"
        )
    )
}

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