// viewmodel/ChatViewModel.kt - VERSIÓN OPTIMIZADA
package com.example.juka.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.IMessage
import com.example.juka.data.repository.ChatRepository
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.usecase.SendMessageUseCase
import com.example.juka.FishDatabase
import com.example.juka.IntelligentResponses
import com.example.juka.FishingDataExtractor
import com.example.juka.data.firebase.PartePesca
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

import kotlinx.coroutines.launch

/**
 * ViewModel optimizado con Repository Pattern y Use Cases
 * Responsabilidades:
 * - Manejo de estado UI
 * - Coordinación de casos de uso
 * - Observación de datos del Repository
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    // ================== DEPENDENCIES ==================
    private val localStorageHelper = LocalStorageHelper(application)
    private val firebaseManager = FirebaseManager(application)
    private val repository = ChatRepository(firebaseManager, localStorageHelper)

    private val fishDatabase = FishDatabase(application)
    private val intelligentResponses = IntelligentResponses(fishDatabase)
    private val dataExtractor = FishingDataExtractor(application)

    private val sendMessageUseCase = SendMessageUseCase(
        repository,
        intelligentResponses,
        dataExtractor
    )

    // ================== UI STATE ==================
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Backward compatibility (puedes remover después)
    val messages: StateFlow<List<IMessage>> = repository.messages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val isTyping: StateFlow<Boolean> = _uiState.map { it.isTyping }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    val isAnalyzing: StateFlow<Boolean> = _uiState.map { it.isAnalyzing }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    val firebaseStatus: StateFlow<String?> = _uiState.map { it.firebaseStatus }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    // ================== INITIALIZATION ==================
    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                // Inicializar base de datos
                fishDatabase.initialize()

                // Cargar mensajes locales
                val localMessages = repository.loadLocalMessages()

                // Si no hay mensajes, agregar bienvenida
                if (localMessages.isEmpty()) {
                    addWelcomeMessage()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ================== PUBLIC ACTIONS ==================

    fun sendTextMessage(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true) }

            delay(kotlin.random.Random.nextLong(1000, 3000))

            sendMessageUseCase.sendTextMessage(content)
                .onSuccess {
                    _uiState.update { it.copy(isTyping = false, error = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTyping = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun sendAudioTranscript(transcript: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true) }

            delay(kotlin.random.Random.nextLong(1000, 2500))

            sendMessageUseCase.sendAudioMessage(transcript)
                .onSuccess {
                    _uiState.update { it.copy(isTyping = false, error = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTyping = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun sendImageMessage(imagePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }

            delay(kotlin.random.Random.nextLong(2000, 4000))

            sendMessageUseCase.sendImageMessage(imagePath)
                .onSuccess {
                    _uiState.update { it.copy(isAnalyzing = false, error = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            repository.clearMessages()
            addWelcomeMessage()
        }
    }

    fun getConversationStats(): String {
        val currentMessages = messages.value
        val totalMessages = currentMessages.size
        val userMessages = currentMessages.count { it.isFromUser }
        val botMessages = totalMessages - userMessages

        return "📊 Total: $totalMessages | Usuario: $userMessages | Bot: $botMessages | Firebase: Activo"
    }

    // ================== PRIVATE HELPERS ==================

    private suspend fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            content = """
                Hola pescador! Soy Juka, tu asistente de pesca inteligente.
                
                **Funciones:**
                • Consejos sobre especies argentinas
                • Análisis de técnicas y carnadas
                • Registro automático en Firebase
                
                Contame sobre tus jornadas, subí fotos o grabá un audio!
            """.trimIndent(),
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = repository.getCurrentTimestamp()
        )
        repository.saveMessageLocally(welcomeMessage)
    }
}


// ================== UI STATE ==================

data class ChatUiState(
    val isTyping: Boolean = false,
    val isAnalyzing: Boolean = false,
    val firebaseStatus: String? = null,
    val error: String? = null
)

// ================== DATA CLASSES (mantener compatibilidad) ==================

data class ChatMessage(
    override val content: String,
    override val isFromUser: Boolean,
    override val type: MessageType,
    override val timestamp: String
) : IMessage

enum class MessageType {
    TEXT, AUDIO, IMAGE
}
/**
 * Obtiene estadísticas de Firebase
 */
/*
suspend fun obtenerEstadisticasFirebase(): Map<String, Any> {
    return firebaseManager.obtenerEstadisticas()
}

*/
/**
 * Obtiene mis partes desde Firebase
 *//*

suspend fun obtenerMisPartes(): List<PartePesca> {
    return firebaseManager.obtenerMisPartes()
}*/
