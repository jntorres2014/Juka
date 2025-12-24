package com.example.juka.data.repository

import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult // Asegúrate de tener este import
import com.example.juka.data.firebase.PartePesca
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.domain.model.ChatMessageWithMode
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.IMessage
import com.example.juka.domain.usecase.FishingData
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatRepository(
    private val firebaseManager: FirebaseManager,
    private val localStorageHelper: LocalStorageHelper
) {

    private val _messages = MutableStateFlow<List<IMessage>>(emptyList())
    val messages: StateFlow<List<IMessage>> = _messages.asStateFlow()

    /**
     * Carga mensajes de la base de datos local
     */
    suspend fun loadLocalMessages(): List<IMessage> {
        val history = localStorageHelper.loadChatHistory()
        _messages.value = history
        return history
    }
    // Agrega esto al final de ChatRepository.kt
    data class EspecieJson(
        val nombre: String,
        val sinonimos: List<String>? = null
    )
    // En ChatRepository.kt

    fun obtenerListaDePeces(): List<EspecieJson> {
        return try {
            // Leemos el archivo peces.json
            val jsonString = firebaseManager.context.assets.open("peces.json")
                .bufferedReader()
                .use { it.readText() }

            // Convertimos JSON a objetos Kotlin
            val listType = object : TypeToken<List<EspecieJson>>() {}.type
            Gson().fromJson(jsonString, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            // Lista de respaldo por si falla la lectura del archivo
            listOf(
                EspecieJson("Dorado", listOf("tigre del río")),
                EspecieJson("Surubí", listOf("pintado", "atigrado"))
            )
        }
    }
    /**
     * Guarda un mensaje
     */
    suspend fun saveMessageLocally(message: IMessage) {
        val messageWithMode = if (message is ChatMessageWithMode) {
            message
        } else {
            ChatMessageWithMode(
                content = message.content,
                isFromUser = message.isFromUser,
                type = message.type,
                timestamp = message.timestamp,
                mode = ChatMode.GENERAL
            )
        }

        localStorageHelper.saveChatMessage(messageWithMode)

        val currentList = _messages.value.toMutableList()
        currentList.add(messageWithMode)
        _messages.value = currentList
    }

    suspend fun clearMessages() {
        localStorageHelper.clearHistory()
        _messages.value = emptyList()
    }

    fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    suspend fun saveParteToFirebase(parte: PartePesca): FirebaseResult {
        return firebaseManager.guardarParte(parte)
    }
}