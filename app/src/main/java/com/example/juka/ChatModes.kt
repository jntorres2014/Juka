// ChatModes.kt - Estructura para manejar dos tipos de chat
package com.example.juka

import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.MessageType
import java.text.SimpleDateFormat
import java.util.*
interface IMessage {
    val content: String
    val isFromUser: Boolean
    val type: MessageType
    val timestamp: String
}
// Enum para los diferentes modos de chat
enum class ChatMode {
    GENERAL,    // Chat normal - consejos, identificación, etc.
    CREAR_PARTE // Chat específico para crear un parte de pesca
}

// Data class para mensajes con contexto de modo
data class ChatMessageWithMode(
    override val content: String,
    override val isFromUser: Boolean,
    override val type: MessageType,
    override val timestamp: String,
    val mode: ChatMode
) : IMessage

// Data class para sesiones de chat de partes
data class ParteSessionChat(
    //val sessionId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val estado: EstadoParte = EstadoParte.BORRADOR,
    val fechaCreacion: String = getCurrentTimestamp(),
    val parteData: ParteEnProgreso = ParteEnProgreso(),

    )

// Estados posibles de un parte
enum class EstadoParte {
    EN_PROGRESO,    // Se está completando
    BORRADOR,       // Guardado como borrador
    COMPLETADO,     // Enviado y completado
    CANCELADO       // Cancelado por el usuario
}

// Data class para el parte en progreso (extraído del chat)
data class ParteEnProgreso(
    // Información básica
    val fecha: String? = null,
    val horaInicio: String? = null,
    val horaFin: String? = null,
    val lugar: String? = null,
    val provincia: Provincia? = null,

    // Modalidad de pesca
    val modalidad: ModalidadPesca? = null,
    val numeroCanas: Int? = null,
    val tipoEmbarcacion: TipoEmbarcacion? = null,

    // Especies capturadas
    val especiesCapturadas: List<EspecieCapturada> = emptyList(),

    // Multimedia y observaciones
    val imagenes: List<String> = emptyList(),
    val observaciones: String? = null,
    val noIdentificoEspecie: Boolean = false,

    // Metadatos
    val porcentajeCompletado: Int = 0,
    val camposFaltantes: List<String> = emptyList()
)

// Enums para las opciones predefinidas
enum class Provincia(val displayName: String) {
    BUENOS_AIRES("Buenos Aires"),
    CHUBUT("Chubut"),
    NEUQUEN("Neuquén"),
    RIO_NEGRO("Río Negro"),
    SANTA_CRUZ("Santa Cruz"),
    TIERRA_DEL_FUEGO("Tierra del Fuego");

    companion object {
        fun fromString(name: String): Provincia? {
            return values().find {
                it.displayName.equals(name, ignoreCase = true) ||
                        it.name.equals(name.replace(" ", "_"), ignoreCase = true)
            }
        }
    }
}

enum class ModalidadPesca(val displayName: String) {
    CON_LINEA_COSTA("Con línea costa"),
    CON_LINEA_EMBARCACION("Con línea embarcación"),
    CON_RED("Con red"),
    PESCA_SUBMARINA_COSTA("Pesca submarina costa"),
    PESCA_SUBMARINA_EMBARCACION("Pesca submarina embarcación");

    companion object {
        fun fromString(text: String): ModalidadPesca? {
            return values().find {
                it.displayName.contains(text, ignoreCase = true) ||
                        text.contains(it.displayName, ignoreCase = true)
            }
        }
    }
}

enum class TipoEmbarcacion(val displayName: String) {
    A_MOTOR("A motor"),
    KAYAK("Kayak"),
    VELERO("Velero"),
    GOMONES("Gomón");

    companion object {
        fun fromString(text: String): TipoEmbarcacion? {
            return values().find {
                it.displayName.contains(text, ignoreCase = true) ||
                        text.contains(it.displayName, ignoreCase = true)
            }
        }
    }
}

enum class TipoRed(val displayName: String) {
    AGALLERA("Agallera"),
    ARRASTRE("Arrastre"),
    MEDIOMUNDO("Mediomundo")
}

// Especie capturada individual
data class EspecieCapturada(
    val nombre: String,
    val numeroEjemplares: Int = 0,
    val pesoEstimado: Double? = null,
    val numeroRetenidos: Int = 0,
    val esEspecieDesconocida: Boolean = false
)

// Resultado de extracción con ML Kit
data class MLKitExtractionResult(
    val textoExtraido: String,
    val entidadesDetectadas: List<MLKitEntity>,
    val confianza: Float
)

data class MLKitEntity(
    val tipo: String, // "FECHA", "HORA", "LUGAR", "ESPECIE", etc.
    val valor: String,
    val confianza: Float,
    val posicionInicio: Int,
    val posicionFin: Int
)

// Helper function
private fun getCurrentTimestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}
// Agregar esta función helper al final del archivo ChatModes.kt

fun ParteEnProgreso.copy(
    fecha: String? = this.fecha,
    horaInicio: String? = this.horaInicio,
    horaFin: String? = this.horaFin,
    lugar: String? = this.lugar,
    provincia: Provincia? = this.provincia,
    modalidad: ModalidadPesca? = this.modalidad,
    numeroCanas: Int? = this.numeroCanas,
    tipoEmbarcacion: TipoEmbarcacion? = this.tipoEmbarcacion,
    especiesCapturadas: List<EspecieCapturada> = this.especiesCapturadas,
    imagenes: List<String> = this.imagenes,
    observaciones: String? = this.observaciones,
    noIdentificoEspecie: Boolean = this.noIdentificoEspecie,
    porcentajeCompletado: Int = this.porcentajeCompletado,
    camposFaltantes: List<String> = this.camposFaltantes
): ParteEnProgreso = ParteEnProgreso(
    fecha, horaInicio, horaFin, lugar, provincia, modalidad, numeroCanas,
    tipoEmbarcacion, especiesCapturadas, imagenes, observaciones,
    noIdentificoEspecie, porcentajeCompletado, camposFaltantes
)