package com.example.juka.data.firebase

import android.content.Context
import android.util.Log
import com.example.juka.FishDatabase
import com.example.juka.domain.usecase.FishingData
import com.example.juka.domain.model.ParteSessionChat
import com.example.juka.data.encuesta.EncuestaData
import com.example.juka.data.encuesta.RespuestaPregunta
import com.example.juka.data.firebase.EncuestaFirebase.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import com.example.juka.data.firebase.PartePesca
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // ✅ Importante
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.juka.util.Constants

class FirebaseManager(val context: Context) {

    val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fishDatabase = FishDatabase(context)
/*

    companion object {
        const val TAG = "🔥 FirebaseManager"
        const val COLLECTION_PARTES = "partes_pesca"
        const val SUBCOLLECTION_PARTES = "partes"
    }
*/

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fishDatabase.initialize()
                Log.d(TAG, "🟢 Base de datos inicializada: ${fishDatabase.getStats()}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error inicializando FishDatabase: ${e.message}", e)
            }
        }
    }

    fun getCurrentUserInfo(): Map<String, Any> = auth.currentUser?.let {
        mapOf(
            "userId" to (it.uid ?: "anonymous"),
            "email" to (it.email ?: "no-email"),
            "displayName" to (it.displayName ?: "Usuario sin nombre"),
            "photoUrl" to (it.photoUrl?.toString() ?: ""),
            "lastLogin" to Timestamp.now()
        )
    } ?: emptyMap()

    // --- Delegaciones ---
    suspend fun guardarParteAutomatico(fishingData: FishingData, transcripcion: String) =
        PartesFirebase(this).guardarParteAutomatico(fishingData, transcripcion)

    suspend fun obtenerMisPartes(limite: Int = 50) =
        PartesFirebase(this).obtenerMisPartes(limite)

    suspend fun convertirSessionAParte(session: ParteSessionChat) =
        SesionesFirebase(this).convertirSessionAParte(session)

    suspend fun obtenerEstadisticas() =
        EstadisticasFirebase(this).obtenerEstadisticas()

    suspend fun obtenerEstadisticasGlobales() =
        EstadisticasFirebase(this).obtenerEstadisticasGlobales()

    suspend fun guardarRespuestasEncuesta(respuestas: EncuestaData) =
        EncuestaFirebase(this).guardarRespuestasEncuesta(respuestas)

    suspend fun verificarEncuestaCompletada() =
        EncuestaFirebase(this).verificarEncuestaCompletada()

    suspend fun obtenerRespuestasEncuesta() =
        EncuestaFirebase(this).obtenerRespuestasEncuesta()

    suspend fun guardarProgresoEncuesta(respuestas: List<RespuestaPregunta>, progreso: Int) =
        EncuestaFirebase(this).guardarProgresoEncuesta(respuestas, progreso)

    suspend fun obtenerProgresoEncuesta() =
        EncuestaFirebase(this).obtenerProgresoEncuesta()

    suspend fun limpiarProgresoEncuesta() =
        EncuestaFirebase(this).limpiarProgresoEncuesta()

    // =================================================================
    // ✅ LA FUNCIÓN CLAVE (Debe estar AQUÍ, DENTRO de la clase)
    // =================================================================
    suspend fun guardarParte(parte: PartePesca): FirebaseResult {
        return try {
            val userId = auth.currentUser?.uid ?: return FirebaseResult.Error(Exception("No usuario").toString())

            val docRef = firestore.collection(Constants.Firebase.USERS_COLLECTION)
                .document(userId)
                .collection(Constants.Firebase.PARTES_COLLECTION)
                .document()

            val parteMap = mapOf(
                "id" to docRef.id,
                "userId" to userId,
                "fecha" to (parte.fecha ?: ""),
                "horaInicio" to (parte.horaInicio ?: ""),
                "horaFin" to (parte.horaFin ?: ""),
                "tipo" to (parte.tipo ?: "general"),
                "ubicacion" to mapOf(
                    "lat" to (parte.ubicacion?.latitud ?: 0.0),
                    "lng" to (parte.ubicacion?.longitud ?: 0.0),
                    "nombre" to (parte.ubicacion?.nombre ?: "")
                ),
                "cantidadTotal" to parte.cantidadTotal,
                "observaciones" to (parte.observaciones ?: ""),
                "timestamp" to Timestamp.now(),
                "peces" to parte.peces.map { pez ->
                    mapOf("especie" to pez.especie, "cantidad" to pez.cantidad)
                }
            )

            // Guardar usando await
            docRef.set(parteMap).await()

            // Retorno exitoso
            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Error guardando parte: ${e.message}")
            FirebaseResult.Error(e.toString())
        }
    }

} // ✅ FIN DE LA CLASE