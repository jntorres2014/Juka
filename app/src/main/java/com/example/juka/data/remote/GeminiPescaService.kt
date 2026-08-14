package com.example.juka.data.remote

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiPescaService {

    private val modelName = "gemini-3.5-flash"

    private val generativeModel = GenerativeModel(
        modelName = modelName,
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
        val key = com.example.juka.BuildConfig.GEMINI_API_KEY
        Log.d("DEBUG_CHAT", "obtenerConsejoPesca | modelo=$modelName | apiKey.len=${key.length} | apiKey.blank=${key.isBlank()}")

        val promptCompleto = buildString {
            append(systemPrompt)
            append("\n\n")
            contexto?.let {
                append("Contexto del pescador:\n")
                it.ubicacion?.let { loc -> append("- Ubicación: $loc\n") }
                it.especieObjetivo?.let { especie -> append("- Especie objetivo: $especie\n") }
                it.experiencia?.let { exp -> append("- Nivel de experiencia: $exp\n") }
                append("\n")
            }
            append("Pregunta del pescador: $pregunta")
        }

        // Gemini a veces devuelve 503 "high demand" (transitorio). Reintentamos
        // un par de veces con pausa antes de rendirnos. Si al final falla,
        // PROPAGAMOS la excepción (no la devolvemos como si fuera un consejo),
        // para que la capa de arriba la trate como error y NO descuente cuota.
        var ultimaEx: Exception? = null
        repeat(3) { intento ->
            try {
                val response = generativeModel.generateContent(content { text(promptCompleto) })
                Log.d("DEBUG_CHAT", "✅ Gemini respondió (${response.text?.length ?: 0} chars) en intento ${intento + 1}")
                return@withContext response.text ?: "Lo siento, no pude generar un consejo en este momento."
            } catch (e: Exception) {
                ultimaEx = e
                val msg = e.message ?: ""
                val transitorio = msg.contains("503") || msg.contains("UNAVAILABLE", true) ||
                        msg.contains("high demand", true) || msg.contains("overloaded", true)
                Log.w("DEBUG_CHAT", "Intento ${intento + 1}/3 falló [${e.javaClass.simpleName}] transitorio=$transitorio: ${msg.take(140)}")
                if (!transitorio) throw e            // error no transitorio → cortar ya
                if (intento < 2) kotlinx.coroutines.delay(1500)
            }
        }
        Log.e("DEBUG_CHAT", "💥 Gemini agotó reintentos", ultimaEx)
        throw ultimaEx ?: RuntimeException("Error desconocido consultando Gemini")
    }
}

data class ConversationContext(
    val ubicacion: String? = null,
    val especieObjetivo: String? = null,
    val experiencia: String? = null
)