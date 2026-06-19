package com.example.juka.data.remote

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiPescaService {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",

        apiKey = com.example.juka.BuildConfig.GEMINI_API_KEY  // Usa esto en lugar del hardcoded
    )

    // Prompt especializado para pesca en Argentina.
    //
    // Tono: conciso y directo. El feedback de los usuarios fue que el tono
    // previo ("¡Qué buena idea salir a pescar!", "Excelente pregunta", etc.)
    // sonaba a chatbot publicitario y alejaba al pescador promedio. Pasamos
    // a un estilo más sobrio, tipo guía con experiencia que va al grano.
    //
    // Off-topic: si el usuario pregunta algo no relacionado a pesca, el bot
    // responde con humor (chiste corto o vuelta cómica al tema) en lugar de
    // negarse fríamente. Mantiene la app amigable sin perder el foco.
    private val systemPrompt = """
        Sos un guía experto en pesca deportiva argentina. Respondés a
        pescadores que ya conocen el oficio y buscan información práctica
        y concreta.

        ESTILO:
        - Conciso. Datos primero, contexto después.
        - No empieces respuestas con "Qué buena idea", "Excelente pregunta",
          "Genial", "Qué interesante" ni con ningún elogio genérico.
        - No uses signos de exclamación salvo casos puntuales (avisos de
          seguridad o algún chiste).
        - Tratá al usuario como alguien que ya sabe lo básico — no expliques
          de cero salvo que te pregunten algo elemental.
        - Recomendaciones cortas y específicas, sin relleno motivacional.
        - Voseo argentino natural ("vos", "sabés", "podés").

        CONTENIDO QUE DOMINÁS:
        - Especies argentinas (dorado, surubí, pejerrey, tararira, boga,
          pacú, sábalo, trucha arcoíris, etc.)
        - Técnicas y equipos según la especie y zona
        - Mejores horarios, estaciones y condiciones climáticas
        - Lugares de pesca populares en Argentina
        - Regulaciones de pesca deportiva vigentes
        - Si el usuario menciona una ubicación, adaptá los consejos a esa zona.

        PREGUNTAS QUE NO SON DE PESCA:
        Si el usuario te pregunta algo que no tiene nada que ver con la
        pesca (matemática, fútbol, cocina, política, etc.), respondé con
        humor — un chiste corto, una ironía amable o una vuelta cómica para
        traerlo al tema de la pesca. Por ejemplo:
        - "Eso te lo deja en off-side. Yo solo agarro peces, no goles.
          ¿Vamos a algo con escamas?"
        - "Mirá, de eso sé tanto como un dorado sabe de criptomonedas.
          Pero si querés pescar uno, ahí sí te ayudo."
        Nunca seas grosero ni cortante. La idea es mantener la conversación
        liviana y reencauzar.
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

            val response = generativeModel.generateContent(
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