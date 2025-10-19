package com.example.juka.data.firebase

import android.util.Log
import com.example.juka.data.encuesta.EncuestaData
import com.example.juka.data.encuesta.RespuestaPregunta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * Maneja todas las operaciones de Firebase relacionadas con la encuesta de usuarios
 */
class EncuestaFirebase(private val firebaseManager: FirebaseManager) {

    companion object {
        const val TAG = "📋 EncuestaFirebase"
        const val COLLECTION_USERS = "userEncuestas"
        const val SUBCOLLECTION_ENCUESTA = "encuesta"
        const val DOCUMENT_RESPUESTAS = "respuestas"
        const val COLLECTION_ESTADISTICAS_ENCUESTA = "estadisticas_encuesta"
    }

    /**
     * Guarda las respuestas completas de la encuesta del usuario
     */
    suspend fun guardarRespuestasEncuesta(encuestaData: EncuestaData): FirebaseResult {
        return try {
            val userId = firebaseManager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            Log.d(TAG, "Guardando encuesta para usuario: $userId")

            // Estructura del documento en Firestore
            val encuestaDocument = mapOf(
                "userId" to encuestaData.userId,
                "completada" to encuestaData.completada,
                "fechaInicio" to encuestaData.fechaInicio,
                "fechaCompletado" to (encuestaData.fechaCompletado ?: Timestamp.now()),
                "progreso" to encuestaData.progreso,
                "dispositivo" to encuestaData.dispositivo,
                "versionApp" to encuestaData.versionApp,
                "timestamp" to FieldValue.serverTimestamp(),
                "respuestas" to encuestaData.respuestas.map { respuesta ->
                    mapOf(
                        "preguntaId" to respuesta.preguntaId,
                        "respuestaTexto" to respuesta.respuestaTexto,
                        "opcionSeleccionada" to respuesta.opcionSeleccionada,
                        "opcionesSeleccionadas" to respuesta.opcionesSeleccionadas,
                        "valorEscala" to respuesta.valorEscala,
                        "respuestaSiNo" to respuesta.respuestaSiNo,
                        "timestamp" to respuesta.timestamp
                    )
                }
            )

            // Guardar en la ruta específica del usuario
            firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ENCUESTA)
                .document(DOCUMENT_RESPUESTAS)
                .set(encuestaDocument)
                .await()

            // También actualizar el flag de encuesta completada en el documento principal del usuario
            firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .update("encuestaCompletada", true, "fechaEncuesta", Timestamp.now())
                .await()

            // Incrementar estadísticas globales
            incrementarEstadisticasGlobales()

            Log.i(TAG, "✅ Encuesta guardada exitosamente para usuario $userId")
            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando encuesta: ${e.message}", e)
            FirebaseResult.Error("Error guardando encuesta: ${e.message}", e)
        }
    }

    /**
     * Verifica si el usuario actual ya completó la encuesta
     */
    suspend fun verificarEncuestaCompletada(): FirebaseResult {
        return try {
            val userId = firebaseManager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            Log.d(TAG, "Verificando encuesta para usuario: $userId")

            // Primero verificar el flag en el documento principal del usuario
            val userDoc = firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            val encuestaCompletada = userDoc.getBoolean("encuestaCompletada") ?: false

            if (encuestaCompletada) {
                Log.d(TAG, "✅ Usuario ya completó la encuesta")
                return FirebaseResult.Success
            }

            // Si no está marcada, verificar si existe el documento de respuestas
            val encuestaDoc = firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ENCUESTA)
                .document(DOCUMENT_RESPUESTAS)
                .get()
                .await()

            val tieneRespuestas = encuestaDoc.exists() &&
                    encuestaDoc.getBoolean("completada") == true

            if (tieneRespuestas) {
                // Actualizar el flag principal si faltaba
                firebaseManager.firestore
                    .collection(COLLECTION_USERS)
                    .document(userId)
                    .update("encuestaCompletada", true)
                    .await()

                return FirebaseResult.Success
            }

            Log.d(TAG, "Estado encuesta: $tieneRespuestas")
            FirebaseResult.Error("Encuesta no completada")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando encuesta: ${e.message}", e)
            FirebaseResult.Error("Error verificando encuesta: ${e.message}", e)
        }
    }

    /**
     * Obtiene las respuestas de la encuesta del usuario actual
     */
    suspend fun obtenerRespuestasEncuesta(): FirebaseResult {
        return try {
            val userId = firebaseManager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            Log.d(TAG, "Obteniendo respuestas de encuesta para: $userId")

            val encuestaDoc = firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ENCUESTA)
                .document(DOCUMENT_RESPUESTAS)
                .get()
                .await()

            if (!encuestaDoc.exists()) {
                Log.d(TAG, "No se encontraron respuestas de encuesta")
                return FirebaseResult.Success
            }

            // Convertir documento a EncuestaData
            val respuestasArray = encuestaDoc.get("respuestas") as? List<Map<String, Any>>
                ?: return FirebaseResult.Error("Formato de respuestas inválido")

            val respuestas = respuestasArray.map { respuestaMap ->
                RespuestaPregunta(
                    preguntaId = (respuestaMap["preguntaId"] as Long).toInt(),
                    respuestaTexto = respuestaMap["respuestaTexto"] as? String,
                    opcionSeleccionada = respuestaMap["opcionSeleccionada"] as? String,
                    opcionesSeleccionadas = (respuestaMap["opcionesSeleccionadas"] as? List<String>) ?: emptyList(),
                    valorEscala = (respuestaMap["valorEscala"] as? Long)?.toInt(),
                    respuestaSiNo = respuestaMap["respuestaSiNo"] as? Boolean,
                    timestamp = respuestaMap["timestamp"] as? Timestamp ?: Timestamp.now()
                )
            }

            val encuestaData = EncuestaData(
                userId = encuestaDoc.getString("userId") ?: userId,
                respuestas = respuestas,
                fechaInicio = encuestaDoc.getTimestamp("fechaInicio") ?: Timestamp.now(),
                fechaCompletado = encuestaDoc.getTimestamp("fechaCompletado"),
                completada = encuestaDoc.getBoolean("completada") ?: false,
                progreso = (encuestaDoc.getLong("progreso") ?: 0).toInt(),
                dispositivo = encuestaDoc.getString("dispositivo") ?: "Desconocido",
                versionApp = encuestaDoc.getString("versionApp") ?: "1.0.0"
            )

            Log.i(TAG, "✅ Respuestas obtenidas: ${respuestas.size} preguntas")
            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo respuestas: ${e.message}", e)
            FirebaseResult.Error("Error obteniendo respuestas: ${e.message}", e)
        }
    }

    /**
     * Guarda progreso parcial de la encuesta (para guardar borrador)
     */
    suspend fun guardarProgresoEncuesta(
        respuestasTemporales: List<RespuestaPregunta>,
        progreso: Int
    ): FirebaseResult {
        return try {
            val userId = firebaseManager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            Log.d(TAG, "Guardando progreso de encuesta: $progreso%")

            val progresoDocument = mapOf(
                "userId" to userId,
                "completada" to false,
                "fechaInicio" to Timestamp.now(),
                "progreso" to progreso,
                "dispositivo" to android.os.Build.MODEL,
                "versionApp" to "1.0.0",
                "lastUpdate" to FieldValue.serverTimestamp(),
                "respuestas" to respuestasTemporales.map { respuesta ->
                    mapOf(
                        "preguntaId" to respuesta.preguntaId,
                        "respuestaTexto" to respuesta.respuestaTexto,
                        "opcionSeleccionada" to respuesta.opcionSeleccionada,
                        "opcionesSeleccionadas" to respuesta.opcionesSeleccionadas,
                        "valorEscala" to respuesta.valorEscala,
                        "respuestaSiNo" to respuesta.respuestaSiNo,
                        "timestamp" to respuesta.timestamp
                    )
                }
            )

            firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ENCUESTA)
                .document("progreso")
                .set(progresoDocument)
                .await()

            Log.i(TAG, "✅ Progreso guardado: $progreso%")
            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando progreso: ${e.message}", e)
            FirebaseResult.Error("Error guardando progreso: ${e.message}", e)
        }
    }

    /**
     * Obtiene el progreso guardado de una encuesta incompleta
     */
    suspend fun obtenerProgresoEncuesta(): FirebaseResult {
        return try {
            val userId = firebaseManager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            val progresoDoc = firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ENCUESTA)
                .document("progreso")
                .get()
                .await()

            if (!progresoDoc.exists()) {
                return FirebaseResult.Success
            }

            // Convertir progreso a EncuestaData similar a obtenerRespuestasEncuesta
            val respuestasArray = progresoDoc.get("respuestas") as? List<Map<String, Any>>
                ?: return FirebaseResult.Success

            val respuestas = respuestasArray.map { respuestaMap ->
                RespuestaPregunta(
                    preguntaId = (respuestaMap["preguntaId"] as Long).toInt(),
                    respuestaTexto = respuestaMap["respuestaTexto"] as? String,
                    opcionSeleccionada = respuestaMap["opcionSeleccionada"] as? String,
                    opcionesSeleccionadas = (respuestaMap["opcionesSeleccionadas"] as? List<String>) ?: emptyList(),
                    valorEscala = (respuestaMap["valorEscala"] as? Long)?.toInt(),
                    respuestaSiNo = respuestaMap["respuestaSiNo"] as? Boolean,
                    timestamp = respuestaMap["timestamp"] as? Timestamp ?: Timestamp.now()
                )
            }

            Log.i(TAG, "✅ Progreso obtenido: ${respuestas.size} respuestas")
            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo progreso: ${e.message}", e)
            FirebaseResult.Error("Error obteniendo progreso: ${e.message}", e)
        }
    }

    /**
     * Elimina el progreso guardado (cuando se completa la encuesta)
     */
    suspend fun limpiarProgresoEncuesta(): FirebaseResult {
        return try {
            val userId = firebaseManager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            firebaseManager.firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ENCUESTA)
                .document("progreso")
                .delete()
                .await()

            Log.i(TAG, "✅ Progreso temporal eliminado")
            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error eliminando progreso: ${e.message}", e)
            FirebaseResult.Error("Error eliminando progreso: ${e.message}", e)
        }
    }

    /**
     * Incrementa las estadísticas globales de encuestas completadas
     */
    private suspend fun incrementarEstadisticasGlobales() {
        try {
            firebaseManager.firestore
                .collection(COLLECTION_ESTADISTICAS_ENCUESTA)
                .document("global")
                .update(
                    "totalCompletadas", FieldValue.increment(1),
                    "ultimaEncuesta", FieldValue.serverTimestamp()
                )
                .await()

            Log.d(TAG, "📊 Estadísticas globales actualizadas")

        } catch (e: Exception) {
            // Si el documento no existe, crearlo
            try {
                firebaseManager.firestore
                    .collection(COLLECTION_ESTADISTICAS_ENCUESTA)
                    .document("global")
                    .set(
                        mapOf(
                            "totalCompletadas" to 1,
                            "fechaInicio" to FieldValue.serverTimestamp(),
                            "ultimaEncuesta" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                Log.d(TAG, "📊 Documento de estadísticas creado")

            } catch (createException: Exception) {
                Log.e(TAG, "❌ Error creando estadísticas: ${createException.message}")
            }
        }
    }

    /**
     * Obtiene estadísticas globales de las encuestas
     */
    suspend fun obtenerEstadisticasGlobales(): FirebaseResult {
        return try {
            val estadisticasDoc = firebaseManager.firestore
                .collection(COLLECTION_ESTADISTICAS_ENCUESTA)
                .document("global")
                .get()
                .await()

            if (!estadisticasDoc.exists()) {
                return FirebaseResult.Success
            }

            val estadisticas = estadisticasDoc.data ?: emptyMap()
            Log.i(TAG, "📊 Estadísticas obtenidas: ${estadisticas["totalCompletadas"]} encuestas")

            FirebaseResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo estadísticas: ${e.message}", e)
            FirebaseResult.Error("Error obteniendo estadísticas: ${e.message}", e)
        }
    }
}