// domain/usecase/SendMessageUseCase.kt
package com.example.juka.usecase
import com.example.juka.data.firebase.Captura
import com.example.juka.data.firebase.PartePesca
import com.google.firebase.Timestamp

import com.example.juka.data.repository.ChatRepository
import com.example.juka.domain.usecase.IntelligentResponses
import com.example.juka.domain.usecase.FishingDataExtractor
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
// En viewmodel/UseCases.kt

    suspend fun sendTextMessage(content: String): Result<ChatMessage> {
        return try {
            // 1. Mensaje usuario
            val userMessage = ChatMessage(
                content = content,
                isFromUser = true,
                type = MessageType.TEXT,
                timestamp = repository.getCurrentTimestamp()
            )
            repository.saveMessageLocally(userMessage)

            // 2. Extracción y Respuesta
            val extractedData = dataExtractor.extractFromMessage(content)
            val missingFields = dataExtractor.getMissingFields(extractedData)
            val response = intelligentResponses.getResponse(content)

            var finalResponse = response

            // 3. Guardado en Firebase (Lógica Simple y Robusta)
            if (missingFields.isEmpty() && extractedData.fishCount != null && extractedData.fishCount!! > 0) {

                // Intentamos detectar especies simplemente (sin frenar el flujo)
                val pecesDetectados = detectarPecesSimple(content, extractedData.fishCount ?: 0)

                val parteParaGuardar = PartePesca(
                    fecha = extractedData.day,
                    horaInicio = extractedData.startTime,
                    horaFin = extractedData.endTime,
                    tipo = extractedData.type,
                    numeroCanas = extractedData.rodsCount ?: 0,
                    cantidadTotal = extractedData.fishCount ?: 0,
                    transcripcionOriginal = content,
                    timestamp = Timestamp.now(),
                    peces = pecesDetectados // Si no encuentra nada, va vacía y no rompe nada
                )

                repository.saveParteToFirebase(parteParaGuardar)
                finalResponse += "\n\n✅ **Parte guardado en la nube.**"

            } else if (missingFields.isNotEmpty()) {
                finalResponse += "\n\n📝 **Falta:** ${missingFields.joinToString(", ")}"
            }

            // 4. Respuesta Bot
            val botMessage = ChatMessage(
                content = finalResponse,
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
    // =====================================================================
    //  FUNCIONES AUXILIARES (Pégalas abajo de sendTextMessage)
    // =====================================================================

    /**
     * Busca TODAS las coincidencias posibles para mostrar opciones al usuario.
     * Ej: "Pejerrey" -> ["Pejerrey de mar", "Pejerrey de río", "Pejerrey cornalito"]
     */
    private fun buscarPosiblesPeces(texto: String): List<String> {
        val textoLower = texto.lowercase()
        // ⚠️ Asegúrate de que tu Repositorio tenga esta función implementada (leyendo el JSON)
        val todasLasEspecies = repository.obtenerListaDePeces()
        val coincidencias = mutableSetOf<String>()

        // 1. Si el texto YA ES un nombre exacto, devolvemos solo ese (no hay ambigüedad)
        val coincidenciaExacta = todasLasEspecies.find {
            textoLower == it.nombre.lowercase() || textoLower.contains(it.nombre.lowercase())
        }

        // Si encontramos un nombre largo y específico (ej: "Pejerrey de mar"), confiamos en ese
        // y no preguntamos más.
        if (coincidenciaExacta != null && coincidenciaExacta.nombre.length > 8) { // Filtro simple por longitud
            // Opcional: Podrías retornar vacío para indicar "Sin ambigüedad"
            // Pero retornamos solo 1 para ser consistentes
            return listOf(coincidenciaExacta.nombre)
        }

        // 2. Búsqueda amplia (para encontrar las opciones)
        todasLasEspecies.forEach { pez ->
            // Chequeamos nombre oficial
            if (pez.nombre.lowercase().contains(textoLower) || textoLower.contains(pez.nombre.lowercase())) {
                coincidencias.add(pez.nombre)
            }
            // Chequeamos sinónimos
            pez.sinonimos?.forEach { sin ->
                if (textoLower.contains(sin.lowercase())) {
                    coincidencias.add(pez.nombre)
                }
            }
        }

        // Filtramos resultados locos (si el usuario puso "a", no devolvemos todos los peces con "a")
        return if (textoLower.length > 3) coincidencias.toList() else emptyList()
    }
// En UseCases.kt

    private fun detectarPecesSimple(texto: String, cantidadTotal: Int): List<Captura> {
        val textoLower = texto.lowercase()
        val candidatos = mutableListOf<Captura>()
        val especiesDelJson = repository.obtenerListaDePeces()

        // 1. Búsqueda cruda
        especiesDelJson.forEach { especieData ->
            var encontrado = false
            if (textoLower.contains(especieData.nombre.lowercase())) encontrado = true

            if (!encontrado && !especieData.sinonimos.isNullOrEmpty()) {
                especieData.sinonimos.forEach { sinonimo ->
                    if (textoLower.contains(sinonimo.lowercase())) encontrado = true
                }
            }

            if (encontrado) {
                candidatos.add(Captura(especie = especieData.nombre, cantidad = 1, pesoAproximado = 0.0))
            }
        }

        // 2. Filtrado inteligente (Eliminar "Pejerrey" si ya está "Pejerrey de mar")
        val listaFinal = mutableListOf<Captura>()
        val candidatosOrdenados = candidatos.sortedByDescending { it.especie.length } // Largos primero

        candidatosOrdenados.forEach { candidato ->
            val esRedundante = listaFinal.any { pezYaAgregado ->
                pezYaAgregado.especie.lowercase().contains(candidato.especie.lowercase())
            }
            if (!esRedundante) {
                listaFinal.add(candidato)
            }
        }

        // 3. Ajuste de cantidad
        if (listaFinal.size == 1 && cantidadTotal > 1) {
            listaFinal[0] = listaFinal[0].copy(cantidad = cantidadTotal)
        }

        return listaFinal
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