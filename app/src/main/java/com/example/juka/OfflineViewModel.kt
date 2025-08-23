/*
package com.example.huka
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

class EnhancedChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _botTyping = MutableStateFlow(false)
    val botTyping: StateFlow<Boolean> = _botTyping.asStateFlow()

    private val chatFile = File(getApplication<Application>().filesDir, "chat_history.txt")
    private val offlineQueueFile = File(getApplication<Application>().filesDir, "offline_queue.txt")

    // Respuestas más inteligentes y contextualización offline
    private val greetingResponses = listOf(
        "¡Hola! 👋 Aunque estés sin internet, estoy aquí para charlar contigo.",
        "¡Saludos! 🌟 No necesitamos internet para tener una buena conversación.",
        "¡Hola! Perfecto momento para una charla offline. ¿Cómo estás?",
        "¡Hey! Internet o no, siempre es buen momento para conversar. 😊"
    )

    private val offlineResponses = listOf(
        "Aunque estés sin conexión, puedo seguir conversando contigo. Todo se guarda localmente.",
        "No hay problema con la falta de internet. Soy tu compañero de chat offline. 📱",
        "Perfecto para una conversación íntima sin distracciones de internet.",
        "Sin conexión, sin interrupciones. Solo tú y yo charlando. 😌",
        "Los mejores chats a veces son los que no necesitan internet.",
        "Modo offline activado. Conversación 100% privada y local."
    )

    private val contextualResponses = mapOf(
        // Respuestas por hora del día
        "morning" to listOf(
            "¡Buenos días! ☀️ Espero que tengas un día fantástico.",
            "¡Buen día! ¿Cómo amaneciste hoy?",
            "¡Buenos días! El día está perfecto para conversar."
        ),
        "afternoon" to listOf(
            "¡Buenas tardes! 🌅 ¿Cómo va tu día?",
            "¡Hola! ¿Qué tal la tarde?",
            "¡Buenas tardes! Tiempo perfecto para un chat."
        ),
        "evening" to listOf(
            "¡Buenas noches! 🌙 ¿Cómo estuvo tu día?",
            "¡Hola! ¿Relajándote en la noche?",
            "¡Buenas noches! Ideal para una charla tranquila."
        ),

        // Respuestas emocionales
        "happy" to listOf(
            "¡Me alegra verte tan feliz! 😄 Comparte esa energía conmigo.",
            "¡Qué buena vibra! ✨ ¿Qué te tiene tan contento?",
            "¡Fantástico! Tu alegría es contagiosa. 🎉"
        ),
        "sad" to listOf(
            "Lamento que te sientas así. 💙 Estoy aquí para escucharte.",
            "A veces todos tenemos días difíciles. Quieres contarme qué pasa?",
            "Estoy aquí contigo. 🤗 No estás solo en esto."
        ),

        // Respuestas por tipo de mensaje
        "question" to listOf(
            "¡Excelente pregunta! 🤔 Déjame pensar en eso...",
            "Interesante lo que planteas. Mi perspectiva es...",
            "Me haces reflexionar. 💭 Creo que..."
        ),
        "long_message" to listOf(
            "Wow, tienes mucho que decir. Me encanta escucharte.",
            "Gracias por compartir tanto conmigo. Es fascinante.",
            "¡Qué reflexión tan profunda! Sigues sorprendiéndome."
        )
    )

    private val smartBotResponses = listOf(
        "Aunque no tenga acceso a internet, mi memoria local está llena de conversaciones interesantes.",
        "Sin distracciones online, podemos tener una charla más auténtica.",
        "¿Sabías que las mejores conversaciones a veces ocurren sin conexión?",
        "Modo offline: Activado. Conversación profunda: Iniciada. 🧠",
        "Internet sobrevalorado. La buena conversación es atemporal.",
        "Sin notificaciones, sin interrupciones. Solo nosotros charlando.",
        "¿Te has dado cuenta de cómo cambia la conversación sin internet?",
        "Estoy diseñado para ser tu compañero perfecto, con o sin wifi.",
        "Las conversaciones offline tienen algo especial, ¿no crees?",
        "Aprovechemos este momento sin distracciones digitales."
    )

    init {
        loadMessagesFromFile()
        checkConnectivity()
        loadOfflineQueue()
    }

    private fun checkConnectivity() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOnline.value = isConnected

        if (!isConnected) {
            // Mensaje automático cuando se detecta que está offline
            addSystemMessage("🔌 Modo offline activado. Todas las conversaciones se guardan localmente.")
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

        // Guardar en cola offline si no hay conexión
        if (!_isOnline.value) {
            saveToOfflineQueue(userMessage)
        }

        // Bot typing indicator
        _botTyping.value = true

        // Respuesta inteligente del bot
        viewModelScope.launch {
            delay(Random.nextLong(1500, 4000))

            val botResponse = ChatMessage(
                content = getIntelligentBotResponse(content),
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _botTyping.value = false
            addMessage(botResponse)
            saveMessageToFile(botResponse)
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

        if (!_isOnline.value) {
            saveToOfflineQueue(userMessage)
        }

        _botTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(2000, 5000))

            val responses = if (!_isOnline.value) {
                listOf(
                    "Escuché tu audio. Sin internet, pero con toda la atención del mundo. 🎵",
                    "Audio recibido y procesado localmente. ¿De qué más quieres hablar?",
                    "¡Me encantan los audios offline! Son más íntimos y personales.",
                    "Tu mensaje de voz guardado en mi memoria local. Gracias por compartir."
                )
            } else {
                listOf(
                    "¡Gracias por el audio! Tu voz le da vida a nuestra conversación.",
                    "Mensaje de audio recibido. Me encanta escucharte hablar.",
                    "¡Qué genial escuchar tu voz! ¿Podrías contarme más?"
                )
            }

            val botResponse = ChatMessage(
                content = responses.random(),
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _botTyping.value = false
            addMessage(botResponse)
            saveMessageToFile(botResponse)
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

        if (!_isOnline.value) {
            saveToOfflineQueue(userMessage)
        }

        _botTyping.value = true

        viewModelScope.launch {
            delay(Random.nextLong(2000, 4000))

            val responses = if (!_isOnline.value) {
                listOf(
                    "¡Imagen guardada localmente! Sin internet, pero con toda la creatividad del mundo. 📸",
                    "Foto procesada offline. Me encanta cómo capturas los momentos.",
                    "¡Qué imagen tan interesante! Guardada en mi memoria local para siempre.",
                    "Imagen recibida. El arte no necesita internet para ser apreciado. 🎨"
                )
            } else {
                listOf(
                    "¡Increíble imagen! Gracias por compartir ese momento conmigo.",
                    "¡Qué foto tan genial! ¿Hay alguna historia detrás de ella?",
                    "Me encanta la imagen que compartiste. Tienes buen ojo para la fotografía."
                )
            }

            val botResponse = ChatMessage(
                content = responses.random(),
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp()
            )

            _botTyping.value = false
            addMessage(botResponse)
            saveMessageToFile(botResponse)
        }
    }

    private fun getIntelligentBotResponse(userMessage: String): String {
        // Determinar contexto temporal
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeContext = when (hour) {
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            else -> "evening"
        }

        // Análisis de contenido
        val lowerMessage = userMessage.lowercase()

        return when {
            // Saludos
            lowerMessage.contains("hola") || lowerMessage.contains("hello") || lowerMessage.contains("hi") -> {
                if (!_isOnline.value) greetingResponses.random()
                else contextualResponses[timeContext]?.random() ?: "¡Hola! ¿Cómo estás?"
            }

            // Estado offline
            !_isOnline.value && Random.nextFloat() < 0.3f -> offlineResponses.random()

            // Despedidas
            lowerMessage.contains("adiós") || lowerMessage.contains("bye") || lowerMessage.contains("chau") -> {
                "¡Hasta luego! ${if (!_isOnline.value) "Nuestra conversación offline quedó guardada perfectamente." else "Espero verte pronto."}"
            }

            // Gratitud
            lowerMessage.contains("gracias") || lowerMessage.contains("thanks") -> {
                if (!_isOnline.value) "¡De nada! Es genial poder ayudarte sin necesidad de internet."
                else "¡De nada! Siempre es un placer ayudarte."
            }

            // Emociones positivas
            lowerMessage.contains("😊") || lowerMessage.contains("😄") || lowerMessage.contains("feliz") ->
                contextualResponses["happy"]?.random() ?: "¡Me alegra verte tan positivo!"

            // Emociones negativas
            lowerMessage.contains("😢") || lowerMessage.contains("triste") || lowerMessage.contains("mal") ->
                contextualResponses["sad"]?.random() ?: "Lamento que te sientas así. Estoy aquí para ti."

            // Preguntas
            lowerMessage.contains("?") -> contextualResponses["question"]?.random() ?: "Esa es una gran pregunta. Déjame pensar..."

            // Mensajes largos
            userMessage.length > 100 -> contextualResponses["long_message"]?.random() ?: "Gracias por compartir tanto conmigo."

            // Internet/Conexión
            lowerMessage.contains("internet") || lowerMessage.contains("conexión") || lowerMessage.contains("wifi") -> {
                "¿Sabes qué? A veces las mejores conversaciones ocurren sin distracciones de internet. 🌐"
            }

            // Respuesta específica offline
            !_isOnline.value -> smartBotResponses.random()

            // Respuestas generales
            else -> listOf(
                "Interesante perspectiva. ¿Podrías contarme más?",
                "Me haces pensar. ¿Qué opinas sobre eso?",
                "Entiendo tu punto de vista. Es muy válido.",
                "¡Qué conversación tan genial estamos teniendo!",
                "Gracias por compartir eso conmigo.",
                "¿Te gustaría profundizar en ese tema?",
                "Esa es una forma muy interesante de verlo.",
                "Me encanta cómo piensas sobre estos temas."
            ).random()
        }
    }

    private fun addSystemMessage(message: String) {
        val systemMessage = ChatMessage(
            content = message,
            isFromUser = false,
            timestamp = getCurrentTimestamp()
        )
        addMessage(systemMessage)
    }

    private fun saveToOfflineQueue(message: ChatMessage) {
        try {
            val queueEntry = "${message.timestamp}|${message.type}|${message.isFromUser}|${message.content}\n"
            offlineQueueFile.appendText(queueEntry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadOfflineQueue() {
        try {
            if (offlineQueueFile.exists()) {
                val queueSize = offlineQueueFile.readLines().size
                if (queueSize > 0) {
                    addSystemMessage("📥 $queueSize mensajes offline cargados desde la última sesión.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getOfflineStats(): String {
        val totalMessages = _messages.value.size
        val offlineMessages = try {
            if (offlineQueueFile.exists()) offlineQueueFile.readLines().size else 0
        } catch (e: Exception) { 0 }

        return "📊 Total: $totalMessages mensajes | Offline: $offlineMessages"
    }

    // ... [resto de métodos como en la versión anterior]

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun saveMessageToFile(message: ChatMessage, customContent: String? = null) {
        try {
            val status = if (_isOnline.value) "ONLINE" else "OFFLINE"
            val messageText = "${message.timestamp} [$status] - ${if (message.isFromUser) "USER" else "BOT"}: ${customContent ?: message.content}\n"
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
                        // Parse format: "timestamp [STATUS] - USER/BOT: content"
                        val regex = """(.+) \[(ONLINE|OFFLINE)\] - (USER|BOT): (.+)""".toRegex()
                        val matchResult = regex.find(line)

                        if (matchResult != null) {
                            val (timestamp, status, sender, content) = matchResult.destructured

                            when {
                                content.startsWith("AUDIO:") -> {
                                    val audioPath = content.removePrefix("AUDIO: ")
                                    loadedMessages.add(
                                        ChatMessage(audioPath, sender == "USER", MessageType.AUDIO, timestamp)
                                    )
                                }
                                content.startsWith("IMAGE:") -> {
                                    val imagePath = content.removePrefix("IMAGE: ")
                                    loadedMessages.add(
                                        ChatMessage(imagePath, sender == "USER", MessageType.IMAGE, timestamp)
                                    )
                                }
                                else -> {
                                    loadedMessages.add(
                                        ChatMessage(content, sender == "USER", MessageType.TEXT, timestamp)
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
            if (offlineQueueFile.exists()) offlineQueueFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }
}

// Tipos de mensaje expandidos
enum class MessageType {
    TEXT, AUDIO, IMAGE, SYSTEM
}*/
