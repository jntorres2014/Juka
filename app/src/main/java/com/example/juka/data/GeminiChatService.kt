import android.util.Log
import com.example.juka.data.GeminiPescaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiChatService {
    private val geminiService = GeminiPescaService()

    companion object {
        private const val TAG = "GeminiChatService"
    }

    suspend fun processUserMessage(
        message: String,
        messageType: MessageType = MessageType.TEXT
    ): ChatResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Procesando mensaje: $message")

            // Llamar a Gemini
            val response = geminiService.obtenerConsejoPesca(
                pregunta = message,
                contexto = null // Por ahora sin contexto
            )

            ChatResult.Success(response)

        } catch (e: Exception) {
            Log.e(TAG, "Error en Gemini: ${e.message}")
            ChatResult.Error(
                getErrorMessage(e),
                shouldConsumeQuota = false
            )
        }
    }

    suspend fun processAudioMessage(transcript: String): ChatResult {
        return processUserMessage(transcript, MessageType.AUDIO)
    }

    private fun getErrorMessage(error: Exception): String {
        return when {
            error.message?.contains("network", ignoreCase = true) == true -> {
                """
                    📵 **Sin conexión**
                    
                    No pude conectarme a internet.
                    Verificá tu conexión e intentá de nuevo.
                    
                    Esta consulta no se descontó.
                """.trimIndent()
            }
            error.message?.contains("timeout", ignoreCase = true) == true -> {
                """
                    ⏱️ **Tiempo agotado**
                    
                    La respuesta tardó demasiado.
                    Intentá de nuevo en unos segundos.
                    
                    Esta consulta no se descontó.
                """.trimIndent()
            }
            else -> {
                """
                    ❌ **Error inesperado**
                    
                    Algo salió mal al procesar tu consulta.
                    Por favor, intentá de nuevo.
                    
                    Esta consulta no se descontó.
                """.trimIndent()
            }
        }
    }
}

sealed class ChatResult {
    data class Success(
        val message: String
    ) : ChatResult()

    data class Error(
        val message: String,
        val shouldConsumeQuota: Boolean = false
    ) : ChatResult()
}

enum class MessageType {
    TEXT, AUDIO, IMAGE
}