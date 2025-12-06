package com.example.juka.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiPescaService {
    private val apiKey = "AIzaSyCsgv3LoE2u4o2uaoNeIPM7zxSuZf5O0eA"

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash", // o "gemini-pro" para respuestas más elaboradas
            apiKey = apiKey
        )
    }

    // Prompt especializado para pesca en Argentina
    private val systemPrompt = """
        Eres un experto en pesca deportiva en Argentina con años de experiencia.
        Conoces perfectamente las especies argentinas, las mejores técnicas, 
        épocas del año, regulaciones locales y los mejores lugares para pescar.
        
        Debes dar consejos prácticos y específicos considerando:
        - Especies argentinas (dorado, surubí, pejerrey, tararira, boga, etc.)
        - Condiciones climáticas y estacionales de Argentina
        - Regulaciones de pesca deportiva vigentes
        - Técnicas y equipos apropiados
        - Lugares de pesca populares en el país
        
        Mantén un tono amigable y apasionado por la pesca.
        Si el usuario menciona una ubicación específica, adapta tus consejos a esa zona.
    """.trimIndent()

    suspend fun obtenerConsejoPesca(
        pregunta: String,
        contexto: ConversationContext? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val promptCompleto = buildString {
                append(systemPrompt)
                append("\n\n")

                // Agregar contexto si está disponible
                contexto?.let {
                    append("Contexto del pescador:\n")
                    it.ubicacion?.let { loc ->
                        append("- Ubicación: $loc\n")
                    }
                    it.especieObjetivo?.let { especie ->
                        append("- Especie objetivo: $especie\n")
                    }
                    it.experiencia?.let { exp ->
                        append("- Nivel de experiencia: $exp\n")
                    }
                    append("\n")
                }

                append("Pregunta del pescador: $pregunta")
            }

            val response = model.generateContent(
                content { text(promptCompleto) }
            )

            response.text ?: "Lo siento, no pude generar un consejo en este momento."

        } catch (e: Exception) {
            "Error al obtener consejo: ${e.message}"
        }
    }
}

data class ConversationContext(
    val ubicacion: String? = null,
    val especieObjetivo: String? = null,
    val experiencia: String? = null
)