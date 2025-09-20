// ChatViewModel.kt - Versión corregida con Firebase integrado
package com.example.juka

import android.R.attr.content
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // Firebase integration
    private val _firebaseStatus = MutableStateFlow<String?>(null)
    val firebaseStatus: StateFlow<String?> = _firebaseStatus.asStateFlow()

    // Archivos para persistencia local (backup)
    private val chatFile = File(getApplication<Application>().filesDir, "fishing_chat_history.txt")
    private val conversationLogFile = File(getApplication<Application>().filesDir, "conversation_log.txt")

    // Instancias de las clases auxiliares
    private val fishDatabase = FishDatabase(getApplication())
    private val intelligentResponses = IntelligentResponses(fishDatabase)
    private val fishIdentifier = FishIdentifier(getApplication())
    private val storyAnalyzer = FishingStoryAnalyzer(getApplication())
    private val dataExtractor = FishingDataExtractor(getApplication())

    // Firebase Manager
    private val firebaseManager = FirebaseManager(getApplication())
    val openAiService = OpenAiApiService()

    init {
        // ✅ INICIALIZAR BASE DE DATOS PRIMERO
        viewModelScope.launch {
            try {
                //val response = xaiApiService.generateResponse("Hola, prueba de IA")
                //Log.d("XAI_TEST", response)
                val prompt = "Eres Juka, asistente de pesca en español. Responde amigablemente a: '$content'. Usa emojis."
                val aiResponse = openAiService.generateResponse(prompt)
                Log.d("Respuesta", aiResponse)
                fishDatabase.initialize()
                android.util.Log.i("ChatViewModel", "✅ Base de datos de peces inicializada")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "❌ Error inicializando base de datos: ${e.message}")
            }
        }

        loadMessagesFromFile()
        addWelcomeMessage()
    }

    private fun addWelcomeMessage() {
        if (_messages.value.isEmpty()) {
            val welcomeMessage = ChatMessage(
                content = "Hola pescador! Soy Juka, tu asistente de pesca inteligente.\n\n" +
                        "**Funciones:**\n" +
                        "• Consejos sobre especies argentinas\n" +
                        "• Análisis de técnicas y carnadas\n" +
                        "• Registro automático en Firebase\n" +
                        "• Análisis automático de relatos de pesca\n" +
                        "• Registro de datos de pesca\n\n" +
                        "Contame sobre tus jornadas, subí fotos o grabá un audio!",
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )
            addMessage(welcomeMessage)
        }
    }

    fun sendTextMessage(content: String) {
        val userMessage = ChatMessage(
            content = content,
            isFromUser = true,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp()
        )

        addMessage(userMessage)
        saveMessageToFile(userMessage)
        saveConversationLog("USER", content)

        // Extraer datos de pesca
        val extractedData = dataExtractor.extractFromMessage(content)
        val missingFields = dataExtractor.getMissingFields(extractedData)

        // Detectar si es un relato de pesca
        val isLikelyStory = isLikelyFishingStory(content)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(1000, 3000))

            val response = if (isLikelyStory) {
                val storyAnalysis = storyAnalyzer.analyzeStory(content)
                val analysisResponse = storyAnalyzer.buildAnalysisResponse(storyAnalysis)
                val contextualResponse = intelligentResponses.getStoryResponse(storyAnalysis)
                "$analysisResponse\n\n$contextualResponse"
            } else {
                intelligentResponses.getResponse(content)
            }

            // Verificar si el parte está completo y guardarlo
            var finalResponse = if (missingFields.isEmpty() && extractedData.fishCount != null && extractedData.fishCount!! > 0) {
                // Parte completo - intentar guardar en Firebase
                _firebaseStatus.value = "Guardando en Firebase..."

                val firebaseResult = firebaseManager.guardarParteAutomatico(extractedData, content)

                when (firebaseResult) {
                    is FirebaseResult.Success -> {
                        _firebaseStatus.value = "Guardado en Firebase"
                        dataExtractor.resetSession()
                        "$response\n\n✅ **Parte completo guardado automáticamente en Firebase!**\n" +
                                "📊 Datos: Día ${extractedData.day}, ${extractedData.fishCount} peces, " +
                                "Tipo ${extractedData.type}, ${extractedData.rodsCount} cañas"
                    }
                    is FirebaseResult.Error -> {
                        _firebaseStatus.value = "Error Firebase"
                        "$response\n\n⚠️ **Datos completos localmente, pero error guardando en Firebase:**\n" +
                                "${firebaseResult.message}\n\n📊 Datos locales: ${extractedData.fishCount} peces, ${extractedData.type}"
                    }
                    else -> response
                }
            } else if (missingFields.isNotEmpty()) {
                "$response\n\n📝 **Para completar el registro:** ${missingFields.joinToString(" ")}"
            } else {
                response
            }

            // Limpiar estado Firebase después de 3 segundos
            if (_firebaseStatus.value != null) {
                delay(3000)
                _firebaseStatus.value = null
            }

            val botMessage = ChatMessage(
                content = finalResponse,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _isTyping.value = false
            addMessage(botMessage)
            saveMessageToFile(botMessage)
            saveConversationLog("BOT_SMART", finalResponse)
        }
    }

    fun sendImageMessage(imagePath: String) {
        val userMessage = ChatMessage(
            content = imagePath,
            isFromUser = true,
            type = MessageType.IMAGE,
            timestamp = getCurrentTimestamp()
        )

        addMessage(userMessage)
        saveMessageToFile(userMessage, "IMAGE: $imagePath")
        saveConversationLog("USER_IMAGE", imagePath)

        // OBTENER CONTEXTO DE MENSAJES ANTERIORES PARA ESPECIES
        val contextoPrevio = obtenerContextoPescaReciente()

        // Extraer datos usando el contexto si existe
        val extractedData = if (contextoPrevio.isNotBlank()) {
            // Si hay contexto previo con especies, usarlo
            val dataFromContext = dataExtractor.extractFromMessage(contextoPrevio)
            // Agregar la foto al resultado
            dataFromContext.copy(photoUri = imagePath)
        } else {
            // Si no hay contexto, crear datos básicos con la foto
            dataExtractor.extractFromMessage("").copy(photoUri = imagePath)
        }

        val missingFields = dataExtractor.getMissingFields(extractedData)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(1500, 2500))

            var response = if (contextoPrevio.isNotBlank()) {
                "📸 Excelente foto! La agregué a tu reporte de pesca."
            } else {
                "📸 Foto recibida! Para incluirla en un reporte, contame qué pescaste."
            }

            // Solo guardar si hay datos completos
            if (missingFields.isEmpty() && extractedData.fishCount != null && extractedData.fishCount!! > 0) {
                _firebaseStatus.value = "Guardando en Firebase..."

                // Usar el contexto previo como transcripción para análisis de especies
                val transcripcionParaAnalisis = if (contextoPrevio.isNotBlank()) {
                    "$contextoPrevio (con foto adjunta)"
                } else {
                    "Foto de pesca sin especificar especies"
                }

                val firebaseResult = firebaseManager.guardarParteAutomatico(extractedData, transcripcionParaAnalisis)

                response += when (firebaseResult) {
                    is FirebaseResult.Success -> {
                        _firebaseStatus.value = "Guardado en Firebase"
                        dataExtractor.resetSession()
                        "\n\n✅ **Reporte con foto guardado en Firebase!**\n📊 ${extractedData.fishCount} peces registrados con imagen"
                    }
                    is FirebaseResult.Error -> {
                        _firebaseStatus.value = "Error Firebase"
                        "\n\n⚠️ **Error guardando en Firebase:** ${firebaseResult.message}"
                    }
                    else -> ""
                }
            } else {
                if (extractedData.fishCount == null || extractedData.fishCount!! == 0) {
                    response += "\n\n💡 **Tip:** Contame qué especies pescaste para crear un reporte completo con tu foto."
                } else if (missingFields.isNotEmpty()) {
                    response += "\n\n📝 Para completar el reporte con foto: ${missingFields.joinToString(" ")}"
                }
            }

            if (_firebaseStatus.value != null) {
                delay(3000)
                _firebaseStatus.value = null
            }

            val botMessage = ChatMessage(
                content = response,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _isTyping.value = false
            addMessage(botMessage)
            saveMessageToFile(botMessage)
            saveConversationLog("BOT_IMAGE", response)
        }
    }

    // NUEVA FUNCIÓN AUXILIAR - Agregar al ChatViewModel
    private fun obtenerContextoPescaReciente(): String {
        // Buscar en los últimos 5 mensajes del usuario por especies o datos de pesca
        val mensajesRecientes = _messages.value
            .filter { it.isFromUser && it.type != MessageType.IMAGE }
            .takeLast(5)
            .map { it.content }
            .joinToString(" ")

        // Buscar indicadores de especies en el contexto
        val especiesIndicadores = listOf(
            "dorado", "dorados", "surubí", "surubís", "pacú", "pacús",
            "pejerrey", "pejerreyes", "tararira", "bagre", "corvina"
        )

        return if (especiesIndicadores.any { mensajesRecientes.lowercase().contains(it) }) {
            mensajesRecientes
        } else {
            ""
        }
    }

    fun sendAudioTranscript(transcript: String) {
        val userMessage = ChatMessage(
            content = "🎤 \"$transcript\"",
            isFromUser = true,
            type = MessageType.AUDIO,
            timestamp = getCurrentTimestamp()
        )

        addMessage(userMessage)
        saveMessageToFile(userMessage, "AUDIO_TRANSCRIPT: $transcript")
        saveConversationLog("USER_AUDIO", transcript)

        val extractedData = dataExtractor.extractFromMessage(transcript)
        val missingFields = dataExtractor.getMissingFields(extractedData)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(1000, 2500))

            val confirmationResponse = "👂 Perfecto, entendí: \"$transcript\""
            val baseResponse = intelligentResponses.getAudioResponse()

            var finalResponse = if (missingFields.isEmpty() && extractedData.fishCount != null && extractedData.fishCount!! > 0) {
                _firebaseStatus.value = "Guardando en Firebase..."

                val firebaseResult = firebaseManager.guardarParteAutomatico(extractedData, transcript)

                when (firebaseResult) {
                    is FirebaseResult.Success -> {
                        _firebaseStatus.value = "Guardado en Firebase"
                        dataExtractor.resetSession()
                        "$confirmationResponse\n\n$baseResponse\n\n🔥 **Audio procesado y parte guardado en Firebase!**\n" +
                                "📊 Registrado: ${extractedData.fishCount} peces, tipo ${extractedData.type}"
                    }
                    is FirebaseResult.Error -> {
                        _firebaseStatus.value = "Error Firebase"
                        "$confirmationResponse\n\n$baseResponse\n\n⚠️ **Error guardando en Firebase:** ${firebaseResult.message}"
                    }
                    else -> "$confirmationResponse\n\n$baseResponse"
                }
            } else if (missingFields.isNotEmpty()) {
                "$confirmationResponse\n\n$baseResponse\n\nPara completar: ${missingFields.joinToString(" ")}"
            } else {
                "$confirmationResponse\n\n$baseResponse"
            }

            if (_firebaseStatus.value != null) {
                delay(3000)
                _firebaseStatus.value = null
            }

            val botMessage = ChatMessage(
                content = finalResponse,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _isTyping.value = false
            addMessage(botMessage)
            saveMessageToFile(botMessage)
            saveConversationLog("BOT_AUDIO", finalResponse)
        }
    }

    // Función para obtener estadísticas Firebase
    suspend fun obtenerEstadisticasFirebase(): Map<String, Any> {
        return firebaseManager.obtenerEstadisticas()
    }

    // Función para obtener mis partes desde Firebase
    suspend fun obtenerMisPartes(): List<PartePesca> {
        return firebaseManager.obtenerMisPartes()
    }

    private fun isLikelyFishingStory(text: String): Boolean {
        val storyIndicators = listOf(
            "pesqu", "saq", "captur", "jornada", "salida", "día de pesca",
            "fuimos", "estuve", "nos fuimos", "salimos", "ayer", "hoy",
            "mañana", "tarde", "dorado", "surubí", "pacú", "pejerrey"
        )

        val indicatorCount = storyIndicators.count {
            text.lowercase().contains(it)
        }

        return text.length > 50 && indicatorCount >= 2
    }

    fun getConversationStats(): String {
        return try {
            val totalMessages = _messages.value.size
            val userMessages = _messages.value.count { it.isFromUser }
            val botMessages = totalMessages - userMessages
            val audioMessages = _messages.value.count { it.type == MessageType.AUDIO }

            "📊 Total: $totalMessages | Usuario: $userMessages | Bot: $botMessages | Audio: $audioMessages | Firebase: Activo"
        } catch (e: Exception) {
            "📊 Estadísticas no disponibles"
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun saveMessageToFile(message: ChatMessage, customContent: String? = null) {
        try {
            val messageText = "${message.timestamp} - ${if (message.isFromUser) "USER" else "BOT"}: ${customContent ?: message.content}\n"
            chatFile.appendText(messageText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveConversationLog(sender: String, content: String) {
        try {
            val timestamp = getCurrentTimestamp()
            val logText = "$timestamp - $sender: $content\n"
            conversationLogFile.appendText(logText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMessagesFromFile() {
        try {
            if (chatFile.exists()) {
                val lines = chatFile.readLines().takeLast(50)
                val loadedMessages = mutableListOf<ChatMessage>()

                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split(" - ", limit = 2)
                        if (parts.size == 2) {
                            val timestamp = parts[0]
                            val content = parts[1]

                            when {
                                content.startsWith("USER: AUDIO_TRANSCRIPT:") -> {
                                    val transcript = content.removePrefix("USER: AUDIO_TRANSCRIPT: ")
                                    loadedMessages.add(
                                        ChatMessage("🎤 \"$transcript\"", true, MessageType.AUDIO, timestamp)
                                    )
                                }
                                content.startsWith("USER: IMAGE:") -> {
                                    val imagePath = content.removePrefix("USER: IMAGE: ")
                                    loadedMessages.add(
                                        ChatMessage(imagePath, true, MessageType.IMAGE, timestamp)
                                    )
                                }
                                content.startsWith("USER: ") -> {
                                    loadedMessages.add(
                                        ChatMessage(content.removePrefix("USER: "), true, MessageType.TEXT, timestamp)
                                    )
                                }
                                content.startsWith("BOT: ") -> {
                                    loadedMessages.add(
                                        ChatMessage(content.removePrefix("BOT: "), false, MessageType.TEXT, timestamp)
                                    )
                                }
                            }
                        }
                    }
                }

                _messages.value = loadedMessages
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        try {
            if (chatFile.exists()) chatFile.delete()
            if (conversationLogFile.exists()) conversationLogFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        addWelcomeMessage()
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }
}

// Data classes (sin cambios)
data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val type: MessageType,
    val timestamp: String
)

enum class MessageType {
    TEXT, AUDIO, IMAGE
}