package com.example.juka.domain.usecase

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.juka.BuildConfig

class FishIdentifier(private val application: Application) {

    // ⚠️ PEGA TU API KEY AQUÍ DE NUEVO (se borró al actualizar)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        //modelName = "gemini-2.5-flash-tts",
        apiKey = BuildConfig.GEMINI_API_KEY  // Usa esto en lugar del hardcoded
    )

    // 🔧 CORRECCIÓN CRÍTICA:
    // Tu cuenta no tiene acceso a 'gemini-1.5-flash', pero SÍ tiene 'gemini-2.0-flash'.
    // Usamos este modelo que vimos en tu lista de Python.
   /* private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = API_KEY
    )*/

    suspend fun identifyFish(imagePath: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("FISH_ID", "Iniciando análisis con Gemini: $imagePath")

            // 1. Cargar imagen optimizada
            val bitmap = decodeBitmapFromFile(imagePath)

            if (bitmap == null) {
                return@withContext "❌ Error: No se pudo leer el archivo de imagen."
            }

            // 2. Prompt de Experto en Pesca
            val prompt = """
                Actúa como un guía de pesca experto local y biólogo marino. 
                Analiza la foto de este pez y dame un reporte útil para un pescador deportivo.
                
                Estructura tu respuesta en estos 4 puntos clave, usa emojis:
                
                1. 🆔 **Identificación:**
                   - Nombre común.
                   - Nombre científico.
                
                2. 🎣 **Técnica de Pesca:**
                   - ¿Cuál es la mejor carnada o señuelo?
                   - ¿Dónde buscarlo (fondo, superficie, palos)?
                
                3. 🍽️ **Cocina:**
                   - ¿Es buena carne? ¿Tiene muchas espinas?
                   - Recomendación: ¿Frito, Parrilla o Chupín?
                
                4. ⚠️ **Cuidados:**
                   - ¿Tiene dientes o espinas peligrosas?
                   - Advertencia sobre veda si aplica.

                Si la imagen NO es un pez, responde con humor que eso no se pesca.
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            // 3. Llamada a la IA
            val response = generativeModel.generateContent(inputContent)

            return@withContext response.text ?: "La IA no devolvió texto."

        } catch (e: Exception) {
            // Manejo de errores REAL
            val errorMsg = e.localizedMessage ?: "Error desconocido"
            Log.e("FISH_ID", "Error Gemini: $errorMsg")

            // Si sigue fallando por el error de serialización, damos un mensaje claro
            if (errorMsg.contains("MissingFieldException")) {
                return@withContext "⚠️ Error de Modelo: El servidor rechazó la conexión (404). Verifica que tu API Key sea válida y tenga permisos para 'gemini-2.0-flash'."
            }

            return@withContext "⚠️ Ocurrió un error al consultar la IA:\n\n$errorMsg"
        }
    }

    private fun decodeBitmapFromFile(path: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)

            // Reducimos un poco más la calidad para asegurar que suba rápido
            options.inSampleSize = calculateInSampleSize(options, 800, 800)

            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}