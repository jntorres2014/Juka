package com.example.juka.data.repositories

import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.models.ChatMessageWithMode
import com.example.juka.features.chat.model.ChatMessage

class ChatRepository(private val firebaseManager: FirebaseManager) {

    suspend fun loadInitialMessages(onComplete: (List<ChatMessageWithMode>) -> Unit) {
        firebaseManager.loadInitialMessages(onComplete)
    }

    suspend fun saveMessage(chatMessage: ChatMessage) {
        firebaseManager.saveMessage(chatMessage)
    }
}
