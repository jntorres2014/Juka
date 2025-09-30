package com.example.juka.data.firebase

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.juka.ParteSessionChat
import com.example.juka.data.firebase.FirebaseManager.Companion.COLLECTION_PARTES
import com.example.juka.data.firebase.FirebaseManager.Companion.SUBCOLLECTION_PARTES
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class EstadisticasFirebase(private val manager: FirebaseManager) {
    private val TAG = "${FirebaseManager.TAG} - Estadísticas"

    suspend fun obtenerEstadisticas(): Map<String, Any> {
        return try {
            val userId = manager.getCurrentUserId() ?: return emptyMap()

            Log.d(TAG, "📊 Obteniendo estadísticas para usuario: $userId")

            val query = manager.firestore
                .collection("$COLLECTION_PARTES/$userId/$SUBCOLLECTION_PARTES")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            val snapshot = query.get().await()
            val partes = snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(PartePesca::class.java)?.copy(id = document.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando parte ${document.id}: ${e.message}")
                    null
                }
            }

            val totalPartes = partes.size
            val totalPeces = partes.sumOf { it.cantidadTotal }
            val especieFavorita = UtilsFirebase.obtenerEspecieFavorita(partes)
            val mejorDia = UtilsFirebase.obtenerMejorDia(partes)
            val tipoPreferido = UtilsFirebase.obtenerTipoPreferido(partes)

            mapOf(
                "totalPartes" to totalPartes,
                "totalPeces" to totalPeces,
                "especieFavorita" to especieFavorita,
                "mejorDia" to mejorDia,
                "tipoPreferido" to tipoPreferido,
                "promediosPorParte" to if (totalPartes > 0) totalPeces.toDouble() / totalPartes else 0.0
            )

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error obteniendo estadísticas: ${e.message}", e)
            emptyMap()
        }
    }

    suspend fun obtenerEstadisticasGlobales(): Map<String, Any> {
        return try {
            Log.d(TAG, "🌍 Obteniendo estadísticas globales")

            // Por ahora retorna estadísticas del usuario actual
            obtenerEstadisticas()

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error obteniendo estadísticas globales: ${e.message}", e)
            emptyMap()
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun obtenerHistorialChat(sessionId: String) =
        SesionesFirebase(manager).obtenerHistorialChat(sessionId)  // CAMBIO: manager en lugar de this
}