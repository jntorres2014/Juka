package com.example.juka.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

enum class EstadoParticipante {
    PENDIENTE,
    ACEPTADO,
    RECHAZADO
}

enum class TipoPuntaje(val displayName: String) {
    CANTIDAD_PECES("Cantidad de peces"),
    ESPECIES_DISTINTAS("Especies distintas"),
    PERSONALIZADO("Reglas personalizadas")
}

/**
 * Reglas de puntaje componibles para torneos PERSONALIZADO. El admin tilda
 * las que quiera al crear el torneo y se aplican aditivamente sobre cada
 * parte que cargue un participante.
 *
 * Cada regla es opcional (null = desactivada):
 *  - `bonusPrimerParte`: puntos extra al PRIMER parte cargado en el torneo
 *    (lo gana el participante que llega primero — una sola vez por torneo).
 *  - `puntosPorPez`: suma X puntos por cada ejemplar registrado, sin importar
 *    la especie. Equivalente a la regla "por cantidad" tradicional, pero con
 *    multiplicador editable.
 *  - `puntosPorEspecie`: tabla nombre-normalizado → puntos. Cada especie de
 *    la tabla suma su valor por cada ejemplar capturado.
 *  - `puntosOtrosPeces`: catch-all para peces que NO están en `puntosPorEspecie`.
 *    Solo se aplica si `puntosPorEspecie` está activado (no es null y tiene
 *    al menos una entrada). Si querés que los no listados no cuenten, dejá 0.
 *
 * Persistencia: data class con defaults en todos los params para que el SDK
 * de Firestore pueda (de)serializar. NO usamos @PropertyName por los líos
 * de getter vs constructor en val (ver Pescadex).
 */
data class ReglasPuntaje(
    val bonusPrimerParte: Int? = null,
    val puntosPorPez: Int? = null,
    val puntosPorEspecie: Map<String, Int>? = null,
    val puntosOtrosPeces: Int = 0
) {
    @get:Exclude
    val tieneAlgunaRegla: Boolean
        get() = bonusPrimerParte != null ||
                puntosPorPez != null ||
                (puntosPorEspecie != null && puntosPorEspecie.isNotEmpty())
}

enum class EstadoTorneo {
    PROXIMO,
    ACTIVO,
    FINALIZADO
}

enum class EstadoParteTorneo {
    ACTIVO,
    RECHAZADO
}

// ── Torneo ────────────────────────────────────────────────────────────────────

data class Torneo(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val fechaInicio: Timestamp = Timestamp.now(),
    val fechaFin: Timestamp = Timestamp.now(),
    val tipoPuntaje: String = TipoPuntaje.CANTIDAD_PECES.name,
    // Texto libre legacy — sigue acá por compat con torneos viejos y como
    // descripción adicional opcional. Para el cálculo real usamos `reglas`.
    val reglasPersonalizadas: String = "",
    // Reglas estructuradas para tipoPuntaje == PERSONALIZADO. null si el
    // torneo es de los otros dos tipos (cálculo legacy en TorneosViewModel).
    val reglas: ReglasPuntaje? = null,
    val codigoInvitacion: String = "",
    val creadoEn: Timestamp = Timestamp.now()
) {
    // ✅ @get:Exclude — Firestore no intenta serializar estas propiedades
    @get:Exclude
    val estado: EstadoTorneo
        get() {
            val ahora = Timestamp.now()
            return when {
                ahora < fechaInicio -> EstadoTorneo.PROXIMO
                ahora > fechaFin    -> EstadoTorneo.FINALIZADO
                else                -> EstadoTorneo.ACTIVO
            }
        }

    @get:Exclude
    val tipoPuntajeEnum: TipoPuntaje
        get() = TipoPuntaje.values().find { it.name == tipoPuntaje } ?: TipoPuntaje.CANTIDAD_PECES
}

// ── ParticipanteTorneo ────────────────────────────────────────────────────────

data class ParticipanteTorneo(
    val userId: String = "",
    val userName: String = "",
    val userPhoto: String = "",
    val estado: String = EstadoParticipante.PENDIENTE.name,
    val puntaje: Int = 0,
    val parteIds: List<String> = emptyList(),
    val solicitadoEn: Timestamp = Timestamp.now(),
    val respondidoEn: Timestamp? = null
) {
    @get:Exclude
    val estadoEnum: EstadoParticipante
        get() = EstadoParticipante.values().find { it.name == estado } ?: EstadoParticipante.PENDIENTE
}

// ── ParteTorneo ───────────────────────────────────────────────────────────────

data class EspecieTorneo(
    val nombre: String = "",
    val cantidad: Int = 0
)

data class ParteTorneo(
    val parteId: String = "",
    val userId: String = "",
    val userName: String = "",
    val fecha: String = "",
    val especies: List<EspecieTorneo> = emptyList(),
    val fotos: List<String> = emptyList(),
    val puntaje: Int = 0,
    val estado: String = EstadoParteTorneo.ACTIVO.name,
    val motivoRechazo: String = "",
    val creadoEn: Timestamp = Timestamp.now()
) {
    @get:Exclude
    val estadoEnum: EstadoParteTorneo
        get() = EstadoParteTorneo.values().find { it.name == estado } ?: EstadoParteTorneo.ACTIVO

    @get:Exclude
    val totalPeces: Int
        get() = especies.sumOf { it.cantidad }
}

// ── TorneoConParticipantes (solo UI, no va a Firestore) ───────────────────────

data class TorneoConParticipantes(
    val torneo: Torneo,
    val participantes: List<ParticipanteTorneo> = emptyList(),
    val partes: List<ParteTorneo> = emptyList(),
    val miEstado: EstadoParticipante? = null,
    val miPuntaje: Int = 0,
    val miPosicion: Int = 0
) {
    val soyCreador: Boolean get() = miEstado == null

    val pendientes: List<ParticipanteTorneo>
        get() = participantes.filter { it.estadoEnum == EstadoParticipante.PENDIENTE }

    val aceptados: List<ParticipanteTorneo>
        get() = participantes
            .filter { it.estadoEnum == EstadoParticipante.ACEPTADO }
            .sortedByDescending { it.puntaje }

    val partesActivos: List<ParteTorneo>
        get() = partes
            .filter { it.estadoEnum == EstadoParteTorneo.ACTIVO }
            .sortedByDescending { it.creadoEn.seconds }

    val partesRechazados: List<ParteTorneo>
        get() = partes.filter { it.estadoEnum == EstadoParteTorneo.RECHAZADO }
}