package com.example.juka.data.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.juka.EstadoParte
import com.example.juka.FishDatabase
import com.example.juka.FishingData
import com.example.juka.ParteSessionChat
import com.example.juka.data.encuesta.EncuestaData
import com.example.juka.data.encuesta.RespuestaPregunta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp

/**
 * Clase principal para gestionar la interacción con Firebase, delegando las operaciones específicas
 * a clases especializadas como PartesFirebase, SesionesFirebase y EstadisticasFirebase.
 */
class FirebaseManager(val context: Context) {

    val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fishDatabase = FishDatabase(context)

    companion object {
        const val TAG = "🔥 FirebaseManager"
        const val COLLECTION_PARTES = "partes_pesca"
        const val SUBCOLLECTION_PARTES = "partes"
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    init {
        // Inicializa la base de datos local en un hilo de I/O
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

    // Delegación a clases especializadas
    suspend fun guardarParteAutomatico(fishingData: FishingData, transcripcion: String) =
        PartesFirebase(this).guardarParteAutomatico(fishingData, transcripcion)

    suspend fun obtenerMisPartes(limite: Int = 50) =
        PartesFirebase(this).obtenerMisPartes(limite)

/*
    suspend fun guardarParteSession(session: ParteSessionChat) =
        SesionesFirebase(this).guardarParteSession(session)
*/
    suspend fun convertirSessionAParte(session: ParteSessionChat) =
        SesionesFirebase(this).convertirSessionAParte(session)


/*
    suspend fun obtenerSesionesUsuario(estado: EstadoParte? = null) =
        SesionesFirebase(this).obtenerSesionesUsuario(estado)
*/


    /*suspend fun obtenerChatPorParteId(parteId: String) =
        SesionesFirebase(this).obtenerChatPorParteId(parteId)
*/
    /*suspend fun obtenerSesionesPendientes() =
        SesionesFirebase(this).obtenerSesionesPendientes()
*/
    /*  suspend fun obtenerSesionPorId(sessionId: String) =
        SesionesFirebase(this).obtenerSesionPorId(sessionId)
*/
 /*   suspend fun actualizarEstadoSesion(sessionId: String, nuevoEstado: EstadoParte) =
        SesionesFirebase(this).actualizarEstadoSesion(sessionId, nuevoEstado)

    suspend fun eliminarSesion(sessionId: String) =
        SesionesFirebase(this).eliminarSesion(sessionId)
*/
    suspend fun obtenerEstadisticas() =
        EstadisticasFirebase(this).obtenerEstadisticas()

    suspend fun obtenerEstadisticasGlobales() =
        EstadisticasFirebase(this).obtenerEstadisticasGlobales()

    //suspend fun obtenerEstadisticasSesiones() =
    //EstadisticasFirebase(this).obtenerEstadisticasSesiones()

    @RequiresApi(Build.VERSION_CODES.O)
   /* suspend fun obtenerHistorialChat(sessionId: String) =
        SesionesFirebase(this).obtenerHistorialChat(sessionId)
*/
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
}