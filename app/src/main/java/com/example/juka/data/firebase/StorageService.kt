package com.example.juka.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class StorageService {
    private val storageRef = FirebaseStorage.getInstance().reference

    // Sube el archivo y devuelve el Link de Internet (https://...)
    suspend fun subirImagen(localPath: String): String? {
        val file = File(localPath)
        if (!file.exists()) return null

        // Nombre único en la nube
        val remoteName = "images/${UUID.randomUUID()}.jpg"
        val imageRef = storageRef.child(remoteName)

        return try {
            // Subir
            imageRef.putFile(Uri.fromFile(file)).await()
            // Obtener Link
            imageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}