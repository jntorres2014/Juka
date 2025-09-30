package com.example.juka.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.core.logic.IntelligentResponseGenerator
import com.example.juka.data.models.ChatMode
import com.example.juka.data.models.ChatMessageWithMode
import com.example.juka.data.repositories.ChatRepository
import com.example.juka.data.repositories.FishRepository
import com.example.juka.features.chat.model.ChatMessage
import com.example.juka.features.chat.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val fishRepository: FishRepository,
    private val intelligentResponseGenerator: IntelligentResponseGenerator
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessageWithMode>>(emptyList())
    val messages: StateFlow<List<ChatMessageWithMode>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    init {
        loadInitialMessages()
        viewModelScope.launch {
            fishRepository.initialize()
        }
    }

    private fun loadInitialMessages() {
        viewModelScope.launch {
            chatRepository.loadInitialMessages {
                _messages.value = it
            }
        }
    }

    fun sendMessage(text: String, user: com.google.firebase.auth.FirebaseUser) {
        val message = ChatMessage(
            message = text,
            timestamp = System.currentTimeMillis(),
            isMe = true,
            author = user.displayName ?: "User",
            type = MessageType.TEXT
        )
        _messages.value = _messages.value + ChatMessageWithMode(message, ChatMode.GENERAL)

        viewModelScope.launch {
            chatRepository.saveMessage(message)
            val response = intelligentResponseGenerator.getResponse(text)
            val botMessage = ChatMessage(
                message = response,
                timestamp = System.currentTimeMillis(),
                isMe = false,
                author = "Juka",
                type = MessageType.TEXT
            )
            _messages.value = _messages.value + ChatMessageWithMode(botMessage, ChatMode.GENERAL)
            chatRepository.saveMessage(botMessage)
        }
    }
}
