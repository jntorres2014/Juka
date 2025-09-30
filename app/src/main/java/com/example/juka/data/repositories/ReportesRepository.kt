package com.example.juka.data.repositories

import android.util.Log
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.models.PartePesca
import com.example.juka.features.chat.model.ChatMessage
import com.example.juka.features.reportes.ParteConChat

class ReportesRepository(private val firebaseManager: FirebaseManager) {

    companion object {
        private const val TAG = "ReportesRepository"
    }

    suspend fun getPartesConChat(limite: Int): List<ParteConChat> {
        val partes = firebaseManager.obtenerMisPartes(limite)
        return partes.map { parte ->
            try {
                val chatMessages = firebaseManager.obtenerChatPorParteId(parte.id)
                val validChatMessages = chatMessages.filterIsInstance<ChatMessage>()

                ParteConChat(
                    parte = parte,
                    tieneChat = validChatMessages.isNotEmpty(),
                    chatMessages = validChatMessages,
                    numeroMensajes = validChatMessages.size
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error verificando chat para parte ${parte.id}: ${e.message}")
                ParteConChat(parte = parte)
            }
        }
    }
}
