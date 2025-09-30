package com.example.juka.data.firebase

import android.util.Log
import com.example.juka.FishingData
import com.example.juka.data.firebase.FirebaseManager.Companion.COLLECTION_PARTES
import com.example.juka.data.firebase.FirebaseManager.Companion.SUBCOLLECTION_PARTES
import com.example.juka.data.firebase.UtilsFirebase.convertirAPartePesca
import com.example.juka.data.firebase.UtilsFirebase.esParteValido
import com.example.juka.data.firebase.UtilsFirebase.generarIdParte
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class PartesFirebase(private val manager: FirebaseManager) {
    private val TAG = "${FirebaseManager.TAG} - Partes"

    suspend fun guardarParteAutomatico(fishingData: FishingData, transcripcion: String): FirebaseResult {
        return try {
            val userId = manager.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ Usuario no autenticado")
                return FirebaseResult.Error("Usuario no autenticado. Por favor, inicia sesión.")
            }

            Log.d(TAG, "💾 Guardando parte automático para usuario: $userId")
            Log.d(TAG, "📝 Transcripción: '$transcripcion'")

            // Validar que tiene datos mínimos necesarios
            if (!esParteValido(fishingData)) {
                Log.w(TAG, "⚠️ Parte incompleto, no guardando automáticamente")
                return FirebaseResult.Error("Parte incompleto - faltan datos esenciales")
            }

            val parte = convertirAPartePesca(fishingData, transcripcion, userId, manager.context)
            val parteId = generarIdParte()

            Log.d(TAG, "🐟 Especies detectadas: ${parte.peces.size}")
            parte.peces.forEach { pez ->
                Log.d(TAG, "  - ${pez.especie}: ${pez.cantidad}")
            }

            // Guardar en Firestore con estructura basada en usuario
            val documentPath = "$COLLECTION_PARTES/$userId/$SUBCOLLECTION_PARTES/$parteId"

            manager.firestore.document(documentPath)
                .set(parte, SetOptions.merge())
                .await()

            Log.i(TAG, "✅ Parte guardado exitosamente: $parteId")
            Log.d(TAG, "📍 Ruta: $documentPath")
            Log.d(TAG, "🐟 Datos: ${parte.cantidadTotal} peces, tipo: ${parte.tipo}")

            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error guardando parte: ${e.message}", e)
            FirebaseResult.Error("Error guardando en Firebase: ${e.localizedMessage}", e)
        }
    }

    /**
     * Obtiene todos los partes del usuario actual
     */
    suspend fun obtenerMisPartes(limite: Int = 50): List<PartePesca> {
        return try {
            val userId = manager.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ Usuario no autenticado para obtener partes")
                return emptyList()
            }

            Log.d(TAG, "📋 Obteniendo partes para usuario: $userId")

            val query = manager.firestore
                .collection("$COLLECTION_PARTES/$userId/$SUBCOLLECTION_PARTES")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limite.toLong())

            val snapshot = query.get().await()
            val partes = snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(PartePesca::class.java)?.copy(id = document.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando parte ${document.id}: ${e.message}")
                    null
                }
            }

            Log.i(TAG, "📊 Encontrados ${partes.size} partes para usuario $userId")
            partes

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error obteniendo partes: ${e.message}", e)
            emptyList()
        }
    }
}