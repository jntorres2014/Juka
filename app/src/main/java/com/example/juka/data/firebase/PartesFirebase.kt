package com.example.juka.data.firebase

import android.util.Log
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.usecase.FishingData

import com.example.juka.data.firebase.UtilsFirebase.convertirAPartePesca
import com.example.juka.data.firebase.UtilsFirebase.esParteValido
import com.example.juka.data.firebase.UtilsFirebase.generarIdParte
import com.example.juka.util.Constants
import com.example.juka.util.Constants.Firebase.PARTES_COLLECTION
import com.example.juka.util.Constants.Firebase.SUBCOLLECTION_PARTES
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class PartesFirebase(private val manager: FirebaseManager) {
    private val TAG = "${Constants.Firebase.TAG} - Partes"

    /**
     * Persiste un parte finalizado a partir de los datos cargados en el chat
     * o el wizard. Reemplaza al antiguo flujo de "sesiones": ya no se guarda
     * el chat en Firestore, sólo el parte en sí.
     */
    suspend fun guardarParteCompletado(
        parteData: ParteEnProgreso,
        transcripcion: String? = null
    ): FirebaseResult {
        return try {
            val userId = manager.getCurrentUserId()
                ?: return FirebaseResult.Error("Usuario no autenticado")

            val parteId = generarIdParte()

            val ubicacion = if (
                parteData.nombreLugar != null ||
                parteData.provincia != null ||
                parteData.ubicacion != null
            ) {
                UbicacionParte(
                    nombre = parteData.nombreLugar,
                    zona = parteData.provincia?.displayName,
                    latitud = parteData.ubicacion?.latitude,
                    longitud = parteData.ubicacion?.longitude
                )
            } else null

            val parte = PartePesca(
                id = parteId,
                userId = userId,
                fecha = parteData.fecha ?: UtilsFirebase.getCurrentDate(),
                horaInicio = parteData.horaInicio,
                horaFin = parteData.horaFin,
                duracionHoras = UtilsFirebase.calcularDuracionFromSession(parteData),
                peces = parteData.especiesCapturadas.map { pez ->
                    Captura(especie = pez.nombre, cantidad = pez.numeroEjemplares)
                },
                cantidadTotal = parteData.especiesCapturadas.sumOf { it.numeroEjemplares },
                // Si el usuario eligió "Otra" modalidad en el wizard con texto
                // libre, ese texto va como tipo. Sino, el displayName del enum.
                tipo = parteData.modalidadOtra
                    ?: parteData.modalidad?.displayName?.lowercase(),
                modalidadOtra = parteData.modalidadOtra,
                numeroCanas = parteData.numeroCanas,
                ubicacion = ubicacion,
                fotos = parteData.imagenes,
                transcripcionOriginal = transcripcion,
                deviceInfo = UtilsFirebase.getDeviceInfo(),
                userInfo = manager.getCurrentUserInfo(),
                timestamp = Timestamp.now(),
                estado = "completado",
                observaciones = parteData.observaciones
            )

            val partePath = "$PARTES_COLLECTION/$userId/$SUBCOLLECTION_PARTES/$parteId"
            val ok = withTimeoutOrNull(15_000) {
                manager.firestore.document(partePath)
                    .set(parte, SetOptions.merge())
                    .await()
                true
            }
            if (ok != true) {
                Log.w(TAG, "⏳ Timeout o sin red guardando parte $parteId")
                return FirebaseResult.Error("Sin conexión o red lenta. El parte no se subió.")
            }

            Log.i(TAG, "✅ Parte completado guardado: $parteId")
            FirebaseResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error guardando parte completado: ${e.message}", e)
            FirebaseResult.Error("Error guardando parte: ${e.localizedMessage}", e)
        }
    }

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
            val documentPath = "$PARTES_COLLECTION/$userId/$SUBCOLLECTION_PARTES/$parteId"

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
                .collection("$PARTES_COLLECTION/$userId/$SUBCOLLECTION_PARTES")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limite.toLong())

            // Timeout: si no hay red, devolvemos lista vacía para que la
            // pantalla de "Mis Reportes" salga del spinner y muestre el
            // empty state en lugar de quedar girando.
            val snapshot = withTimeoutOrNull(12_000) { query.get().await() }
                ?: run {
                    Log.w(TAG, "⏳ Timeout/sin red obteniendo partes")
                    return emptyList()
                }
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