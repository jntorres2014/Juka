
package com.example.juka.features.chat.model

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val type: MessageType,
    val timestamp: String
)
