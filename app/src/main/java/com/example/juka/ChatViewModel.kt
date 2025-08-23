// ChatViewModel.kt - Versión actualizada con audio robusto
package com.example.juka

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.huka.FishDatabase
import com.example.huka.FishIdentifier
import com.example.huka.FishingStoryAnalyzer
import com.example.huka.IntelligentResponses
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

    // Archivos para persistencia
    private val chatFile = File(getApplication<Application>().filesDir, "fishing_chat_history.txt")
    private val conversationLogFile = File(getApplication<Application>().filesDir, "conversation_log.txt")
    private val speciesLogFile = File(getApplication<Application>().filesDir, "identified_species.txt")

    // Instancias de las clases auxiliares
    private val fishDatabase = FishDatabase()
    private val intelligentResponses = IntelligentResponses(fishDatabase)
    private val fishIdentifier = FishIdentifier(getApplication())
    private val storyAnalyzer = FishingStoryAnalyzer(getApplication())
    private val dataExtractor = FishingDataExtractor(getApplication())

    init {
        loadMessagesFromFile()
        addWelcomeMessage()
    }

    private fun addWelcomeMessage() {
        if (_messages.value.isEmpty()) {
            val welcomeMessage = ChatMessage(
                content = "¡Hola pescador! 🎣 Soy Juka, tu asistente de pesca inteligente.\n\n🧠 " +
                        "**Funciones:**\n" +
                        "• Consejos sobre especies argentinas\n" +
                        "• Análisis de técnicas y carnadas\n" +
                        "• 📸 **Registro de fotos para el reporte**\n" +
                        "• 📊 **Análisis automático de relatos de pesca**\n" +
                        "• 📝 **Registro de datos de pesca** (día, horas, piezas, etc.)\n\n" +
                        "¡Contame sobre tus jornadas, subí fotos o grabá un audio! 🐟",
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
                // Analizar relato de pesca
                val storyAnalysis = storyAnalyzer.analyzeStory(content)
                val analysisResponse = storyAnalyzer.buildAnalysisResponse(storyAnalysis)
                val contextualResponse = intelligentResponses.getStoryResponse(storyAnalysis)
                "$analysisResponse\n\n$contextualResponse"
            } else {
                // Respuesta normal inteligente
                intelligentResponses.getResponse(content)
            }

            // Agregar preguntas por datos faltantes
            val finalResponse = if (missingFields.isNotEmpty()) {
                "$response\n\nPara completar el registro: ${missingFields.joinToString(" ")}"
            } else {
                "$response\n\n¡Datos completos! Registrado: Día ${extractedData.day}, " +
                        "Inicio ${extractedData.startTime}, Fin ${extractedData.endTime}, " +
                        "${extractedData.fishCount} piezas, Tipo ${extractedData.type}, " +
                        "${extractedData.rodsCount} cañas, Foto ${extractedData.photoUri ?: "ninguna"}"
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

            // Resetear sesión si datos completos
            if (missingFields.isEmpty()) {
                dataExtractor.resetSession()
            }
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

        // Solo extraer datos de pesca (foto para reporte)
        val extractedData = dataExtractor.extractFromMessage("", imagePath)
        val missingFields = dataExtractor.getMissingFields(extractedData)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(1500, 2500))

            // Respuesta simple para foto (sin identificación de especies)
            var response = "📸 ¡Excelente foto! Agregada al reporte de pesca."

            if (missingFields.isNotEmpty()) {
                response += "\n\nPara completar el registro: ${missingFields.joinToString(" ")}"
            } else {
                response += "\n\n¡Datos completos! Registrado: Día ${extractedData.day ?: "no especificado"}, " +
                        "Inicio ${extractedData.startTime ?: "no especificado"}, Fin ${extractedData.endTime ?: "no especificado"}, " +
                        "${extractedData.fishCount ?: 0} piezas, Tipo ${extractedData.type ?: "no especificado"}, " +
                        "${extractedData.rodsCount ?: 0} cañas, Foto incluida ✅"
                dataExtractor.resetSession()
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

    // 🎤 NUEVO MÉTODO PARA AUDIO ROBUSTO
    fun sendAudioTranscript(transcript: String) {
        // Crear mensaje del usuario con el audio transcrito
        val userMessage = ChatMessage(
            content = "🎤 Audio: \"$transcript\"",
            isFromUser = true,
            type = MessageType.AUDIO,
            timestamp = getCurrentTimestamp()
        )

        addMessage(userMessage)
        saveMessageToFile(userMessage, "AUDIO_TRANSCRIPT: $transcript")
        saveConversationLog("USER_AUDIO", transcript)

        // Extraer datos de pesca del texto transcrito
        val extractedData = dataExtractor.extractFromMessage(transcript)
        val missingFields = dataExtractor.getMissingFields(extractedData)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(1000, 2500))

            val baseResponse = intelligentResponses.getAudioResponse()
            val response = if (missingFields.isNotEmpty()) {
                "$baseResponse\n\nEntendí: \"$transcript\"\n\nPara completar el registro: ${missingFields.joinToString(" ")}"
            } else {
                "$baseResponse\n\nEntendí: \"$transcript\"\n\n¡Datos completos! Registrado: Día ${extractedData.day}, " +
                        "Inicio ${extractedData.startTime}, Fin ${extractedData.endTime}, " +
                        "${extractedData.fishCount} piezas, Tipo ${extractedData.type}, " +
                        "${extractedData.rodsCount} cañas, Foto ${extractedData.photoUri ?: "ninguna"}"
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
            saveConversationLog("BOT_AUDIO", response)

            // Resetear sesión si datos completos
            if (missingFields.isEmpty()) {
                dataExtractor.resetSession()
            }
        }
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

            "📊 Total: $totalMessages | Usuario: $userMessages | Bot: $botMessages | Audio: $audioMessages | IA: Local"
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
                                        ChatMessage("🎤 Audio: \"$transcript\"", true, MessageType.AUDIO, timestamp)
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