package com.example.juka.data.firebase

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.juka.ChatMessageWithMode
import com.example.juka.EspecieCapturada
import com.example.juka.FishingData
import com.example.juka.ParteEnProgreso
import com.example.juka.ParteSessionChat
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Objeto utilitario que contiene funciones genéricas y helper para la gestión de datos en Firebase.
 * No depende directamente de Firestore o Auth, sino que se enfoca en lógica reusable.
 */
object UtilsFirebase {

    private const val TAG = "🔧 UtilsFirebase"

    // === Funciones de utilidad básica ===

    /**
     * Obtiene el ID único del dispositivo basado en ANDROID_ID.
     */
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo deviceId: ${e.message}", e)
            "fallback_device_${System.currentTimeMillis()}"
        }
    }

    /**
     * Obtiene información del dispositivo.
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            modelo = Build.MODEL,
            marca = Build.MANUFACTURER,
            versionAndroid = Build.VERSION.RELEASE
        )
    }

    /**
     * Genera un ID único para un parte de pesca.
     */
    fun generarIdParte(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "parte_${timestamp}_${(1000..9999).random()}"
    }

    /**
     * Obtiene la fecha actual en formato yyyy-MM-dd.
     */
    fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // === Funciones de procesamiento de datos ===

    /**
     * Calcula la duración entre dos horas en formato legible.
     */
    fun calcularDuracion(inicio: String, fin: String): String {
        return try {
            val formato = SimpleDateFormat("HH:mm", Locale.getDefault())
            val horaInicio = formato.parse(inicio)
            val horaFin = formato.parse(fin)

            if (horaInicio != null && horaFin != null) {
                val diferenciaMs = horaFin.time - horaInicio.time
                val horas = diferenciaMs / (1000 * 60 * 60)
                val minutos = (diferenciaMs % (1000 * 60 * 60)) / (1000 * 60)

                when {
                    horas > 0 && minutos > 0 -> "${horas}h ${minutos}min"
                    horas > 0 -> "${horas}h"
                    else -> "${minutos}min"
                }
            } else "N/A"
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando duración: ${e.message}", e)
            "N/A"
        }
    }

    /**
     * Calcula la duración a partir de los datos de una sesión.
     */
    fun calcularDuracionFromSession(parteData: ParteEnProgreso): String? {
        return if (parteData.horaInicio != null && parteData.horaFin != null) {
            calcularDuracion(parteData.horaInicio!!, parteData.horaFin!!)
        } else null
    }

    /**
     * Extrae las especies capturadas de una transcripción mejorada.
     */
    fun extraerEspeciesDeTranscripcionMejorado(transcripcion: String, cantidadTotal: Int): List<PezCapturado> {
        Log.d(TAG, "🐟 Analizando transcripción para especies...")
        Log.d(TAG, "📝 Texto: '$transcripcion'")

        val transcripcionLower = transcripcion.lowercase().trim()
        val especiesEncontradas = mutableListOf<PezCapturado>()

        // Patrones mejorados para especies argentinas
        val patronesEspecies = mapOf(
            "dorado" to listOf("dorado", "dorados", "doradito", "doraditos"),
            "surubí" to listOf("surubí", "surubís", "surubi", "surubis", "pintado", "pintados"),
            "pacú" to listOf("pacú", "pacús", "pacu", "pacus", "piraña vegetariana"),
            "pejerrey" to listOf("pejerrey", "pejerreyes", "pejerrei"),
            "tararira" to listOf("tararira", "tarariras", "tarira", "tariras"),
            "sábalo" to listOf("sábalo", "sábalos", "sabalo", "sabalos"),
            "boga" to listOf("boga", "bogas"),
            "bagre" to listOf("bagre", "bagres", "gato", "gatos"),
            "corvina" to listOf("corvina", "corvinas"),
            "lisa" to listOf("lisa", "lisas"),
            "lenguado" to listOf("lenguado", "lenguados"),
            "trucha" to listOf("trucha", "truchas"),
            "carpa" to listOf("carpa", "carpas")
        )

        patronesEspecies.forEach { (especieNormalizada, variantes) ->
            variantes.forEach { variante ->
                if (transcripcionLower.contains(variante)) {
                    Log.d(TAG, "✅ Encontrada especie: '$variante' → $especieNormalizada")

                    val cantidad = extraerCantidadParaEspecieConPatrones(transcripcionLower, variante)

                    if (!especiesEncontradas.any { it.especie.equals(especieNormalizada, true) }) {
                        especiesEncontradas.add(
                            PezCapturado(
                                especie = especieNormalizada.replaceFirstChar { it.uppercase() },
                                cantidad = cantidad,
                                observaciones = "Detectado en: '$variante'"
                            )
                        )
                        Log.d(TAG, "➕ Agregada: $especieNormalizada ($cantidad unidades)")
                    }
                }
            }
        }

        if (especiesEncontradas.isEmpty()) {
            Log.w(TAG, "⚠️ No se detectaron especies específicas, usando genérico")

            val esGenerico = transcripcionLower.contains("pez") ||
                    transcripcionLower.contains("pescado") ||
                    transcripcionLower.contains("captura")

            especiesEncontradas.add(
                PezCapturado(
                    especie = if (esGenerico) "Peces varios" else "Especies sin identificar",
                    cantidad = cantidadTotal,
                    observaciones = "Detectado automáticamente - especificar especie para mejores reportes"
                )
            )
        }

        val totalDetectado = especiesEncontradas.sumOf { it.cantidad }
        if (totalDetectado != cantidadTotal && cantidadTotal > 0) {
            Log.d(TAG, "⚖️ Ajustando cantidades: detectado $totalDetectado vs total $cantidadTotal")
            if (especiesEncontradas.size == 1) {
                especiesEncontradas[0] = especiesEncontradas[0].copy(cantidad = cantidadTotal)
            }
        }

        Log.i(TAG, "🏆 ESPECIES FINALES: ${especiesEncontradas.size}")
        especiesEncontradas.forEach { pez ->
            Log.i(TAG, "  🐟 ${pez.especie}: ${pez.cantidad} unidades")
        }

        return especiesEncontradas
    }

    /**
     * Extrae la cantidad de una especie usando patrones de texto.
     */
    private fun extraerCantidadParaEspecieConPatrones(texto: String, especie: String): Int {
        val patronesCantidad = listOf(
            Regex("""(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s+${Regex.escape(especie)}"""),
            Regex("""${Regex.escape(especie)}\s*:?\s*(\d+)"""),
            Regex("""(?:pesqu[éeí]|saq[uéí]|captur[éeí])\s+(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s+${Regex.escape(especie)}""")
        )

        val numerosEscritos = mapOf(
            "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
            "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10
        )

        patronesCantidad.forEach { patron ->
            val match = patron.find(texto)
            if (match != null) {
                val cantidadStr = match.groupValues[1].lowercase()
                val cantidad = numerosEscritos[cantidadStr] ?: cantidadStr.toIntOrNull()

                if (cantidad != null && cantidad > 0) {
                    Log.d(TAG, "🔢 Cantidad encontrada para '$especie': $cantidad")
                    return cantidad
                }
            }
        }

        return 1
    }

    /**
     * Convierte una lista de especies capturadas a la estructura de PezCapturado.
     */
    fun convertirEspeciesCapturadas(especies: List<EspecieCapturada>): List<PezCapturado> {
        return especies.map { especie ->
            PezCapturado(
                especie = especie.nombre,
                cantidad = especie.numeroEjemplares,
                observaciones = if (especie.esEspecieDesconocida) "Especie no identificada" else null
            )
        }
    }

    /**
     * Extrae la transcripción de una sesión a partir de los mensajes del usuario.
     */
    fun extraerTranscripcionFromSession(session: ParteSessionChat): String {
        return try {
            session.messages
                .filterIsInstance<ChatMessageWithMode>()
                .filter { it.isFromUser }
                .joinToString(" ") { it.content }
                .take(500) // Limitar longitud para evitar datos excesivos
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo transcripción: ${e.message}", e)
            ""
        }
    }

    /**
     * Determina si una sesión y un parte están relacionados basándose en datos clave.
     */
    fun sonSesionYParteRelacionados(session: ParteSessionChat, parte: PartePesca): Boolean {
        val parteData = session.parteData

        val fechaCoincide = parteData.fecha == parte.fecha
        val cantidadCoincide = parteData.especiesCapturadas.sumOf { it.numeroEjemplares } == parte.cantidadTotal
        val horaCoincide = parteData.horaInicio == parte.horaInicio || parteData.horaFin == parte.horaFin

        val transcripcionSimilar = if (!parte.transcripcionOriginal.isNullOrBlank()) {
            val transcripcionSession = session.messages
                .filter { it.isFromUser }
                .joinToString(" ") { it.content }
            transcripcionSession.take(100) in (parte.transcripcionOriginal ?: "")
        } else false

        return fechaCoincide && (cantidadCoincide || horaCoincide || transcripcionSimilar)
    }

    /**
     * Valida si un objeto FishingData tiene los datos mínimos requeridos.
     */
    fun esParteValido(data: FishingData): Boolean {
        return data.day != null &&
                data.fishCount != null &&
                data.fishCount!! > 0 &&
                (data.startTime != null || data.endTime != null)
    }

    /**
     * Convierte un objeto FishingData a PartePesca.
     */
    fun convertirAPartePesca(data: FishingData, transcripcion: String, userId: String, context: Context): PartePesca {
        Log.d(TAG, "🔄 Convirtiendo transcripción a parte de pesca para usuario: $userId")

        val peces = extraerEspeciesDeTranscripcionMejorado(transcripcion, data.fishCount ?: 0)
        val duracion = if (data.startTime != null && data.endTime != null) {
            calcularDuracion(data.startTime!!, data.endTime!!)
        } else null
        Log.d(TAG, "⌛ Dataaaaaa: $data")

        return PartePesca(
            userId = userId,
            //deviceId = getDeviceId(context),
            fecha = data.day ?: getCurrentDate(),
            horaInicio = data.startTime,
            horaFin = data.endTime,
            duracionHoras = duracion,
            peces = peces,
            cantidadTotal = data.fishCount ?: 0,
            tipo = data.type,
            canas = data.rodsCount,
            ubicacion = null,
            fotos = if (data.photoUri != null) listOf(data.photoUri!!) else emptyList(),
            transcripcionOriginal = transcripcion,
            userInfo = mapOf(
                "userId" to userId,
                "email" to "no-email", // Placeholder, debería venir de Auth
                "displayName" to "Usuario sin nombre", // Placeholder
                "photoUrl" to "",
                "lastLogin" to Timestamp.now()
            ),
            timestamp = Timestamp.now(),
            estado = "completado",


        )
    }

    /**
     * Obtiene la especie favorita a partir de una lista de partes.
     */
    fun obtenerEspecieFavorita(partes: List<PartePesca>): String {
        val conteoEspecies = mutableMapOf<String, Int>()
        partes.forEach { parte ->
            parte.peces.forEach { pez ->
                conteoEspecies[pez.especie] = conteoEspecies.getOrDefault(pez.especie, 0) + pez.cantidad
            }
        }
        return conteoEspecies.maxByOrNull { it.value }?.key ?: "N/A"
    }

    /**
     * Obtiene el mejor día de pesca basado en la cantidad total de peces.
     */
    fun obtenerMejorDia(partes: List<PartePesca>): String {
        return partes.maxByOrNull { it.cantidadTotal }?.fecha ?: "N/A"
    }

    /**
     * Obtiene el tipo de pesca preferido.
     */
    fun obtenerTipoPreferido(partes: List<PartePesca>): String {
        val tipos = partes.mapNotNull { it.tipo }
        return tipos.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "N/A"
    }

    /**
     * Obtiene timestamp actual en formato String
     */
    fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}