// PescadexManager.kt - Gestión completa de la Pescadex integrada con Firebase
package com.example.juka.features.pescadex

import android.content.Context
import android.provider.Settings
import com.example.juka.features.pescadex.data.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class PescadexManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val deviceId = getDeviceId()

    companion object {
        private const val TAG = "🐟 PescadexManager"
        private const val COLLECTION_PESCADEX = "pescadex_usuarios"

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
                especieExistente.copy(
                    totalCapturas = especieExistente.totalCapturas + 1,
                    pesoRecord = maxOfNotNull(especieExistente.pesoRecord, peso),
                    locaciones = (especieExistente.locaciones + listOfNotNull(locacion)).distinct()
                )
            }

            val especiesActualizadas = pescadexActual.especiesDescubiertas.toMutableMap()
            especiesActualizadas[especieId] = especieActualizada

            val pescadexActualizado = pescadexActual.copy(
                especiesDescubiertas = especiesActualizadas,
                ultimaActividad = Timestamp.now()
            )

            firestore.document("$COLLECTION_PESCADEX/$deviceId")
                .set(pescadexActualizado, SetOptions.merge())
                .await()

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

    suspend fun obtenerPescadexUsuario(): PescadexUsuario {
        // ... (resto del código sin cambios)
        return PescadexUsuario() // Placeholder
    }

    // ... (resto de las funciones sin cambios)
}

sealed class RegistroResult {
    data class Success(
        val esNuevaEspecie: Boolean,
        val especieInfo: EspecieInfo,
        val totalEspecies: Int,
        val logrosDesbloqueados: List<LogroPescadex>
    ) : RegistroResult()

    data class Error(val mensaje: String) : RegistroResult()
}

data class EspecieConEstado(
    val info: EspecieInfo,
    val esCapturada: Boolean,
    val datosCaptura: EspecieDescubierta?,
    val orden: Int
)

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