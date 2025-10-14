// domain/usecase/SendMessageUseCase.kt
package com.example.juka.usecase

import com.example.juka.data.repository.ChatRepository
import com.example.juka.IntelligentResponses
import com.example.juka.FishingDataExtractor
import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.MessageType

/**
 * Use Case para enviar mensajes
 * Contiene toda la lógica de negocio
 */
class SendMessageUseCase(
    private val repository: ChatRepository,
    private val intelligentResponses: IntelligentResponses,
    private val dataExtractor: FishingDataExtractor
) {

    suspend fun sendTextMessage(content: String): Result<ChatMessage> {
        return try {
            // 1. Crear mensaje del usuario
            val userMessage = ChatMessage(
                content = content,
                isFromUser = true,
                type = MessageType.TEXT,
                timestamp = repository.getCurrentTimestamp()
            )

            // 2. Guardar localmente
            repository.saveMessageLocally(userMessage)

            // 3. Extraer datos de pesca
            val extractedData = dataExtractor.extractFromMessage(content)
            val missingFields = dataExtractor.getMissingFields(extractedData)

            // 4. Generar respuesta inteligente
            val response = intelligentResponses.getResponse(content)

            // 5. Si el parte está completo, guardar en Firebase
            var finalResponse = response
            if (missingFields.isEmpty() && extractedData.fishCount != null && extractedData.fishCount!! > 0) {
                repository.saveParteToFirebase(extractedData, content)
                finalResponse += "\n\n✅ **Parte completo guardado automáticamente en Firebase!**"
            } else if (missingFields.isNotEmpty()) {
                finalResponse += "\n\n📝 **Para completar el registro:** ${missingFields.joinToString(" ")}"
            }

            // 6. Crear respuesta del bot
            val botMessage = ChatMessage(
                content = finalResponse,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = repository.getCurrentTimestamp()
            )

            // 7. Guardar respuesta
            repository.saveMessageLocally(botMessage)

            Result.success(botMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAudioMessage(transcript: String): Result<ChatMessage> {
        // Similar lógica para audio
        return try {
            val userMessage = ChatMessage(
                content = "🎤 \"$transcript\"",
                isFromUser = true,
                type = MessageType.AUDIO,
                timestamp = repository.getCurrentTimestamp()
            )

            repository.saveMessageLocally(userMessage)

            val response = intelligentResponses.getAudioResponse()
            val botMessage = ChatMessage(
                content = "👂 Perfecto, entendí: \"$transcript\"\n\n$response",
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = repository.getCurrentTimestamp()
            )

            repository.saveMessageLocally(botMessage)

            Result.success(botMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendImageMessage(imagePath: String): Result<ChatMessage> {
        // Similar lógica para imágenes
        return try {
            val userMessage = ChatMessage(
                content = imagePath,
                isFromUser = true,
                type = MessageType.IMAGE,
                timestamp = repository.getCurrentTimestamp()
            )

            repository.saveMessageLocally(userMessage)

            val response = "📸 ¡Excelente foto! La agregué a tu reporte de pesca."
            val botMessage = ChatMessage(
                content = response,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = repository.getCurrentTimestamp()
            )

            repository.saveMessageLocally(botMessage)

            Result.success(botMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}