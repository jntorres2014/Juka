// FirebaseManager.kt - VERSIÓN CORREGIDA COMPLETA
package com.example.juka

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ===== DATA CLASSES CORREGIDAS PARA FIREBASE =====

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
    val userId: String = "", // NUEVO: ID del usuario logueado
    val deviceId: String = "", // Mantener como referencia adicional
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
    val deviceInfo: DeviceInfo? = null,
    val userInfo: Map<String, Any> = emptyMap(), // NUEVO: Info del usuario
    val timestamp: com.google.firebase.Timestamp? = null,
    val estado: String = "completado"
)

sealed class FirebaseResult {
    object Success : FirebaseResult()
    data class Error(val message: String, val exception: Exception? = null) : FirebaseResult()
    object Loading : FirebaseResult()
}

class FirebaseManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fishDatabase = FishDatabase(context)

    companion object {
        private const val TAG = "🔥 FirebaseManager"
        private const val COLLECTION_PARTES = "partes_pesca"
        private const val SUBCOLLECTION_PARTES = "partes"
    }

    init {
        // Inicializar base de datos al crear el manager
        CoroutineScope(Dispatchers.IO).launch {
            fishDatabase.initialize()
            android.util.Log.d(TAG, "🐟 Base de datos inicializada: ${fishDatabase.getStats()}")
        }
    }

    /**
     * Obtiene el ID del usuario actual
     */
    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Obtiene información del usuario actual
     */
    private fun getCurrentUserInfo(): Map<String, Any> {
        val user = auth.currentUser
        return mapOf(
            "userId" to (user?.uid ?: "anonymous"),
            "email" to (user?.email ?: "no-email"),
            "displayName" to (user?.displayName ?: "Usuario sin nombre"),
            "photoUrl" to (user?.photoUrl?.toString() ?: ""),
            "lastLogin" to com.google.firebase.Timestamp.now()
        )
    }

    /**
     * Guarda automáticamente un parte cuando está completo
     */
    suspend fun guardarParteAutomatico(fishingData: FishingData, transcripcion: String): FirebaseResult {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e(TAG, "❌ Usuario no autenticado")
                return FirebaseResult.Error("Usuario no autenticado. Por favor, inicia sesión.")
            }

            android.util.Log.d(TAG, "💾 Guardando parte automático para usuario: $userId")
            android.util.Log.d(TAG, "📝 Transcripción: '$transcripcion'")

            // Validar que tiene datos mínimos necesarios
            if (!esParteValido(fishingData)) {
                android.util.Log.w(TAG, "⚠️ Parte incompleto, no guardando automáticamente")
                return FirebaseResult.Error("Parte incompleto - faltan datos esenciales")
            }

            val parte = convertirAPartePesca(fishingData, transcripcion, userId)
            val parteId = generarIdParte()

            android.util.Log.d(TAG, "🐟 Especies detectadas: ${parte.peces.size}")
            parte.peces.forEach { pez ->
                android.util.Log.d(TAG, "  - ${pez.especie}: ${pez.cantidad}")
            }

            // Guardar en Firestore con estructura basada en usuario
            val documentPath = "$COLLECTION_PARTES/$userId/$SUBCOLLECTION_PARTES/$parteId"

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
     * Obtiene todos los partes del usuario actual
     */
    suspend fun obtenerMisPartes(limite: Int = 50): List<PartePesca> {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e(TAG, "❌ Usuario no autenticado para obtener partes")
                return emptyList()
            }

            android.util.Log.d(TAG, "📋 Obteniendo partes para usuario: $userId")

            val query = firestore
                .collection("$COLLECTION_PARTES/$userId/$SUBCOLLECTION_PARTES")
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

            android.util.Log.i(TAG, "📊 Encontrados ${partes.size} partes para usuario $userId")
            partes

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error obteniendo partes: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Obtiene estadísticas del usuario actual
     */
    suspend fun obtenerEstadisticas(): Map<String, Any> {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                return mapOf("error" to "Usuario no autenticado")
            }

            val partes = obtenerMisPartes(100) // Últimos 100 partes

            mapOf(
                "userId" to userId,
                "total_partes" to partes.size,
                "total_peces" to partes.sumOf { it.cantidadTotal },
                "especie_favorita" to obtenerEspecieFavorita(partes),
                "mejor_dia" to obtenerMejorDia(partes),
                "tipo_preferido" to obtenerTipoPreferido(partes),
                "user_info" to getCurrentUserInfo()
            )

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo estadísticas: ${e.message}")
            mapOf("error" to e.localizedMessage)
        }
    }

    /**
     * Obtiene estadísticas globales (opcional - para rankings)
     */
    suspend fun obtenerEstadisticasGlobales(): Map<String, Any> {
        return try {
            // Esta función podría ser útil para rankings entre usuarios
            val globalQuery = firestore.collectionGroup(SUBCOLLECTION_PARTES)
                .limit(1000)

            val snapshot = globalQuery.get().await()
            val totalPartes = snapshot.size()
            val totalPeces = snapshot.documents.mapNotNull { doc ->
                doc.getLong("cantidadTotal")?.toInt()
            }.sum()

            mapOf(
                "total_usuarios_activos" to snapshot.documents
                    .mapNotNull { it.getString("userId") }
                    .distinct().size,
                "total_partes_globales" to totalPartes,
                "total_peces_globales" to totalPeces
            )

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo estadísticas globales: ${e.message}")
            emptyMap()
        }
    }

    // ===== MÉTODOS PRIVADOS ACTUALIZADOS =====

    private fun esParteValido(data: FishingData): Boolean {
        return data.day != null &&
                data.fishCount != null &&
                data.fishCount!! > 0 &&
                (data.startTime != null || data.endTime != null)
    }

    private fun convertirAPartePesca(data: FishingData, transcripcion: String, userId: String): PartePesca {
        android.util.Log.d(TAG, "🔄 Convirtiendo transcripción a parte de pesca para usuario: $userId")

        // Extraer especies usando la función mejorada
        val peces = extraerEspeciesDeTranscripcionMejorado(transcripcion, data.fishCount ?: 0)

        // Calcular duración si tenemos ambas horas
        val duracion = if (data.startTime != null && data.endTime != null) {
            calcularDuracion(data.startTime!!, data.endTime!!)
        } else null

        return PartePesca(
            userId = userId, // CAMBIO PRINCIPAL: usar userId en lugar de deviceId
            deviceId = getDeviceId(), // Mantener como referencia adicional
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
            deviceInfo = getDeviceInfo(),
            userInfo = getCurrentUserInfo(), // NUEVO: info del usuario
            timestamp = com.google.firebase.Timestamp.now(),
            estado = "completado"
        )
    }

    /**
     * Función mejorada para detectar especies (sin cambios)
     */
    private fun extraerEspeciesDeTranscripcionMejorado(transcripcion: String, cantidadTotal: Int): List<PezCapturado> {
        android.util.Log.d(TAG, "🐟 Analizando transcripción para especies...")
        android.util.Log.d(TAG, "📝 Texto: '$transcripcion'")

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

        // Buscar cada especie en el texto
        patronesEspecies.forEach { (especieNormalizada, variantes) ->
            variantes.forEach { variante ->
                if (transcripcionLower.contains(variante)) {
                    android.util.Log.d(TAG, "✅ Encontrada especie: '$variante' → $especieNormalizada")

                    val cantidad = extraerCantidadParaEspecieConPatrones(transcripcionLower, variante)

                    if (!especiesEncontradas.any { it.especie.equals(especieNormalizada, true) }) {
                        especiesEncontradas.add(
                            PezCapturado(
                                especie = especieNormalizada.replaceFirstChar { it.uppercase() },
                                cantidad = cantidad,
                                observaciones = "Detectado en: '$variante'"
                            )
                        )
                        android.util.Log.d(TAG, "➕ Agregada: $especieNormalizada ($cantidad unidades)")
                    }
                }
            }
        }

        // Si no encontramos especies específicas
        if (especiesEncontradas.isEmpty()) {
            android.util.Log.w(TAG, "⚠️ No se detectaron especies específicas, usando genérico")

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

        // Ajustar cantidades si es necesario
        val totalDetectado = especiesEncontradas.sumOf { it.cantidad }
        if (totalDetectado != cantidadTotal && cantidadTotal > 0) {
            android.util.Log.d(TAG, "⚖️ Ajustando cantidades: detectado $totalDetectado vs total $cantidadTotal")

            if (especiesEncontradas.size == 1) {
                especiesEncontradas[0] = especiesEncontradas[0].copy(cantidad = cantidadTotal)
            }
        }

        android.util.Log.i(TAG, "🏆 ESPECIES FINALES: ${especiesEncontradas.size}")
        especiesEncontradas.forEach { pez ->
            android.util.Log.i(TAG, "  🐟 ${pez.especie}: ${pez.cantidad} unidades")
        }

        return especiesEncontradas
    }

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
                    android.util.Log.d(TAG, "🔢 Cantidad encontrada para '$especie': $cantidad")
                    return cantidad
                }
            }
        }

        return 1
    }

    // Resto de métodos helper sin cambios significativos
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
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
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