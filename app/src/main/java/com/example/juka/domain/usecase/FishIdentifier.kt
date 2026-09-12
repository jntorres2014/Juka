package com.example.juka.domain.usecase

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

enum class FishIdentifierModel {
    GEMINI,
    FISHIAL,
    MODELO_PROPIO
}

enum class ModoIdentificacion {
    ESTANDAR,
    PREMIUM
}

data class FishialResult(
    @SerializedName("name") val name: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("confidence_pct") val confidencePct: String
)

data class PezArgentino(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("cientifico") val cientifico: String,
    @SerializedName("habitat") val habitat: String?,
    @SerializedName("carnadas") val carnadas: List<String>?,
    @SerializedName("mejor_horario") val mejorHorario: String?,
    @SerializedName("tecnica") val tecnica: String?,
    @SerializedName("tamaño") val tamanio: String?,
    @SerializedName("temporada") val temporada: String?
)

data class FishialResponse(
    @SerializedName("is_fish") val isFish: Boolean,
    @SerializedName("results") val results: List<FishialResult>?,
    @SerializedName("message") val message: String?,
    @SerializedName("inference_time_ms") val inferenceTimeMs: Int?
)

class FishIdentifier(private val application: Application) {

    companion object {
        private const val FISHIAL_API_URL = "https://jntorres2014-identifier-fish-api.hf.space/identify"
        private const val TAG = "FishIdentifier"
    }

    private val generativeModel = Firebase
        .ai(backend = GenerativeBackend.googleAI())
        .generativeModel("gemini-3.5-flash")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val pecesArgentinos: Map<String, PezArgentino> by lazy {
        try {
            val json = application.assets.open("peces_argentinos.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<PezArgentino>>() {}.type
            val lista: List<PezArgentino> = gson.fromJson(json, type)
            lista.associateBy { it.cientifico.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando peces_argentinos.json: ${e.javaClass.simpleName}")
            emptyMap()
        }
    }

    private val clasesModeloPropio = listOf(
        "bagre", "carpa", "pejerrey_patagonico", "trucha_arcoiris", "trucha_marron"
    )

    suspend fun identifyFish(
        imagePath: String,
        model: FishIdentifierModel = FishIdentifierModel.GEMINI
    ): String = when (model) {
        FishIdentifierModel.GEMINI -> identifyWithGemini(imagePath)
        FishIdentifierModel.FISHIAL -> identifyWithFishial(imagePath)
        FishIdentifierModel.MODELO_PROPIO -> identifyWithModeloPropio(imagePath)
    }

    suspend fun identifyPremium(imagePath: String): String = identifyWithGemini(imagePath)

    suspend fun identifyEstandar(imagePath: String, hayConexion: Boolean): String {
        if (!hayConexion) {
            return identifyWithModeloPropio(imagePath) +
                "\n\n_📡 Sin conexión — usamos el modelo local de reconocimiento offline._"
        }

        val resultado = identifyWithFishial(imagePath)
        val falloConexion = resultado.startsWith("⚠️ Error al conectar") ||
            resultado.startsWith("⚠️ Error al analizar") ||
            resultado.startsWith("⚠️ Respuesta vacía")

        return if (falloConexion) {
            identifyWithModeloPropio(imagePath) +
                "\n\n_📡 No pudimos conectar con el servidor de identificación — usamos el modelo local como respaldo._"
        } else {
            resultado
        }
    }

    private suspend fun identifyWithGemini(imagePath: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando análisis premium con Firebase AI")

            val bitmap = decodeBitmapFromFile(imagePath)
                ?: return@withContext "❌ Error: No se pudo leer el archivo de imagen."

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

                IMPORTANTE: al final de todo, en una línea aparte y EXACTA, listá
                TODOS los peces que se ven en la foto con su cantidad, con este
                formato (sin texto adicional, separados por coma):
                PECES_DETECTADOS: 2 Pejerrey, 1 Pacú, 1 Tiburón
                Si la imagen no es un pez, poné exactamente:
                PECES_DETECTADOS: ninguno
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            val response = generativeModel.generateContent(inputContent)
            response.text ?: "La IA no devolvió texto."
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Error desconocido"
            Log.e(TAG, "Error Firebase AI: ${e.javaClass.simpleName}")

            when {
                errorMsg.contains("503") ||
                    errorMsg.contains("UNAVAILABLE", true) ||
                    errorMsg.contains("high demand", true) ||
                    errorMsg.contains("overloaded", true) ->
                    "⏳ Gemini está muy ocupado ahora mismo. Esperá unos segundos y volvé a intentar, o usá el modelo Fishial."

                errorMsg.contains("404") || errorMsg.contains("not found", true) ->
                    "⚠️ Firebase AI Logic todavía no está habilitado correctamente para este proyecto."

                errorMsg.contains("401") ||
                    errorMsg.contains("403") ||
                    errorMsg.contains("App Check", true) ||
                    errorMsg.contains("permission", true) ->
                    "🔑 Firebase AI Logic rechazó la autorización de esta instalación."

                else ->
                    "⚠️ Ocurrió un error al consultar Gemini:\n\n$errorMsg"
            }
        }
    }

    private suspend fun identifyWithFishial(imagePath: String): String = withContext(Dispatchers.IO) {
        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                return@withContext "❌ No se pudo leer el archivo de imagen."
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    imageFile.name,
                    imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(FISHIAL_API_URL)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Error HTTP Fishial: ${response.code}")
                return@withContext "⚠️ Error al conectar con el servidor (${response.code})."
            }

            val body = response.body?.string()
                ?: return@withContext "⚠️ Respuesta vacía del servidor."

            val result = gson.fromJson(body, FishialResponse::class.java)

            if (!result.isFish) {
                return@withContext result.message ?: "🤔 Eso no parece un pez..."
            }

            val results = result.results
                ?: return@withContext "⚠️ No se pudo identificar la especie."

            formatearRespuestaFishial(results, result.inferenceTimeMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error Fishial: ${e.javaClass.simpleName}")
            "⚠️ Error al analizar la imagen:\n${e.localizedMessage}"
        }
    }

    private suspend fun identifyWithModeloPropio(imagePath: String): String = withContext(Dispatchers.IO) {
        try {
            val assetFd = application.assets.openFd("modelo_nuevo.tflite")
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val modelBuffer = inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )
            inputStream.close()

            val interpreter = Interpreter(modelBuffer)
            val bitmap = decodeBitmapFromFile(imagePath)
            if (bitmap == null) {
                interpreter.close()
                return@withContext "❌ No se pudo leer la imagen."
            }

            val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }

            for (y in 0 until 224) {
                for (x in 0 until 224) {
                    val pixel = scaled.getPixel(x, y)
                    inputBuffer.putFloat((pixel shr 16 and 0xFF).toFloat())
                    inputBuffer.putFloat((pixel shr 8 and 0xFF).toFloat())
                    inputBuffer.putFloat((pixel and 0xFF).toFloat())
                }
            }
            inputBuffer.rewind()

            val output = Array(1) { FloatArray(clasesModeloPropio.size) }
            interpreter.run(inputBuffer, output)
            interpreter.close()

            val scores = output[0]
            val topIndices = scores.indices.sortedByDescending { scores[it] }
            val best = topIndices[0]
            val bestScore = scores[best]

            if (bestScore < 0.30f) {
                return@withContext "🤔 No estoy seguro de qué pez es (confianza: ${(bestScore * 100).toInt()}%).\n" +
                    "_Este modelo reconoce: ${clasesModeloPropio.joinToString(", ")}_"
            }

            val nombreCientifico = when (clasesModeloPropio[best]) {
                "bagre" -> "Pimelodus maculatus"
                "carpa" -> "Cyprinus carpio"
                "pejerrey_patagonico" -> "Odontesthes hatcheri"
                "trucha_arcoiris" -> "Oncorhynchus mykiss"
                "trucha_marron" -> "Salmo trutta"
                else -> clasesModeloPropio[best]
            }

            val pezLocal = pecesArgentinos[nombreCientifico.lowercase()]
            val sb = StringBuilder()

            if (pezLocal != null) {
                sb.appendLine("🐟 **${pezLocal.nombre}**")
                sb.appendLine("_${nombreCientifico}_ — Confianza: ${(bestScore * 100).toInt()}%")
                sb.appendLine()
                pezLocal.habitat?.let { sb.appendLine("📍 **Hábitat:** $it") }
                pezLocal.tecnica?.let { sb.appendLine("🎣 **Técnica:** $it") }
                pezLocal.carnadas?.let { sb.appendLine("🪱 **Carnadas:** ${it.joinToString(", ")}") }
                pezLocal.temporada?.let { sb.appendLine("📅 **Temporada:** $it") }
            } else {
                val nombre = clasesModeloPropio[best]
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                sb.appendLine("🐟 **$nombre**")
                sb.appendLine("Confianza: ${(bestScore * 100).toInt()}%")
            }

            if (topIndices.size > 1) {
                sb.appendLine()
                sb.appendLine("📋 **Otras posibilidades:**")
                topIndices.drop(1).take(2).forEach { i ->
                    val nombre = clasesModeloPropio[i]
                        .replace("_", " ")
                        .replaceFirstChar { it.uppercase() }
                    sb.appendLine("• $nombre — ${(scores[i] * 100).toInt()}%")
                }
            }

            sb.appendLine()
            sb.appendLine("_Modelo local · Sin conexión a internet_")
            sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error modelo local: ${e.javaClass.simpleName}")
            "⚠️ Error al ejecutar el modelo local:\n${e.localizedMessage}"
        }
    }

    private fun formatearRespuestaFishial(results: List<FishialResult>, timeMs: Int?): String {
        val top = results.first()
        val sb = StringBuilder()
        val pezLocal = pecesArgentinos[top.name.lowercase()]

        if (pezLocal != null) {
            sb.appendLine("🐟 **${pezLocal.nombre}**")
            sb.appendLine("_${top.name}_ — Confianza: ${top.confidencePct}")
            sb.appendLine()
            pezLocal.habitat?.let { sb.appendLine("📍 **Hábitat:** $it") }
            pezLocal.tecnica?.let { sb.appendLine("🎣 **Técnica:** $it") }
            pezLocal.carnadas?.let { sb.appendLine("🪱 **Carnadas:** ${it.joinToString(", ")}") }
            pezLocal.mejorHorario?.let { sb.appendLine("🕐 **Mejor horario:** $it") }
            pezLocal.temporada?.let { sb.appendLine("📅 **Temporada:** $it") }
            pezLocal.tamanio?.let { sb.appendLine("📏 **Tamaño típico:** $it") }
        } else {
            sb.appendLine("🐟 **Especie identificada**")
            sb.appendLine("**${top.name}**")
            sb.appendLine("Confianza: ${top.confidencePct}")
        }

        if (results.size > 1) {
            sb.appendLine()
            sb.appendLine("📋 **Otras posibilidades:**")
            results.drop(1).forEach { r ->
                val local = pecesArgentinos[r.name.lowercase()]
                val nombre = if (local != null) "${local.nombre} (${r.name})" else r.name
                sb.appendLine("• $nombre — ${r.confidencePct}")
            }
        }

        timeMs?.let {
            sb.appendLine()
            sb.appendLine("_Análisis completado en ${it}ms_")
        }

        return sb.toString().trim()
    }

    private fun decodeBitmapFromFile(path: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, 800, 800)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo decodificar imagen: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (
                halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}