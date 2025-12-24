package com.example.juka.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class ImageHelper(private val context: Context) {

    /**
     * Guarda una copia COMPRIMIDA de la imagen.
     * Reduce el peso de 4MB -> ~150KB sin perder calidad visible.
     */
    suspend fun saveImageToInternalStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Abrir la imagen original
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmapOriginal = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmapOriginal == null) return@withContext null

            // 2. Redimensionar si es gigante (Opcional, pero recomendado)
            // Si la foto es de 4000px, la bajamos a 1200px (suficiente para celular)
            val bitmapRedimensionado = resizeBitmap(bitmapOriginal, 1280)

            // 3. Crear nombre único
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val directory = File(context.filesDir, "captured_images")
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)

            // 4. EL TRUCO: Comprimir a JPG calidad 75%
            val outputStream = FileOutputStream(file)
            bitmapRedimensionado.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)

            outputStream.flush()
            outputStream.close()

            // Liberar memoria
            bitmapOriginal.recycle()
            if (bitmapOriginal != bitmapRedimensionado) bitmapRedimensionado.recycle()

            return@withContext file.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    // Función auxiliar para achicar la imagen si es muy grande
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height

        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}