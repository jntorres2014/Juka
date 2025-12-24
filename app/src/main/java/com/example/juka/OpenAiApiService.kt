/*
package com.example.juka

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.juka.BuildConfig // Import correcto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class OpenAiApiService { // Renombré para claridad
    private val client = OkHttpClient()
    private val apiUrl = "https://api.openai.com/v1/chat/completions" // Endpoint de OpenAI

    suspend fun generateResponse(prompt: String, apiKey: String = getApiKey()): String {
        val json = JSONObject().apply {
            put("model", "gpt-4o-mini") // Modelo gratuito/eficiente; usa "gpt-3.5-turbo" si preferís
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("max_tokens", 300)
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonResp = JSONObject(response.body?.string() ?: "{}")
                jsonResp.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            } else {
                Log.e("OPENAI_ERROR", "Error: ${response.message}")
                "Error en IA: ${response.message}"
            }
        }
    }

    // Para imágenes: Usa visión con GPT-4o-mini (soporta base64)
    suspend fun analyzeImage(prompt: String, imageBase64: String, apiKey: String = getApiKey()): String {
        val json = JSONObject().apply {
            put("model", "gpt-4o-mini") // Soporta visión
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$imageBase64")
                        })
                    })
                })
            }))
            put("max_tokens", 300)
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonResp = JSONObject(response.body?.string() ?: "{}")
                jsonResp.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            } else {
                Log.e("OPENAI_IMAGE_ERROR", "Error: ${response.message}")
                "Error en análisis de imagen: ${response.message}"
            }
        }
    }

    private fun getApiKey(): String = BuildConfig.OPENAI_API_KEY
}

// Helper para imágenes (sin cambios)
fun encodeImageToBase64(imagePath: String): String {
    val bitmap = BitmapFactory.decodeFile(imagePath) ?: throw IllegalArgumentException("No se pudo cargar la imagen")
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}*/
