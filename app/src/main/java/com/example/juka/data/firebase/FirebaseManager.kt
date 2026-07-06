package com.example.juka.data.firebase

import android.content.Context
import android.util.Log
import com.example.juka.FishDatabase
import com.example.juka.domain.usecase.FishingData
import com.example.juka.domain.model.ParteEnProgreso
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

    companion object {
        private const val TAG = "🔥 FirebaseManager"
    }

    val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()


    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
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

    /**
     * Persiste un parte finalizado a partir de los datos del chat o el wizard.
     * Reemplaza al antiguo convertirSessionAParte: ya no se persisten sesiones,
     * sólo el parte en sí (el chat queda local en Room si hace falta).
     */
    suspend fun guardarParteCompletado(
        parteData: ParteEnProgreso,
        transcripcion: String? = null,
        parteId: String? = null
    ) = PartesFirebase(this).guardarParteCompletado(parteData, transcripcion, parteId)

    suspend fun obtenerEstadisticas() =
        EstadisticasFirebase(this).obtenerEstadisticas()

    suspend fun obtenerEstadisticasGlobales() =
        EstadisticasFirebase(this).obtenerEstadisticasGlobales()

    // NOTA: las delegaciones de encuesta (guardarRespuestasEncuesta,
    // verificarEncuestaCompletada, obtenerRespuestasEncuesta,
    // guardarProgresoEncuesta, obtenerProgresoEncuesta,
    // limpiarProgresoEncuesta) y la clase EncuestaFirebase entera fueron
    // eliminadas. Escribían a la colección `userEncuestas/` que no era la
    // canónica — el flujo real vive en AuthManager.guardarEncuestaCompleta()
    // / markSurveyCompleted() apuntando a `users/{uid}` y subcolección
    // `users/{uid}/encuestas/`.

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
                "modalidadOtra" to (parte.modalidadOtra ?: ""),
                // Mismas claves que usa el modelo UbicacionParte para que las queries
                // y los reads desde otras rutas sean consistentes.
                "ubicacion" to parte.ubicacion?.let { ubi ->
                    mapOf(
                        "nombre" to (ubi.nombre ?: ""),
                        "latitud" to ubi.latitud,
                        "longitud" to ubi.longitud,
                        "zona" to (ubi.zona ?: "")
                    )
                },
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