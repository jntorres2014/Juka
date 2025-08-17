// ChatViewModel.kt - Archivo principal
package com.example.juka

import android.app.Application
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

    // Archivos para persistencia
    private val chatFile = File(getApplication<Application>().filesDir, "fishing_chat_history.txt")
    private val conversationLogFile = File(getApplication<Application>().filesDir, "conversation_log.txt")
    private val speciesLogFile = File(getApplication<Application>().filesDir, "identified_species.txt")

    // Instancias de las clases auxiliares
    private val fishDatabase = FishDatabase()
    private val intelligentResponses = IntelligentResponses(fishDatabase)
    private val fishIdentifier = FishIdentifier(getApplication())
    private val storyAnalyzer = FishingStoryAnalyzer(getApplication()) // 🔥 NUEVO

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
                        "• 📸 **Identificación de peces con IA**\n" +
                        "• 📊 **Análisis automático de relatos de pesca**\n\n" +
                        "Contame sobre tus jornadas o subí fotos para análisis completo! 🐟",
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

        // 🔥 NUEVO: Detectar si es un relato de pesca
        val isLikelyStory = isLikelyFishingStory(content)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(1000, 3000))

            val response = if (isLikelyStory) {
                // Analizar relato de pesca
                val storyAnalysis = storyAnalyzer.analyzeStory(content)
                val analysisResponse = storyAnalyzer.buildAnalysisResponse(storyAnalysis)

                // Combinar análisis con respuesta inteligente
                val contextualResponse = intelligentResponses.getStoryResponse(storyAnalysis)
                "$analysisResponse\n\n$contextualResponse"
            } else {
                // Respuesta normal inteligente
                intelligentResponses.getResponse(content)
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
            saveConversationLog("BOT_SMART", response)
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

        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                // Identificación real con iNaturalist
                val identification = fishIdentifier.identifyFish(imagePath)

                val analysisMessage = ChatMessage(
                    content = identification,
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp()
                )

                _isAnalyzing.value = false
                addMessage(analysisMessage)
                saveMessageToFile(analysisMessage)
                saveConversationLog("BOT_FISH_ID", identification)

                // Pregunta de seguimiento
                delay(2000)
                val followUpMessage = ChatMessage(
                    content = intelligentResponses.getFollowUpQuestion(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp()
                )
                addMessage(followUpMessage)
                saveMessageToFile(followUpMessage)

            } catch (e: Exception) {
                _isAnalyzing.value = false
                val fallbackAnalysis = intelligentResponses.getImageAnalysisFallback()

                val fallbackMessage = ChatMessage(
                    content = "$fallbackAnalysis\n\n⚠️ *Identificación automática no disponible - análisis visual local*",
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp()
                )

                addMessage(fallbackMessage)
                saveMessageToFile(fallbackMessage)
                saveConversationLog("BOT_FALLBACK_IMAGE", fallbackAnalysis)
            }
        }
    }

    fun sendAudioMessage(audioPath: String) {
        val userMessage = ChatMessage(
            content = audioPath,
            isFromUser = true,
            type = MessageType.AUDIO,
            timestamp = getCurrentTimestamp()
        )

        addMessage(userMessage)
        saveMessageToFile(userMessage, "AUDIO: $audioPath")
        saveConversationLog("USER_AUDIO", audioPath)

        _isTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(2000, 4000))

            val botMessage = ChatMessage(
                content = intelligentResponses.getAudioResponse(),
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _isTyping.value = false
            addMessage(botMessage)
            saveMessageToFile(botMessage)
            saveConversationLog("BOT_AUDIO", botMessage.content)
        }
    }

    private fun saveConversationLog(sender: String, content: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp | $sender: $content\n"
            conversationLogFile.appendText(logEntry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getConversationStats(): String {
        return try {
            val totalMessages = _messages.value.size
            val userMessages = _messages.value.count { it.isFromUser }
            val botMessages = totalMessages - userMessages
            val identifiedSpecies = if (speciesLogFile.exists()) {
                speciesLogFile.readLines().size
            } else 0

            "📊 Total: $totalMessages | Usuario: $userMessages | Bot: $botMessages | Especies ID: $identifiedSpecies | IA: Local + iNaturalist"
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
                                content.startsWith("USER: AUDIO:") -> {
                                    val audioPath = content.removePrefix("USER: AUDIO: ")
                                    loadedMessages.add(
                                        ChatMessage(audioPath, true, MessageType.AUDIO, timestamp)
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

// Data classes
data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val type: MessageType,
    val timestamp: String
)

enum class MessageType {
    TEXT, AUDIO, IMAGE
}