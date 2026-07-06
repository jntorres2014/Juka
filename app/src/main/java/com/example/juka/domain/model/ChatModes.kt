// ChatModes.kt - Estructura para manejar dos tipos de chat
package com.example.juka.domain.model

import com.example.juka.data.ChatOption
import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.MessageType
import com.google.firebase.firestore.GeoPoint
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
    CREAR_PARTE, // Chat específico para crear un parte de pesca

}

// Data class para mensajes con contexto de modo
data class ChatMessageWithMode(
    override val content: String,
    override val isFromUser: Boolean,
    override val type: MessageType,
    override val timestamp: String,
    val mode: ChatMode,
    val options: List<ChatOption>? = null,

    // NUEVO: Metadata para información adicional
    val metadata: Map<String, String> = emptyMap()
) : IMessage

// Estados posibles de un parte
enum class EstadoParte {
    EN_PROGRESO,    // Se está completando
    BORRADOR,       // Guardado como borrador
    COMPLETADO,     // Enviado y completado
    CANCELADO,       // Cancelado por el usuario
}

// Data class para el parte en progreso (extraído del chat)
data class ParteEnProgreso(
    // Información básica
    val fecha: String? = null,
    val horaInicio: String? = null,
    val horaFin: String? = null,
    // val lugar: String? = null, // ELIMINAMOS EL CAMPO LUGAR
    val provincia: Provincia? = null,

    // NUEVOS CAMPOS PARA UBICACIÓN
    val ubicacion: GeoPoint? = null,
    val nombreLugar: String? = null,

    // Modalidad de pesca. Si el usuario eligió "Otra" en el wizard o describió
    // una modalidad no listada en el chat, el texto libre va en modalidadOtra
    // y modalidad queda en null. La precedencia al mostrar/persistir es:
    // modalidadOtra ?: modalidad?.displayName.
    val modalidad: ModalidadPesca? = null,
    val modalidadOtra: String? = null,
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
    val camposFaltantes: List<String> = emptyList(),
    val sinCapturas: Boolean = false // ✅ NUEVO FLAG
)

// Enums para las opciones predefinidas
enum class Provincia(val displayName: String) {
    BUENOS_AIRES("Buenos Aires"),
    CATAMARCA("Catamarca"),
    CHACO("Chaco"),
    CHUBUT("Chubut"),
    CIUDAD_AUTONOMA_DE_BUENOS_AIRES("Ciudad Autónoma de Buenos Aires"),
    CORDOBA("Córdoba"),
    CORRIENTES("Corrientes"),
    ENTRE_RIOS("Entre Ríos"),
    FORMOSA("Formosa"),
    JUJUY("Jujuy"),
    LA_PAMPA("La Pampa"),
    LA_RIOJA("La Rioja"),
    MENDOZA("Mendoza"),
    MISIONES("Misiones"),
    NEUQUEN("Neuquén"),
    RIO_NEGRO("Río Negro"),
    SALTA("Salta"),
    SAN_JUAN("San Juan"),
    SAN_LUIS("San Luis"),
    SANTA_CRUZ("Santa Cruz"),
    SANTA_FE("Santa Fe"),
    SANTIAGO_DEL_ESTERO("Santiago del Estero"),
    TIERRA_DEL_FUEGO("Tierra del Fuego"),
    TUCUMAN("Tucumán");

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

// Especie capturada individual.
//
// Devolución: `numeroDevueltos` = ejemplares devueltos al agua (default 0 = me
// llevé todo, que es lo intuitivo). Los retenidos se derivan:
// numeroEjemplares - numeroDevueltos. (numeroRetenidos queda por compatibilidad
// pero ya no se usa para la devolución.)
data class EspecieCapturada(
    val nombre: String,
    val numeroEjemplares: Int = 0,
    val numeroRetenidos: Int = 0,
    val numeroDevueltos: Int = 0,
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

