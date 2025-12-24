package com.example.juka.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.repository.ChatRepository
import com.example.juka.domain.model.IMessage
import com.example.juka.usecase.SendMessageUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ✅ ViewModel Refactorizado (Clean Architecture)
 * Ya no crea sus herramientas, las recibe en el constructor (Inyección de Dependencias).
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase
) : ViewModel() {

    // ================== UI STATE ==================
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Los mensajes fluyen directamente desde el Repositorio
    val messages: StateFlow<List<IMessage>> = chatRepository.messages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Helpers de estado para la UI (Mantenidos para compatibilidad con tu UI actual)
    val isTyping: StateFlow<Boolean> = _uiState.map { it.isTyping }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isAnalyzing: StateFlow<Boolean> = _uiState.map { it.isAnalyzing }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val firebaseStatus: StateFlow<String?> = _uiState.map { it.firebaseStatus }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ================== INITIALIZATION ==================
    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                val localMessages = chatRepository.loadLocalMessages()

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

            // Usamos el UseCase inyectado
            sendMessageUseCase.sendTextMessage(content)
                .onSuccess {
                    _uiState.update { it.copy(isTyping = false, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isTyping = false, error = error.message) }
                }
        }
    }

    fun sendAudioTranscript(transcript: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true) }

            // Usamos el UseCase inyectado
            sendMessageUseCase.sendAudioMessage(transcript)
                .onSuccess {
                    _uiState.update { it.copy(isTyping = false, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isTyping = false, error = error.message) }
                }
        }
    }

    fun sendImageMessage(imagePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }

            // Usamos el UseCase inyectado
            sendMessageUseCase.sendImageMessage(imagePath)
                .onSuccess {
                    _uiState.update { it.copy(isAnalyzing = false, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isAnalyzing = false, error = error.message) }
                }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            chatRepository.clearMessages()
            addWelcomeMessage()
        }
    }

    fun getConversationStats(): String {
        val currentMessages = messages.value
        val totalMessages = currentMessages.size
        val userMessages = currentMessages.count { it.isFromUser }
        val botMessages = totalMessages - userMessages

        return "📊 Total: $totalMessages | Usuario: $userMessages | Bot: $botMessages"
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
            timestamp = chatRepository.getCurrentTimestamp()
        )
        chatRepository.saveMessageLocally(welcomeMessage)
    }
}

data class ChatUiState(
    val isTyping: Boolean = false,
    val isAnalyzing: Boolean = false,
    val firebaseStatus: String? = null,
    val error: String? = null
)

data class ChatMessage(
    override val content: String,
    override val isFromUser: Boolean,
    override val type: MessageType,
    override val timestamp: String,
    val options: List<String> = emptyList()

) : IMessage

enum class MessageType {
    TEXT, AUDIO, IMAGE
}