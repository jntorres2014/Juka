// PescadexManager.kt - Gestión completa de la Pescadex integrada con Firebase
package com.example.juka

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.provider.Settings

class PescadexManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Identificador del Pescadex en Firestore. Hoy usamos el UID de Firebase
     * Auth para que el Pescadex viaje con la cuenta (no con el celular).
     * Si por alguna razón no hay usuario autenticado caemos al deviceId
     * legacy como fallback — esto solo aplica en escenarios degradados.
     */
    private val pescadexId: String
        get() = auth.currentUser?.uid ?: getDeviceId()

    companion object {
        private const val TAG = "🐟 PescadexManager"
        private const val COLLECTION_PESCADEX = "pescadex_usuarios"
        
        // Logros disponibles
        val LOGROS_DISPONIBLES = listOf(
            LogroPescadex("primer_pez", "Primer Pez", "Tu primera captura registrada", "🎣", "primera_captura"),
            LogroPescadex("explorador", "Explorador", "5 especies diferentes", "🗺️", "5_especies"),
            LogroPescadex("coleccionista", "Coleccionista", "10 especies diferentes", "📋", "10_especies"),
            LogroPescadex("especialista", "Especialista", "15 especies diferentes", "⭐", "15_especies"),
            LogroPescadex("maestro", "Maestro Pescador", "20 especies diferentes", "🏆", "20_especies"),
            LogroPescadex("cazador_raros", "Cazador de Raros", "Una especie rara", "💎", "especie_rara"),
            LogroPescadex("cazador_epicos", "Cazador Épico", "Una especie épica", "👑", "especie_epica"),
            LogroPescadex("completista", "Completista", "30 especies registradas", "🌟", "completista")
        )
    }
    
    /**
     * Registra una nueva especie capturada desde el chat o identificación
     */
    suspend fun registrarEspecieCapturada(
        especieId: String, 
        peso: Double? = null, 
        locacion: String? = null,
        fotoPath: String? = null
    ): RegistroResult {
        return try {
            android.util.Log.d(TAG, "🎯 Registrando especie: $especieId")
            
            val especieInfo = EspeciesArgentinas.especies[especieId]
            if (especieInfo == null) {
                android.util.Log.w(TAG, "⚠️ Especie desconocida: $especieId")
                return RegistroResult.Error("Especie no reconocida")
            }
            
            val pescadexActual = obtenerPescadexUsuario()
            val especieExistente = pescadexActual.especiesDescubiertas[especieId]
            
            val esNuevaEspecie = especieExistente == null
            
            val especieActualizada = if (esNuevaEspecie) {
                // Primera vez capturando esta especie
                EspecieDescubierta(
                    especieId = especieId,
                    nombreComun = especieInfo.nombreComun,
                    nombreCientifico = especieInfo.nombreCientifico,
                    fechaDescubrimiento = Timestamp.now(),
                    totalCapturas = 1,
                    pesoRecord = peso,
                    primeraFoto = fotoPath,
                    locaciones = listOfNotNull(locacion),
                    rareza = especieInfo.rareza
                )
            } else {
                // Actualizar especie existente
                especieExistente!!.copy(
                    totalCapturas = especieExistente.totalCapturas + 1,
                    pesoRecord = maxOfNotNull(especieExistente.pesoRecord, peso),
                    locaciones = (especieExistente.locaciones + listOfNotNull(locacion)).distinct()
                )
            }
            
            // Actualizar Pescadex
            val especiesActualizadas = pescadexActual.especiesDescubiertas.toMutableMap()
            especiesActualizadas[especieId] = especieActualizada
            
            val pescadexActualizado = pescadexActual.copy(
                especiesDescubiertas = especiesActualizadas,
                ultimaActividad = Timestamp.now()
            )
            
            // Guardar en Firebase
            firestore.document("$COLLECTION_PESCADEX/$pescadexId")
                .set(pescadexActualizado, SetOptions.merge())
                .await()
            
            // Verificar y desbloquear logros
            val logrosDesbloqueados = verificarLogros(pescadexActualizado)
            
            android.util.Log.i(TAG, "✅ Especie registrada: ${especieInfo.nombreComun}")
            
            RegistroResult.Success(
                esNuevaEspecie = esNuevaEspecie,
                especieInfo = especieInfo,
                totalEspecies = especiesActualizadas.size,
                logrosDesbloqueados = logrosDesbloqueados
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error registrando especie: ${e.message}", e)
            RegistroResult.Error("Error guardando especie: ${e.localizedMessage}")
        }
    }
    
    /**
     * Obtiene la Pescadex actual del usuario
     */
    suspend fun obtenerPescadexUsuario(): PescadexUsuario {
        return try {
            val document = firestore.document("$COLLECTION_PESCADEX/$pescadexId").get().await()
            
            if (document.exists()) {
                document.toObject(PescadexUsuario::class.java) ?: crearPescadexNuevo()
            } else {
                // Primera vez del usuario
                val nuevaPescadex = crearPescadexNuevo()
                firestore.document("$COLLECTION_PESCADEX/$pescadexId")
                    .set(nuevaPescadex)
                    .await()
                nuevaPescadex
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo Pescadex: ${e.message}")
            crearPescadexNuevo() // Fallback local
        }
    }
    
    /**
     * Obtiene estadísticas completas de la Pescadex
     */
    suspend fun obtenerEstadisticasPescadex(): EstadisticasPescadex {
        return try {
            val pescadex = obtenerPescadexUsuario()
            val especiesDescubiertas = pescadex.especiesDescubiertas.size
            val totalEspecies = EspeciesArgentinas.especies.size
            val progreso = if (totalEspecies > 0) especiesDescubiertas.toFloat() / totalEspecies * 100 else 0f
            
            val totalCapturas = pescadex.especiesDescubiertas.values.sumOf { it.totalCapturas }
            val especiesFavorita = pescadex.especiesDescubiertas.maxByOrNull { it.value.totalCapturas }?.key
            val logrosCount = pescadex.logrosDesbloqueados.size
            
            // Calcular días pescando (aproximado)
            val diasPescando = pescadex.especiesDescubiertas.values.mapNotNull { it.fechaDescubrimiento }
                .map { it.toDate().time }
                .distinct()
                .size
            
            EstadisticasPescadex(
                especiesDescubiertas = especiesDescubiertas,
                totalEspecies = totalEspecies,
                porcentajeCompletado = progreso,
                totalCapturas = totalCapturas,
                especiesFavorita = especiesFavorita,
                logrosDesbloqueados = logrosCount,
                diasPescando = diasPescando
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo estadísticas: ${e.message}")
            EstadisticasPescadex(0, 0, 0f, 0, null, 0, 0)
        }
    }
    
    /**
     * Obtiene especies disponibles con estado de captura
     */
    suspend fun obtenerEspeciesConEstado(): List<EspecieConEstado> {
        return try {
            val pescadex = obtenerPescadexUsuario()
            
            EspeciesArgentinas.especies.map { (id, info) ->
                val especieCapturada = pescadex.especiesDescubiertas[id]
                
                EspecieConEstado(
                    info = info,
                    esCapturada = especieCapturada != null,
                    datosCaptura = especieCapturada,
                    orden = obtenerOrdenRareza(info.rareza)
                )
            }.sortedBy { it.orden }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo especies: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Mapping del id legacy del Pescadex al id unificado en AchievementCatalog.
     * Los achievements del Pescadex ahora viven en /users/{uid}/unlocked_achievements
     * junto al resto, y aparecen en la pantalla "Mis Logros" con categoría ESPECIES.
     */
    private val LOGRO_TO_ACHIEVEMENT = mapOf(
        "primer_pez" to "pescadex_primer_pez",
        "explorador" to "pescadex_explorador",
        "coleccionista" to "pescadex_coleccionista",
        "especialista" to "pescadex_especialista",
        "maestro" to "pescadex_maestro",
        "cazador_raros" to "pescadex_cazador_raros",
        "cazador_epicos" to "pescadex_cazador_epicos",
        "completista" to "pescadex_completista"
    )

    private suspend fun verificarLogros(pescadex: PescadexUsuario): List<LogroPescadex> {
        val logrosDesbloqueados = mutableListOf<LogroPescadex>()
        val especiesCount = pescadex.especiesDescubiertas.size
        val tieneEspecieRara = pescadex.especiesDescubiertas.values.any {
            it.rareza in listOf("raro", "epico", "legendario")
        }
        val tieneEspecieEpica = pescadex.especiesDescubiertas.values.any {
            it.rareza in listOf("epico", "legendario")
        }

        LOGROS_DISPONIBLES.forEach { logro ->
            val cumpleCondicion = when (logro.condicion) {
                "primera_captura" -> especiesCount >= 1
                "5_especies" -> especiesCount >= 5
                "10_especies" -> especiesCount >= 10
                "15_especies" -> especiesCount >= 15
                "20_especies" -> especiesCount >= 20
                "completista" -> especiesCount >= 30
                "especie_rara" -> tieneEspecieRara
                "especie_epica" -> tieneEspecieEpica
                else -> false
            }

            if (cumpleCondicion && !pescadex.logrosDesbloqueados.contains(logro.id)) {
                logrosDesbloqueados.add(logro.copy(
                    desbloqueado = true,
                    fechaDesbloqueo = Timestamp.now()
                ))

                // Disparar también el achievement unificado en /users/{uid}/unlocked_achievements
                // para que aparezca en la pantalla "Mis Logros" general.
                val achievementId = LOGRO_TO_ACHIEVEMENT[logro.id]
                if (achievementId != null) {
                    try {
                        val ref = firestore.collection("users")
                            .document(auth.currentUser?.uid ?: return@forEach)
                            .collection("unlocked_achievements")
                            .document(achievementId)
                        val doc = ref.get().await()
                        if (!doc.exists()) {
                            ref.set(mapOf("timestamp" to System.currentTimeMillis())).await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "No se pudo persistir achievement $achievementId: ${e.message}")
                    }
                }
            }
        }

        // Actualizar logros legacy del Pescadex (compat con pantalla actual del Pescadex)
        if (logrosDesbloqueados.isNotEmpty()) {
            val nuevosLogros = pescadex.logrosDesbloqueados + logrosDesbloqueados.map { it.id }
            firestore.document("$COLLECTION_PESCADEX/$pescadexId")
                .update("logros_desbloqueados", nuevosLogros)
                .await()
        }

        return logrosDesbloqueados
    }
    
    private fun crearPescadexNuevo(): PescadexUsuario {
        return PescadexUsuario(
            deviceId = pescadexId,  // legacy: el campo se llama deviceId pero hoy guarda el UID
            especiesDescubiertas = emptyMap(),
            fechaInicio = Timestamp.now(),
            ultimaActividad = Timestamp.now(),
            logrosDesbloqueados = emptyList()
        )
    }

    /**
     * Auto-registro de las especies de un parte. Llamado desde el flujo
     * de guardarParteCompletado: por cada especie del parte se intenta
     * matchear con el catálogo `EspeciesArgentinas` por nombre normalizado;
     * si hay match, se llama a `registrarEspecieCapturada`. Los nombres
     * que no estén en el catálogo se descartan en silencio — el Pescadex
     * es una colección curada de especies argentinas conocidas.
     */
    suspend fun registrarEspeciesDeParte(
        nombresEspecies: List<String>,
        locacion: String? = null,
        fotoPath: String? = null
    ): List<RegistroResult> {
        val resultados = mutableListOf<RegistroResult>()
        nombresEspecies.distinct().forEach { nombre ->
            val id = matchearEspecie(nombre) ?: return@forEach
            resultados.add(registrarEspecieCapturada(id, peso = null, locacion = locacion, fotoPath = fotoPath))
        }
        return resultados
    }

    /**
     * Devuelve el id del catálogo correspondiente al nombre dado, o null
     * si no hay coincidencia. Normaliza: lowercase, sin acentos, sin
     * caracteres especiales, así "Pejerrey", "pejerrey" y "PEJERREY" matchean
     * todos contra el id "pejerrey".
     */
    private fun matchearEspecie(nombre: String): String? {
        val normalizado = nombre
            .lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .trim()
        // Match exacto contra los ids del catálogo (los ids ya están en minúsculas)
        if (EspeciesArgentinas.especies.containsKey(normalizado)) return normalizado
        // Match parcial: por ejemplo "dorado del paraná" contiene "dorado"
        return EspeciesArgentinas.especies.keys.firstOrNull { id -> normalizado.contains(id) }
    }
    
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "fallback_device_${System.currentTimeMillis()}"
        }
    }
    
    private fun obtenerOrdenRareza(rareza: String): Int {
        return when (rareza) {
            "comun" -> 1
            "poco_comun" -> 2
            "raro" -> 3
            "epico" -> 4
            "legendario" -> 5
            else -> 0
        }
    }
    
    private fun maxOfNotNull(a: Double?, b: Double?): Double? {
        return when {
            a == null && b == null -> null
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }
}

// Resultado de registro
sealed class RegistroResult {
    data class Success(
        val esNuevaEspecie: Boolean,
        val especieInfo: EspecieInfo,
        val totalEspecies: Int,
        val logrosDesbloqueados: List<LogroPescadex>
    ) : RegistroResult()
    
    data class Error(val mensaje: String) : RegistroResult()
}

// Estado de especie
data class EspecieConEstado(
    val info: EspecieInfo,
    val esCapturada: Boolean,
    val datosCaptura: EspecieDescubierta?,
    val orden: Int
)

// Estadísticas
data class EstadisticasPescadex(
    val especiesDescubiertas: Int,
    val totalEspecies: Int,
    val porcentajeCompletado: Float,
    val totalCapturas: Int,
    val especiesFavorita: String?,
    val logrosDesbloqueados: Int,
    val diasPescando: Int
) {
    val progreso: String get() = "$especiesDescubiertas/$totalEspecies"
}