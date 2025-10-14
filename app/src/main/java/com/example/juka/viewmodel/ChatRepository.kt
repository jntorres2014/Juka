// data/repository/ChatRepository.kt
package com.example.juka.data.repository

import com.example.juka.FishingData
import com.example.juka.IMessage
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import com.example.juka.data.local.LocalStorageHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository centralizado para el chat
 * Maneja persistencia local y Firebase
 */
class ChatRepository(
    private val firebaseManager: FirebaseManager,
    private val localStorageHelper: LocalStorageHelper
) {

    private val _messages = MutableStateFlow<List<IMessage>>(emptyList())
    val messages: Flow<List<IMessage>> = _messages

    /**
     * Carga mensajes desde storage local
     */
    suspend fun loadLocalMessages(): List<IMessage> {
        return localStorageHelper.loadMessages()
    }

    /**
     * Guarda mensaje localmente
     */
    suspend fun saveMessageLocally(message: IMessage) {
        localStorageHelper.saveMessage(message)
        _messages.value = _messages.value + message
    }

    /**
     * Guarda parte en Firebase
     */
    suspend fun saveParteToFirebase(extractedData: Any, transcript: String): FirebaseResult {
        return firebaseManager.guardarParteAutomatico(extractedData as FishingData, transcript)
    }

    /**
     * Limpia mensajes
     */
    suspend fun clearMessages() {
        localStorageHelper.clearMessages()
        _messages.value = emptyList()
    }

    fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}