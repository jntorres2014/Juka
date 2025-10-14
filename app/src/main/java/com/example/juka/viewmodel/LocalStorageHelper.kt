// data/local/LocalStorageHelper.kt
package com.example.juka.data.local

import android.content.Context
import com.example.juka.IMessage
import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.MessageType
import java.io.File

/**
 * Helper para manejo de archivos locales
 * Separa lógica de archivos del ViewModel
 */
class LocalStorageHelper(private val context: Context) {

    private val chatFile = File(context.filesDir, "fishing_chat_history.txt")
    private val conversationLogFile = File(context.filesDir, "conversation_log.txt")

    /**
     * Carga mensajes desde archivo
     */
    fun loadMessages(): List<IMessage> {
        if (!chatFile.exists()) return emptyList()

        return try {
            chatFile.readLines()
                .takeLast(50)
                .mapNotNull { line -> parseMessageLine(line) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Guarda mensaje en archivo
     */
    fun saveMessage(message: IMessage, customContent: String? = null) {
        try {
            val messageText = "${message.timestamp} - " +
                    "${if (message.isFromUser) "USER" else "BOT"}: " +
                    "${customContent ?: message.content}\n"
            chatFile.appendText(messageText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Guarda log de conversación
     */
    fun saveConversationLog(sender: String, content: String, timestamp: String) {
        try {
            val logText = "$timestamp - $sender: $content\n"
            conversationLogFile.appendText(logText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Limpia mensajes
     */
    fun clearMessages() {
        try {
            if (chatFile.exists()) chatFile.delete()
            if (conversationLogFile.exists()) conversationLogFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Parsea línea de archivo a mensaje
     */
    private fun parseMessageLine(line: String): IMessage? {
        if (line.isBlank()) return null

        val parts = line.split(" - ", limit = 2)
        if (parts.size != 2) return null

        val timestamp = parts[0]
        val content = parts[1]

        return when {
            content.startsWith("USER: AUDIO_TRANSCRIPT:") -> {
                val transcript = content.removePrefix("USER: AUDIO_TRANSCRIPT: ")
                ChatMessage("🎤 \"$transcript\"", true, MessageType.AUDIO, timestamp)
            }
            content.startsWith("USER: IMAGE:") -> {
                val imagePath = content.removePrefix("USER: IMAGE: ")
                ChatMessage(imagePath, true, MessageType.IMAGE, timestamp)
            }
            content.startsWith("USER: ") -> {
                ChatMessage(content.removePrefix("USER: "), true, MessageType.TEXT, timestamp)
            }
            content.startsWith("BOT: ") -> {
                ChatMessage(content.removePrefix("BOT: "), false, MessageType.TEXT, timestamp)
            }
            else -> null
        }
    }
}