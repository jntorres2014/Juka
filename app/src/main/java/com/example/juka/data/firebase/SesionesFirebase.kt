package com.example.juka.data.firebase

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.juka.ChatMessageWithMode
import com.example.juka.EstadoParte
import com.example.juka.ParteSessionChat
import com.example.juka.data.firebase.FirebaseManager.Companion.COLLECTION_PARTES
import com.example.juka.viewmodel.ChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class SesionesFirebase(private val manager: FirebaseManager) {

    private val TAG = "${FirebaseManager.TAG} - Sesiones"

    suspend fun guardarParteSession(session: ParteSessionChat): FirebaseResult {
        return try {
            val userId = manager.getCurrentUserId() ?: return FirebaseResult.Error("Usuario no autenticado")
//            Log.d(TAG, "💾 Guardando sesión: ${session.sessionId} - Estado: ${session.estado}")
            val sessionPath = "${FirebaseManager.COLLECTION_PARTES}/$userId/"
            //val sessionPath = "${FirebaseManager.COLLECTION_PARTES}/$userId/sesiones/${session.sessionId}"
            manager.firestore.document(sessionPath)
                .set(session, SetOptions.merge())
                .await()

//            Log.i(TAG, "✅ Sesión guardada: ${session.sessionId}")
            FirebaseResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error guardando sesión: ${e.message}", e)
            FirebaseResult.Error("Error guardando sesión: ${e.localizedMessage}", e)
        }
    }

    suspend fun convertirSessionAParte(session: ParteSessionChat): FirebaseResult {
        return try {
            val userId = manager.getCurrentUserId() ?: return FirebaseResult.Error("Usuario no autenticado")

            // Validar que el sessionId no esté vacío
            /*  if (session.sessionId.isBlank()) {
                  Log.e(TAG, "❌ SessionId está vacío o nulo")
                  return FirebaseResult.Error("SessionId inválido")
              }*/

            val parteData = session.parteData
            val parteId = UtilsFirebase.generarIdParte()
            val ubicacionPesca = parteData.nombreLugar?.let { nombreLugar ->
                UbicacionPesca(
                    nombre = nombreLugar,  // <-- CAMBIO: de 'lugar' a 'nombreLugar'
                    zona = parteData.provincia?.displayName,
                    latitud = parteData.ubicacion?.latitude,  // Opcional: agrega GeoPoint si tu UbicacionPesca lo tiene
                    longitud = parteData.ubicacion?.longitude

                )
            }
            val parte = PartePesca(
                id = parteId,
                userId = userId,
                //deviceId = UtilsFirebase.getDeviceId(manager.context),
                fecha = parteData.fecha ?: UtilsFirebase.getCurrentDate(),
                horaInicio = parteData.horaInicio,
                horaFin = parteData.horaFin,
                duracionHoras = UtilsFirebase.calcularDuracionFromSession(parteData),
                peces = UtilsFirebase.convertirEspeciesCapturadas(parteData.especiesCapturadas),
                cantidadTotal = parteData.especiesCapturadas.sumOf { it.numeroEjemplares },
                tipo = parteData.modalidad?.displayName?.lowercase(),
                canas = parteData.numeroCanas,
                ubicacion = ubicacionPesca,
                //ubicacion = parteData.lugar?.let { UbicacionPesca(nombre = it, zona = parteData.provincia?.displayName) },
                fotos = parteData.imagenes,
                transcripcionOriginal = UtilsFirebase.extraerTranscripcionFromSession(session),
                //deviceInfo = UtilsFirebase.getDeviceInfo(),
                userInfo = manager.getCurrentUserInfo(),
                timestamp = Timestamp.now(),
                estado = session.estado.name.lowercase(),
                observaciones = parteData.observaciones

            )

            val partePath = "${FirebaseManager.COLLECTION_PARTES}/$userId/${FirebaseManager.SUBCOLLECTION_PARTES}/$parteId"
            manager.firestore.document(partePath)
                .set(parte, SetOptions.merge())
                .await()

            // Construir la ruta del documento de sesión correctamente
            val sessionPath = "${FirebaseManager.COLLECTION_PARTES}/$userId/"

            Log.d(TAG, "🔍 Actualizando sesión en ruta: $sessionPath")

            val sessionDoc = manager.firestore.document(sessionPath).get().await()
            if (sessionDoc.exists()) {
                manager.firestore.document(sessionPath)
                    .update("estado", EstadoParte.COMPLETADO.name)
                    .await()
            } else {
                manager.firestore.document(sessionPath)
                    .set(session.copy(estado = EstadoParte.COMPLETADO), SetOptions.merge())
                    .await()
            }

            Log.i(TAG, "✅ Parte convertido y guardado: $parteId")
            FirebaseResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error convirtiendo sesión: ${e.message}", e)
            FirebaseResult.Error("Error convirtiendo parte: ${e.localizedMessage}", e)
        }
    }

    /*
        suspend fun obtenerSesionesUsuario(estado: EstadoParte? = null): List<ParteSessionChat> {
            return try {
                val userId = manager.getCurrentUserId() ?: return emptyList()
                var query = manager.firestore
                    .collection("${FirebaseManager.COLLECTION_PARTES}/$userId/sesiones")
                    .orderBy("fechaCreacion", com.google.firebase.firestore.Query.Direction.DESCENDING)

                estado?.let { query = query.whereEqualTo("estado", it.name) }
                val snapshot = query.get().await()
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(ParteSessionChat::class.java)?.copy(obtenerSesionPorId() = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando sesión ${doc.id}: ${e.message}")
                        null
                    }
                }.also { Log.i(TAG, "📊 Encontradas ${it.size} sesiones para usuario $userId") }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error obteniendo sesiones: ${e.message}", e)
                emptyList()
            }
        }
    */

    /**
     * Obtiene el historial completo de mensajes de una sesión específica
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun obtenerHistorialChat(sessionId: String): List<ChatMessageWithMode> {
        return try {
            val userId = manager.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ Usuario no autenticado para obtener historial")
                return emptyList()
            }

            Log.d(TAG, "📜 Obteniendo historial de chat: $sessionId")

            val sessionDoc = manager.firestore
                .document("$COLLECTION_PARTES/$userId/sesiones/$sessionId")
                .get()
                .await()

            if (sessionDoc.exists()) {
                val session = sessionDoc.toObject(ParteSessionChat::class.java)
                val messages = session?.messages ?: emptyList()

                Log.i(TAG, "📨 Historial obtenido: ${messages.size} mensajes")

                // Filtrar solo ChatMessageWithMode
                return messages.filterIsInstance<ChatMessageWithMode>()
            } else {
                Log.w(TAG, "⚠️ Sesión no encontrada: $sessionId")
                emptyList()
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error obteniendo historial: ${e.message}", e)
            emptyList()
        }
    }

    /*
        suspend fun obtenerSesionesPendientes(): List<ParteSessionChat> {
            return try {
                val userId = manager.getCurrentUserId()
                if (userId == null) {
                    Log.e(TAG, "❌ Usuario no autenticado para obtener pendientes")
                    return emptyList()
                }

                Log.d(TAG, "⏳ Obteniendo sesiones pendientes para usuario: $userId")

                val query = manager.firestore
                    .collection("$COLLECTION_PARTES/$userId/sesiones")
                    .whereIn("estado", listOf(
                        EstadoParte.EN_PROGRESO.name,
                        EstadoParte.BORRADOR.name
                    ))
                    .orderBy("fechaCreacion", com.google.firebase.firestore.Query.Direction.DESCENDING)

                val snapshot = query.get().await()
                val sesiones = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(ParteSessionChat::class.java)?.copy(sessionId = document.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando sesión pendiente ${document.id}: ${e.message}")
                        null
                    }
                }

                Log.i(TAG, "📋 Encontradas ${sesiones.size} sesiones pendientes")
                sesiones.forEach { session ->
                    Log.d(TAG, "  - ${session.sessionId}: ${session.estado} (${session.messages.size} mensajes)")
                }

                sesiones

            } catch (e: Exception) {
                Log.e(TAG, "💥 Error obteniendo sesiones pendientes: ${e.message}", e)
                emptyList()
            }
        }
    */

    /*
        suspend fun obtenerSesionPorId(sessionId: String): ParteSessionChat? {
            return try {
                val userId = manager.getCurrentUserId()
                if (userId == null) {
                    Log.e(TAG, "❌ Usuario no autenticado")
                    return null
                }

                Log.d(TAG, "🔍 Obteniendo sesión: $sessionId")

                val sessionDoc = manager.firestore
                    .document("$COLLECTION_PARTES/$userId/sesiones/$sessionId")
                    .get()
                    .await()

                if (sessionDoc.exists()) {
                    val session = sessionDoc.toObject(ParteSessionChat::class.java)?.copy(sessionId = sessionDoc.id)
                    Log.i(TAG, "✅ Sesión obtenida: ${session?.estado}")
                    session
                } else {
                    Log.w(TAG, "⚠️ Sesión no encontrada: $sessionId")
                    null
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 Error obteniendo sesión: ${e.message}", e)
                null
            }
        }
    */

    suspend fun obtenerChatPorParteId(parteId: String): List<ChatMessageWithMode> {
        return try {
            val userId = manager.getCurrentUserId() ?: return emptyList()

            Log.d(TAG, "🔍 Buscando chat para parte: $parteId")

            val query = manager.firestore
                .collection("$COLLECTION_PARTES/$userId/sesiones")
                .whereEqualTo("parteData.id", parteId)
                .limit(1)

            val snapshot = query.get().await()
            val sessionDoc = snapshot.documents.firstOrNull()

            if (sessionDoc != null && sessionDoc.exists()) {
                val session = sessionDoc.toObject(ParteSessionChat::class.java)
                val messages = session?.messages ?: emptyList()

                // Filtrar y retornar directamente ChatMessageWithMode
                return messages.filterIsInstance<ChatMessageWithMode>()
            }

            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error obteniendo chat por parteId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun actualizarEstadoSesion(sessionId: String, nuevoEstado: EstadoParte): FirebaseResult {
        return try {
            val userId = manager.getCurrentUserId() ?: return FirebaseResult.Error("Usuario no autenticado")

            val sessionPath = "$COLLECTION_PARTES/$userId/sesiones/$sessionId"
            manager.firestore.document(sessionPath)
                .update("estado", nuevoEstado.name)
                .await()

            Log.i(TAG, "✅ Estado actualizado para sesión $sessionId: $nuevoEstado")
            FirebaseResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error actualizando estado: ${e.message}", e)
            FirebaseResult.Error("Error actualizando estado: ${e.localizedMessage}", e)
        }
    }

    suspend fun eliminarSesion(sessionId: String): FirebaseResult {
        return try {
            val userId = manager.getCurrentUserId() ?: return FirebaseResult.Error("Usuario no autenticado")

            val sessionPath = "$COLLECTION_PARTES/$userId/sesiones/$sessionId"
            manager.firestore.document(sessionPath)
                .delete()
                .await()

            Log.i(TAG, "✅ Sesión eliminada: $sessionId")
            FirebaseResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error eliminando sesión: ${e.message}", e)
            FirebaseResult.Error("Error eliminando sesión: ${e.localizedMessage}", e)
        }
    }
}