// IntelligentResponseGenerator.kt - Sistema de respuestas inteligentes COMPLETO
package com.example.juka.core.logic

import com.example.juka.core.database.FishInfo
import com.example.juka.data.repositories.FishRepository
import kotlin.random.Random

class IntelligentResponseGenerator(private val fishRepository: FishRepository) {

    private val greetingResponses = listOf(
        "Hola amigo/a! como estas? nos vamos de pesca hoy?",
        "Hola pescador/a! 🎣 Cómo estuvo la última jornada de pesca?",
        "Holaaaa! 🌊 Preparando la próxima salida de pesca?",
        "Buen día! Que vamos a pescar hoy?",
        "Hola! 🐟 pescaste algo interesante para contarme?"
    )

    private val audioResponses = listOf(
        "Que buen dia de pesca! 🎣 ¿Podrías contarme mas? Me interesa saber qué carnadas usaste y en qué zona.",
        "Se te escucha muy bien! 🐟 Para darte consejos más específicos, ¿podrías escribirme qué especies buscabas y si tuviste éxito?",
        "Qué buena pesca! 🌊 Si subís una foto de la captura, Me envias una foto de tu pesca de hoy?"
    )

    private val followUpQuestions = listOf(
        "En qué zona específica hiciste esta captura? ",
        "Qué carnada o señuelo usaste para esta pesca? ",
        "Cómo estaban las condiciones del agua? 🌊",
        "Fue una pelea larga? ¿Cuánto tardó en cansar? ⚡",
        "La liberaste o la llevaste para consumo? 🔄"
    )

    fun getResponse(userInput: String): String {
        val lowerInput = userInput.lowercase()

        // Detectar especies mencionadas
        val mentionedSpecies = fishRepository.getFishSpeciesDB().keys.find { lowerInput.contains(it) }

        return when {
            // Saludos
            lowerInput.matches(Regex(".*(hola|hello|hi|buen dia|buenas).*")) -> {
                greetingResponses.random()
            }

            // Información específica sobre especies
            mentionedSpecies != null -> {
                val fish = fishRepository.getFishSpeciesDB()[mentionedSpecies]!!
                buildSpeciesInfoResponse(fish)
            }

            // Consultas sobre carnadas
            lowerInput.contains("carnada") || lowerInput.contains("señuelo") || lowerInput.contains("cebo") -> {
                getBaitAdviceResponse()
            }

            // Consultas sobre técnicas
            lowerInput.contains("técnica") || lowerInput.contains("como pescar") || lowerInput.contains("método") -> {
                getTechniqueAdviceResponse()
            }

            // Consultas sobre identificación
            lowerInput.contains("identificar") || lowerInput.contains("que pez") || lowerInput.contains("especie") -> {
                " Subí una foto clara de tu captura y te digo exactamente qué especie es, con alguna informacion mas"
            }

            // Consultas sobre zonas
            lowerInput.contains("zona") || lowerInput.contains("lugar") || lowerInput.contains("donde") -> {
                getLocationAdviceResponse()
            }

            // Consultas sobre clima
            lowerInput.contains("clima") || lowerInput.contains("tiempo") || lowerInput.contains("lluvia") || lowerInput.contains("viento") -> {
                getWeatherAdviceResponse()
            }

            // Relatos de pesca (mensajes largos)
            userInput.length > 80 -> {
                getLongMessageResponse()
            }

            // Preguntas generales
            lowerInput.contains("?") -> {
                getGeneralQuestionResponse()
            }

            // Agradecimientos
            lowerInput.contains("gracias") || lowerInput.contains("thanks") -> {
                "De nada! Estoy aquí para ayudarte. :)"
            }

            // Despedidas
            lowerInput.contains("chau") || lowerInput.contains("adiós")
                    || lowerInput.contains("nos vemos") || lowerInput.contains("hasta pronto")
                    || lowerInput.contains("bye") -> {
                "Que tengas excelente pesca! 🎣 Recordá subir fotos de tus capturas para identificación automática. ¡Tight lines! 🐟⚓"
            }

            // Respuesta general inteligente
            else -> {
                getGeneralResponse()
            }
        }
    }

    fun getStoryResponse(analysis: FishingStoryAnalysis): String {
        return when (analysis.storyType) {
            "exitosa" -> {
                val speciesAdvice = if (analysis.speciesFound.isNotEmpty()) {
                    val mainSpecies = analysis.speciesFound.maxByOrNull { it.quantity }?.species
                    mainSpecies?.let { species ->
                        val fishInfo = fishRepository.findFishByKeyword(species)
                        fishInfo?.let {
                            "Para seguir teniendo éxito con ${species}s, recordá: ${it.bestTime} es el mejor horario y ${it.bestBaits.first()} funciona muy bien."
                        }
                    }
                } else {
                    
                } ?: ""

                "¡Excelente jornada! 🏆 ${analysis.totalFish} peces es un gran resultado. $speciesAdvice ¿Pensás volver a la misma zona?"
            }

            "regular" -> {
                val encouragement = if (analysis.totalFish > 0) {
                    "Jornada decente con ${analysis.totalFish} pez${if (analysis.totalFish > 1) "es" else ""}. "
                } else {
                    "Aunque no hubo capturas, es parte del aprendizaje."
                }

                "$encouragement ${getSuggestionBasedOnAnalysis(analysis)} ¿Qué técnica te gustaría probar la próxima?"
            }

            "mala" -> {
                val suggestion = getSuggestionBasedOnAnalysis(analysis)
                "Los días difíciles son parte de la pesca 💪 $suggestion ¿Sabés que a veces cambiar de carnada o zona puede hacer la diferencia?"
            }

            "técnica" -> {
                "¡Me encanta tu enfoque técnico! 📚 ${getAdviceBasedOnTechniques(analysis)} ¿Hay alguna técnica específica que te gustaría perfeccionar?"
            }

            else -> "¡Gracias por compartir tu experiencia! ¿Hay algo específico que te gustaría mejorar para la próxima jornada?"
        }
    }

    private fun getSuggestionBasedOnAnalysis(analysis: FishingStoryAnalysis): String {
        return when {
            analysis.timeOfDay == "mediodía" -> "Probá pescar al amanecer o atardecer, suelen ser más productivos."
            analysis.weather == "soleado" -> "En días soleados, buscá sombras o zonas más profundas."
            analysis.techniques.isEmpty() -> "Experimentar con diferentes técnicas puede marcar la diferencia."
            analysis.baits.isEmpty() -> "Variar las carnadas según la especie objetivo es clave."
            else -> "A veces cambiar de zona o horario puede mejorar los resultados."
        }
    }

    private fun getAdviceBasedOnTechniques(analysis: FishingStoryAnalysis): String {
        return when {
            "spinning" in analysis.techniques -> "El spinning es excelente para depredadores como dorado y tararira."
            "fondo" in analysis.techniques -> "La pesca de fondo funciona muy bien para surubís y bagres grandes."
            "boya" in analysis.techniques -> "Con boya es ideal para pejerrey y pacús en lagunas."
            else -> "Buena selección de técnicas."
        }
    }

    private fun buildSpeciesInfoResponse(fish: FishInfo): String {
        return '''
🐟 **${fish.name}** (${fish.scientificName})

🏞️ **Hábitat:** ${fish.habitat}
🎣 **Mejores carnadas:** ${fish.bestBaits.joinToString(", ")}
⏰ **Mejor horario:** ${fish.bestTime}
🎯 **Técnica:** ${fish.technique}
📏 **Tamaño promedio:** ${fish.avgSize}
📅 **Temporada:** ${fish.season}

¿En qué zona específica estás pescando ${fish.name.lowercase()}? 🗺️
        '''.trimIndent()
    }

    private fun getBaitAdviceResponse(): String {
        return '''
🎯 **Guía de carnadas por especie:**

🔥 **Depredadores (dorado, tararira):**
• Carnada viva (mojarras, bagrecitos)
• Señuelos plateados/dorados
• Cucharitas rotativas

🌱 **Omnívoros (pacú, boga):**
• Frutas (higo, durazno)
• Maíz, pellets
• Masa con esencias

🪱 **Bentónicos (surubí):**
• Lombrices grandes
• Bagre cortado
• Hígado de pollo

¿Qué especie estás buscando específicamente? 🎣
        '''.trimIndent()
    }

    private fun getTechniqueAdviceResponse(): String {
        return '''
🎣 **Técnicas por ambiente:**

🌊 **Río con corriente:**
• Spinning para dorado
• Pesca al correntino para boga
• Fly fishing en pozones

🏞️ **Lagunas y embalses:**
• Pesca con boya para pejerrey
• Trolling para depredadores
• Bottom para grandes

🌿 **Vegetación:**
• Casting para tararira
• Topwater en juncales

¿En qué tipo de agua vas a pescar? 🗺️
        '''.trimIndent()
    }

    private fun getLocationAdviceResponse(): String {
        return '''
🗺️ **Guía de zonas productivas:**

🌊 **Ríos:**
• Confluencias de arroyos
• Pozones después de correderas  
• Sombras de puentes y muelles
• Remansos con vegetación

🏞️ **Lagunas:**
• Juncales y totorales
• Desembocaduras de canales
• Zonas con profundidad variable
• Estructuras sumergidas

¿En qué provincia estás pescando? Te puedo dar consejos más específicos. 📍
        '''.trimIndent()
    }

    private fun getWeatherAdviceResponse(): String {
        return '''
🌤️ **Clima y pesca:**

☀️ **Días soleados:**
• Pescá en sombras
• Mejores horarios: amanecer/atardecer
• Usá carnadas naturales

🌧️ **Antes/después de lluvia:**
• Excelente para pesca
• Agua oxigenada
• Peces más activos

💨 **Días ventosos:**
• Buscá zonas protegidas
• El viento mueve carnadas naturalmente
• Bueno para señuelos

¿Cómo está el tiempo en tu zona? 🌡️
        '''.trimIndent()
    }

    private fun getLongMessageResponse(): String {
        val responses = listOf(
            "¡Qué experiencia genial! 🎣 Me encanta escuchar relatos detallados. Si tenés fotos de la captura, subilas para identificación automática. ¿Qué fue lo más desafiante?",
            "¡Excelente relato! 🐟 Veo que conocés bien la técnica. ¿Algún consejo específico para esa zona? Si subís fotos puedo identificar las especies automáticamente.",
            "¡Increíble jornada! 🌊 Esas experiencias valen oro. ¿Volverías al mismo lugar? ¡Subí fotos de las capturas para análisis completo!"
        )
        return responses.random()
    }

    private fun getGeneralQuestionResponse(): String {
        return '''
🤔 **¡Gran pregunta!** 

Para darte la mejor respuesta, contame:
• ¿Qué especie buscás? 🐟
• ¿En qué tipo de agua? (río/laguna) 🌊
• ¿Qué equipo tenés? 🎣

💡 **Tip:** Si tenés fotos de capturas, ¡subílas! Mi IA puede identificar especies automáticamente y darte info completa.
        '''.trimIndent()
    }

    private fun getGeneralResponse(): String {
        val responses = listOf(
            "Interesante! 🎣 ¿Podrías contarme más? Si tenés fotos de capturas, subílas para identificación automática con IA.",
            "Probaste alguna técnica nueva? ¡Y no olvides subir fotos para análisis de especies! ",
            "¡Qué bueno! 🌊 ¿En qué zona pescás?",
            "¡Excelente! ⚓ ¿Cuál es tu especie favorita? Si subís fotos puedo identificarlas automáticamente y darte info completa."
        )
        return responses.random()
    }

    fun getAudioResponse(): String {
        return audioResponses.random()
    }

    fun getFollowUpQuestion(): String {
        return followUpQuestions.random()
    }

    fun getImageAnalysisFallback(): String {
        val analysisOptions = listOf(
            '''
📸 **Análisis visual local:**

🔍 **Observaciones generales:**
• Buen manejo del ejemplar
• Foto clara y bien enfocada
• Pez en buen estado

🎣 **Para identificación precisa necesito:**
• Conexión a internet para usar IA
• Foto del perfil lateral completo
• Buena iluminación

¿Podrías contarme en qué zona pescaste? 🗺️
            '''.trimIndent(),

            '''
📸 **Análisis local completado:**

⭐ **Puntos positivos:**
• Excelente técnica de sujeción
• Respeto por el ejemplar
• Buena composición de foto

¿Qué carnada usaste para esta captura? 🎯
            '''.trimIndent()
        )

        return analysisOptions.random()
    }
}

class FishingStoryAnalysis(
    val storyType: String,
    val totalFish: Int,
    val speciesFound: List<SpeciesQuantity>,
    val timeOfDay: String?,
    val weather: String?,
    val techniques: List<String>,
    val baits: List<String>
)

data class SpeciesQuantity(
    val species: String,
    val quantity: Int
)