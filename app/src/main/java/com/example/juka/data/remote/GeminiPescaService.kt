package com.example.juka.data.remote

import android.util.Log
import com.example.juka.BuildConfig
import com.example.juka.HukaApplication
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GeminiPescaService {

    // Proyecto secundario gratuito: prueba primero este camino.
    private val firebaseAiModelName = "gemini-3.5-flash-lite"

    // Camino anterior confirmado como funcional: queda como fallback.
    private val directModelName = "gemini-2.5-flash-lite"

    private val directGenerativeModel = GenerativeModel(
        modelName = directModelName,
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val firebaseAiGenerativeModel by lazy {
        val secondaryApp = FirebaseApp.getInstance(HukaApplication.AI_FIREBASE_APP_NAME)
        Firebase
            .ai(
                app = secondaryApp,
                backend = GenerativeBackend.googleAI()
            )
            .generativeModel(firebaseAiModelName)
    }

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

        // 1) Prueba del nuevo proyecto Spark dedicado a AI Logic.
        try {
            Log.d(
                "DEBUG_CHAT",
                "probando HUKA_AI_FREE | modelo=$firebaseAiModelName | sdk=firebase-ai"
            )

            val response = firebaseAiGenerativeModel.generateContent(promptCompleto)
            val text = response.text
            if (!text.isNullOrBlank()) {
                Log.d(
                    "DEBUG_CHAT",
                    "✅ HUKA_AI_FREE respondió (${text.length} chars) | modelo=$firebaseAiModelName"
                )
                return@withContext text
            }

            Log.w("DEBUG_CHAT", "HUKA_AI_FREE devolvió respuesta vacía; fallback=directo")
        } catch (e: Exception) {
            val detalle = (e.message ?: "")
                .replace("\n", " ")
                .take(300)
            Log.w(
                "DEBUG_CHAT",
                "HUKA_AI_FREE falló [${e.javaClass.simpleName}] | detalle='$detalle' | fallback=directo"
            )
        }

        // 2) Fallback al camino anterior que ya comprobamos que funciona.
        Log.d("DEBUG_CHAT", "obtenerConsejoPesca | modelo=$directModelName | sdk=directo | fallback=true")

        var ultimaEx: Exception? = null
        repeat(3) { intento ->
            try {
                val response = directGenerativeModel.generateContent(
                    content { text(promptCompleto) }
                )
                Log.d(
                    "DEBUG_CHAT",
                    "✅ Gemini directo respondió (${response.text?.length ?: 0} chars) en intento ${intento + 1}"
                )
                return@withContext response.text
                    ?: "Lo siento, no pude generar un consejo en este momento."
            } catch (e: Exception) {
                ultimaEx = e
                val msg = e.message ?: ""
                val transitorio = msg.contains("503") ||
                    msg.contains("UNAVAILABLE", true) ||
                    msg.contains("high demand", true) ||
                    msg.contains("overloaded", true)

                val detalle = msg.replace("\n", " ").take(400)
                Log.w(
                    "DEBUG_CHAT",
                    "Fallback intento ${intento + 1}/3 falló [${e.javaClass.simpleName}] " +
                        "transitorio=$transitorio | detalle='$detalle'"
                )

                if (!transitorio) throw e
                if (intento < 2) delay(1500)
            }
        }

        Log.e("DEBUG_CHAT", "💥 Gemini directo agotó reintentos", ultimaEx)
        throw ultimaEx ?: RuntimeException("Error desconocido consultando Gemini")
    }
}

data class ConversationContext(
    val ubicacion: String? = null,
    val especieObjetivo: String? = null,
    val experiencia: String? = null
)
