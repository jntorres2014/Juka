package com.example.juka.data.repository

import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.PartePesca // Asegúrate de importar tu modelo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FishingRepository(
    private val firebaseManager: FirebaseManager
) {
    // Usamos 'withContext' para asegurar que esto corra en un hilo secundario (IO)
    suspend fun obtenerMisPartes(): List<PartePesca> = withContext(Dispatchers.IO) {
        try {
            return@withContext firebaseManager.obtenerMisPartes()
        } catch (e: Exception) {
            // Aquí podrías agregar logs o manejo de errores custom
            throw e
        }
    }

    // Aquí podrás agregar más funciones a futuro:
    // suspend fun borrarParte(id: String) ...
    // suspend fun editarParte(parte: PartePesca) ...
}