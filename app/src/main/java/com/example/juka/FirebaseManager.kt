package com.example.juka

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class PartePesca(
    val id: String = "",
    val deviceId: String = "",
    val fecha: String = "",
    val horaInicio: String? = null,
    val horaFin: String? = null,
    val duracionHoras: String? = null,
    val peces: List<PezCapturado> = emptyList(),
    val cantidadTotal: Int = 0,
    val tipo: String? = null, // "costa" o "embarcado"
    val canas: Int? = null,
    val ubicacion: UbicacionPesca? = null,
    val fotos: List<String> = emptyList(),
    val transcripcionOriginal: String? = null,
    val deviceInfo: DeviceInfo? = null,
    val timestamp: com.google.firebase.Timestamp? = null,
    val estado: String = "completado" // "completado", "incompleto", "borrador"
)

data class PezCapturado(
    val especie: String,
    val cantidad: Int,
    val observaciones: String? = null
)

data class UbicacionPesca(
    val nombre: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val zona: String? = null // "Río Paraná", "Laguna Chascomús", etc.
)

data class DeviceInfo(
    val modelo: String,
    val marca: String,
    val versionAndroid: String,
    val versionApp: String = "1.0"
)

sealed class FirebaseResult {
    object Success : FirebaseResult()
    data class Error(val message: String, val exception: Exception? = null) : FirebaseResult()
    object Loading : FirebaseResult()
}

class FirebaseManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val deviceId = getDeviceId()

    companion object {
        private const val TAG = "🔥 FirebaseManager"
        private const val COLLECTION_PARTES = "partes_pesca"
        private const val SUBCOLLECTION_PARTES = "partes"

        // Mapeo de especies para Firebase
        private val ESPECIES_CONOCIDAS = mapOf(
            "dorado" to "Dorado",
            "dorados" to "Dorado",
            "surubí" to "Surubí",
            "surubi" to "Surubí",
            "pejerrey" to "Pejerrey",
            "pejerreyes" to "Pejerrey",
            "tararira" to "Tararira",
            "tarariras" to "Tararira",
            "pacú" to "Pacú",
            "pacu" to "Pacú",
            "sábalo" to "Sábalo",
            "sabalo" to "Sábalo",
            "boga" to "Boga",
            "bogas" to "Boga",
            "bagre" to "Bagre",
            "bagres" to "Bagre"
        )
    }

    /**
     * Guarda automáticamente un parte cuando está completo
     */
    suspend fun guardarParteAutomatico(fishingData: FishingData, transcripcion: String): FirebaseResult {
        return try {
            android.util.Log.d(TAG, "💾 Guardando parte automático...")

            // Validar que tiene datos mínimos necesarios
            if (!esParteValido(fishingData)) {
                android.util.Log.w(TAG, "⚠️ Parte incompleto, no guardando automáticamente")
                return FirebaseResult.Error("Parte incompleto - faltan datos esenciales")
            }

            val parte = convertirAPartePesca(fishingData, transcripcion)
            val parteId = generarIdParte()

            // Guardar en Firestore
            val documentPath = "$COLLECTION_PARTES/$deviceId/$SUBCOLLECTION_PARTES/$parteId"

            firestore.document(documentPath)
                .set(parte, SetOptions.merge())
                .await()

            android.util.Log.i(TAG, "✅ Parte guardado exitosamente: $parteId")
            android.util.Log.d(TAG, "📍 Ruta: $documentPath")
            android.util.Log.d(TAG, "🐟 Datos: ${parte.cantidadTotal} peces, tipo: ${parte.tipo}")

            FirebaseResult.Success

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error guardando parte: ${e.message}", e)
            FirebaseResult.Error("Error guardando en Firebase: ${e.localizedMessage}", e)
        }
    }

    /**
     * Obtiene todos los partes del dispositivo actual
     */
    suspend fun obtenerMisPartes(limite: Int = 50): List<PartePesca> {
        return try {
            android.util.Log.d(TAG, "📋 Obteniendo mis partes...")

            val query = firestore
                .collection("$COLLECTION_PARTES/$deviceId/$SUBCOLLECTION_PARTES")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limite.toLong())

            val snapshot = query.get().await()
            val partes = snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(PartePesca::class.java)?.copy(id = document.id)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parseando parte ${document.id}: ${e.message}")
                    null
                }
            }

            android.util.Log.i(TAG, "📊 Encontrados ${partes.size} partes")
            partes

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error obteniendo partes: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Obtiene estadísticas básicas
     */
    suspend fun obtenerEstadisticas(): Map<String, Any> {
        return try {
            val partes = obtenerMisPartes(100) // Últimos 100 partes

            mapOf(
                "total_partes" to partes.size,
                "total_peces" to partes.sumOf { it.cantidadTotal },
                "especie_favorita" to obtenerEspecieFavorita(partes),
                "mejor_dia" to obtenerMejorDia(partes),
                "tipo_preferido" to obtenerTipoPreferido(partes)
            )

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo estadísticas: ${e.message}")
            emptyMap()
        }
    }

    // MÉTODOS PRIVADOS

    private fun esParteValido(data: FishingData): Boolean {
        // Criterios mínimos para considerar un parte válido
        return data.day != null &&
                data.fishCount != null &&
                data.fishCount!! > 0 &&
                (data.startTime != null || data.endTime != null)
    }

    private fun convertirAPartePesca(data: FishingData, transcripcion: String): PartePesca {
        // Extraer especies de la transcripción
        val peces = extraerEspeciesDeTranscripcion(transcripcion, data.fishCount ?: 0)

        // Calcular duración si tenemos ambas horas
        val duracion = if (data.startTime != null && data.endTime != null) {
            calcularDuracion(data.startTime!!, data.endTime!!)
        } else null

        return PartePesca(
            deviceId = deviceId,
            fecha = data.day ?: getCurrentDate(),
            horaInicio = data.startTime,
            horaFin = data.endTime,
            duracionHoras = duracion,
            peces = peces,
            cantidadTotal = data.fishCount ?: 0,
            tipo = data.type,
            canas = data.rodsCount,
            ubicacion = null, // TODO: Implementar detección de ubicación
            fotos = if (data.photoUri != null) listOf(data.photoUri!!) else emptyList(),
            transcripcionOriginal = transcripcion,
            deviceInfo = getDeviceInfo(),
            timestamp = com.google.firebase.Timestamp.now(),
            estado = "completado"
        )
    }

    private fun extraerEspeciesDeTranscripcion(transcripcion: String, cantidadTotal: Int): List<PezCapturado> {
        val transcripcionLower = transcripcion.lowercase()
        val especiesEncontradas = mutableListOf<PezCapturado>()

        // Buscar especies conocidas en la transcripción
        ESPECIES_CONOCIDAS.forEach { (clave, especie) ->
            if (transcripcionLower.contains(clave)) {
                // Intentar extraer cantidad específica para esta especie
                val cantidad = extraerCantidadParaEspecie(transcripcionLower, clave) ?: 1
                especiesEncontradas.add(PezCapturado(especie, cantidad))
            }
        }

        // Si no encontramos especies específicas, crear una genérica
        if (especiesEncontradas.isEmpty()) {
            especiesEncontradas.add(PezCapturado("Pez (especie no especificada)", cantidadTotal))
        }

        return especiesEncontradas
    }

    private fun extraerCantidadParaEspecie(texto: String, especie: String): Int? {
        // Buscar patrones como "2 pejerreyes", "tres dorados"
        val patron1 = Regex("""(\d+)\s+$especie""")
        val patron2 = Regex("""(un|una|dos|tres|cuatro|cinco)\s+$especie""")

        patron1.find(texto)?.let {
            return it.groupValues[1].toIntOrNull()
        }

        patron2.find(texto)?.let { match ->
            val palabra = match.groupValues[1]
            return mapOf(
                "un" to 1, "una" to 1, "dos" to 2, "tres" to 3,
                "cuatro" to 4, "cinco" to 5
            )[palabra]
        }

        return null
    }

    private fun calcularDuracion(inicio: String, fin: String): String {
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
            "N/A"
        }
    }

    private fun obtenerEspecieFavorita(partes: List<PartePesca>): String {
        val conteoEspecies = mutableMapOf<String, Int>()

        partes.forEach { parte ->
            parte.peces.forEach { pez ->
                conteoEspecies[pez.especie] = conteoEspecies.getOrDefault(pez.especie, 0) + pez.cantidad
            }
        }

        return conteoEspecies.maxByOrNull { it.value }?.key ?: "N/A"
    }

    private fun obtenerMejorDia(partes: List<PartePesca>): String {
        return partes.maxByOrNull { it.cantidadTotal }?.fecha ?: "N/A"
    }

    private fun obtenerTipoPreferido(partes: List<PartePesca>): String {
        val tipos = partes.mapNotNull { it.tipo }
        return tipos.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "N/A"
    }

    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "fallback_device_${System.currentTimeMillis()}"
        }
    }

    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            modelo = Build.MODEL,
            marca = Build.MANUFACTURER,
            versionAndroid = Build.VERSION.RELEASE
        )
    }

    private fun generarIdParte(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "parte_${timestamp}_${(1000..9999).random()}"
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}