
package com.example.huka
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import android.app.Application

data class FishingStoryAnalysis(
    val totalFish: Int,
    val speciesFound: List<SpeciesCatch>,
    val estimatedDuration: String?,
    val location: String?,
    val techniques: List<String>,
    val baits: List<String>,
    val timeOfDay: String?,
    val weather: String?,
    val hasSuccessIndicators: Boolean,
    val storyType: String // "exitosa", "regular", "mala", "técnica"
)

data class SpeciesCatch(
    val species: String,
    val quantity: Int,
    val sizeIndicators: List<String> = emptyList(),
    val confidence: Double // 0.0 - 1.0
)

class FishingStoryAnalyzer(private val application: Application) {

    private val fishDatabase = FishDatabase()
    private val analysisLogFile = File(application.filesDir, "story_analysis_log.txt")

    // 🐟 Diccionario de especies con sinónimos y variaciones
    private val speciesPatterns = mapOf(
        "dorado" to listOf("dorado", "dorados", "doradito", "doraditos", "salminus", "tigre del río"),
        "surubí" to listOf("surubí", "surubís", "surubi", "surubis", "pintado", "pintados", "pseudoplatystoma"),
        "pacú" to listOf("pacú", "pacús", "pacu", "pacus", "piaractus"),
        "pejerrey" to listOf("pejerrey", "pejerreyes", "pejerrei", "odontesthes"),
        "tararira" to listOf("tararira", "tarariras", "tarira", "tariras", "hoplias", "dientudo"),
        "sábalo" to listOf("sábalo", "sábalos", "sabalo", "sabalos", "prochilodus"),
        "boga" to listOf("boga", "bogas", "leporinus"),
        "bagre" to listOf("bagre", "bagres", "pimelodus", "gato"),
        "carpa" to listOf("carpa", "carpas", "cyprinus"),
        "trucha" to listOf("trucha", "truchas", "salmo", "arcoiris"),
        "salmón" to listOf("salmón", "salmones", "salmon", "salmones"),
        "mojarra" to listOf("mojarra", "mojarras", "mojarrita", "mojarritas")
    )

    // 🔢 Patrones de números y cantidades
    private val numberPatterns = mapOf(
        "cero" to 0, "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20, "treinta" to 30
    )

    // 🎣 Patrones de técnicas
    private val techniquePatterns = listOf(
        "spinning", "baitcasting", "fly", "mosca", "trolling", "curricán", "fondo",
        "correntino", "boya", "flotador", "casting", "jigging", "topwater",
        "superficie", "profundidad", "deriva", "anclado"
    )

    // 🎯 Patrones de carnadas
    private val baitPatterns = listOf(
        "lombriz", "lombrices", "cascarudo", "cascarudos", "maíz", "pan", "masa",
        "carnada viva", "mojarrita", "bagrecito", "cucharita", "spinnerbait",
        "jig", "popper", "señuelo", "artificial", "cebo", "frutas", "pellets"
    )

    fun analyzeStory(text: String): FishingStoryAnalysis {
        val normalizedText = text.lowercase().trim()

        // 1. Detectar especies y cantidades
        val speciesFound = detectSpeciesAndQuantities(normalizedText)

        // 2. Calcular total de peces
        val totalFish = speciesFound.sumOf { it.quantity }

        // 3. Analizar otros aspectos
        val duration = detectDuration(normalizedText)
        val location = detectLocation(normalizedText)
        val techniques = detectTechniques(normalizedText)
        val baits = detectBaits(normalizedText)
        val timeOfDay = detectTimeOfDay(normalizedText)
        val weather = detectWeather(normalizedText)
        val hasSuccess = detectSuccessIndicators(normalizedText)
        val storyType = classifyStoryType(normalizedText, totalFish, hasSuccess)

        val analysis = FishingStoryAnalysis(
            totalFish = totalFish,
            speciesFound = speciesFound,
            estimatedDuration = duration,
            location = location,
            techniques = techniques,
            baits = baits,
            timeOfDay = timeOfDay,
            weather = weather,
            hasSuccessIndicators = hasSuccess,
            storyType = storyType
        )

        // Guardar análisis para estadísticas
        saveAnalysis(text, analysis)

        return analysis
    }

    private fun detectSpeciesAndQuantities(text: String): List<SpeciesCatch> {
        val foundSpecies = mutableListOf<SpeciesCatch>()

        speciesPatterns.forEach { (species, patterns) ->
            patterns.forEach { pattern ->
                if (text.contains(pattern)) {
                    val quantity = extractQuantityNearSpecies(text, pattern)
                    val sizeIndicators = extractSizeIndicators(text, pattern)
                    val confidence = calculateConfidence(text, pattern, quantity)

                    foundSpecies.add(
                        SpeciesCatch(
                            species = species,
                            quantity = quantity,
                            sizeIndicators = sizeIndicators,
                            confidence = confidence
                        )
                    )
                }
            }
        }

        // Eliminar duplicados y mantener el de mayor confianza
        return foundSpecies
            .groupBy { it.species }
            .map { (_, catches) -> catches.maxByOrNull { it.confidence }!! }
    }

    private fun extractQuantityNearSpecies(text: String, speciesPattern: String): Int {
        val speciesIndex = text.indexOf(speciesPattern)
        if (speciesIndex == -1) return 1

        // Buscar números cerca de la especie (±30 caracteres)
        val startSearch = maxOf(0, speciesIndex - 30)
        val endSearch = minOf(text.length, speciesIndex + speciesPattern.length + 30)
        val nearbyText = text.substring(startSearch, endSearch)

        // Patrones de cantidad específicos para pesca
        val quantityPatterns = listOf(
            Regex("""(\d+)\s*${Regex.escape(speciesPattern)}"""),
            Regex("""${Regex.escape(speciesPattern)}.*?(\d+)"""),
            Regex("""(un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s*${Regex.escape(speciesPattern)}"""),
            Regex("""pesqu[éeí].*?(\d+).*?${Regex.escape(speciesPattern)}"""),
            Regex("""saq[uéeí].*?(\d+).*?${Regex.escape(speciesPattern)}"""),
            Regex("""${Regex.escape(speciesPattern)}.*?(un|una|dos|tres|cuatro|cinco)""")
        )

        quantityPatterns.forEach { pattern ->
            val match = pattern.find(nearbyText)
            if (match != null) {
                val numberStr = match.groupValues[1]

                // Convertir número o palabra a entero
                return numberPatterns[numberStr] ?: numberStr.toIntOrNull() ?: 1
            }
        }

        // Indicadores de cantidad múltiple sin número específico
        when {
            nearbyText.contains("varios") || nearbyText.contains("algunos") -> return 3
            nearbyText.contains("muchos") || nearbyText.contains("bastantes") -> return 5
            nearbyText.contains("pocos") -> return 2
            nearbyText.contains("nada") || nearbyText.contains("ninguno") -> return 0
            // Si hay plural pero no número específico
            speciesPattern.endsWith("s") && !speciesPattern.endsWith("is") -> return 2
        }

        return 1 // Por defecto, un pez
    }

    private fun extractSizeIndicators(text: String, speciesPattern: String): List<String> {
        val indicators = mutableListOf<String>()
        val speciesIndex = text.indexOf(speciesPattern)
        if (speciesIndex == -1) return indicators

        val nearbyText = text.substring(
            maxOf(0, speciesIndex - 50),
            minOf(text.length, speciesIndex + speciesPattern.length + 50)
        )

        // Patrones de tamaño
        val sizePatterns = mapOf(
            "grande" to listOf("grande", "grandote", "enorme", "tremendo", "gigante"),
            "mediano" to listOf("mediano", "medio", "regular", "normal"),
            "chico" to listOf("chico", "pequeño", "chiquito", "mini"),
            "peso" to listOf("kg", "kilo", "kilos", "gramos", "gr"),
            "longitud" to listOf("cm", "centímetros", "metros", "largo")
        )

        sizePatterns.forEach { (category, patterns) ->
            patterns.forEach { pattern ->
                if (nearbyText.contains(pattern)) {
                    indicators.add(category)
                }
            }
        }

        return indicators.distinct()
    }

    private fun calculateConfidence(text: String, pattern: String, quantity: Int): Double {
        var confidence = 0.5 // Base

        // Aumentar confianza según contexto
        if (text.contains("pesqu") || text.contains("saq") || text.contains("captur")) confidence += 0.3
        if (quantity > 0) confidence += 0.1
        if (text.contains(pattern) && text.length > 20) confidence += 0.1

        return minOf(1.0, confidence)
    }

    private fun detectDuration(text: String): String? {
        val durationPatterns = listOf(
            Regex("""(\d+)\s*horas?"""),
            Regex("""(\d+)\s*hs?"""),
            Regex("""todo\s+el\s+día"""),
            Regex("""mañana\s+completa"""),
            Regex("""tarde\s+completa"""),
            Regex("""desde.*hasta"""),
            Regex("""(\d+)\s+a\s+(\d+)""")
        )

        durationPatterns.forEach { pattern ->
            val match = pattern.find(text)
            if (match != null) {
                return match.value
            }
        }

        return null
    }

    private fun detectLocation(text: String): String? {
        val locationPatterns = listOf(
            "paraná", "uruguay", "de la plata", "río", "laguna", "embalse",
            "rosario", "santa fe", "tigre", "concordia", "chascomús",
            "san roque", "iguazú", "delta", "costa", "puerto"
        )

        locationPatterns.forEach { location ->
            if (text.contains(location)) {
                return location
            }
        }

        return null
    }

    private fun detectTechniques(text: String): List<String> {
        return techniquePatterns.filter { technique ->
            text.contains(technique)
        }
    }

    private fun detectBaits(text: String): List<String> {
        return baitPatterns.filter { bait ->
            text.contains(bait)
        }
    }

    private fun detectTimeOfDay(text: String): String? {
        return when {
            text.contains("amanecer") || text.contains("alba") || text.contains("madrugada") -> "amanecer"
            text.contains("mañana") || text.contains("matutino") -> "mañana"
            text.contains("mediodía") || text.contains("siesta") -> "mediodía"
            text.contains("tarde") || text.contains("vespertino") -> "tarde"
            text.contains("atardecer") || text.contains("ocaso") -> "atardecer"
            text.contains("noche") || text.contains("nocturno") -> "noche"
            else -> null
        }
    }

    private fun detectWeather(text: String): String? {
        return when {
            text.contains("sol") || text.contains("soleado") || text.contains("despejado") -> "soleado"
            text.contains("nublado") || text.contains("nube") -> "nublado"
            text.contains("lluvia") || text.contains("llovió") || text.contains("mojado") -> "lluvioso"
            text.contains("viento") || text.contains("ventoso") -> "ventoso"
            text.contains("frío") || text.contains("fría") -> "frío"
            text.contains("calor") || text.contains("caluroso") -> "caluroso"
            else -> null
        }
    }

    private fun detectSuccessIndicators(text: String): Boolean {
        val successWords = listOf(
            "excelente", "buena", "genial", "bárbaro", "espectacular",
            "increíble", "exitosa", "productiva", "muchos", "varios"
        )

        val failureWords = listOf(
            "mala", "terrible", "nada", "sin suerte", "fracaso",
            "malo", "pésimo", "ninguno", "cero"
        )

        val successCount = successWords.count { text.contains(it) }
        val failureCount = failureWords.count { text.contains(it) }

        return successCount > failureCount
    }

    private fun classifyStoryType(text: String, totalFish: Int, hasSuccess: Boolean): String {
        return when {
            totalFish == 0 && !hasSuccess -> "mala"
            totalFish >= 5 || hasSuccess -> "exitosa"
            text.contains("técnica") || text.contains("carnada") || text.contains("método") -> "técnica"
            else -> "regular"
        }
    }

    private fun saveAnalysis(originalText: String, analysis: FishingStoryAnalysis) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp | Total: ${analysis.totalFish} | Especies: ${analysis.speciesFound.size} | Tipo: ${analysis.storyType}\n"
            analysisLogFile.appendText(logEntry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun buildAnalysisResponse(analysis: FishingStoryAnalysis): String {
        val response = StringBuilder()

        response.append("📊 **Análisis de relato de pesca:**\n\n")

        // Resumen de capturas
        if (analysis.totalFish > 0) {
            response.append("🎣 **Total de peces:** ${analysis.totalFish}\n\n")

            if (analysis.speciesFound.isNotEmpty()) {
                response.append("🐟 **Especies identificadas:**\n")
                analysis.speciesFound.forEach { catch ->
                    val sizeInfo = if (catch.sizeIndicators.isNotEmpty()) {
                        " (${catch.sizeIndicators.joinToString(", ")})"
                    } else ""
                    response.append("• ${catch.species}: ${catch.quantity} pez${if (catch.quantity > 1) "es" else ""}$sizeInfo\n")
                }
                response.append("\n")
            }
        } else {
            response.append("🎣 **Jornada sin capturas** - ¡Pero es parte de la pesca!\n\n")
        }

        // Detalles técnicos
        if (analysis.techniques.isNotEmpty()) {
            response.append("🎯 **Técnicas usadas:** ${analysis.techniques.joinToString(", ")}\n")
        }

        if (analysis.baits.isNotEmpty()) {
            response.append("🎣 **Carnadas/Señuelos:** ${analysis.baits.joinToString(", ")}\n")
        }

        // Condiciones
        analysis.timeOfDay?.let {
            response.append("⏰ **Horario:** $it\n")
        }

        analysis.weather?.let {
            response.append("🌤️ **Clima:** $it\n")
        }

        analysis.location?.let {
            response.append("📍 **Zona:** $it\n")
        }

        analysis.estimatedDuration?.let {
            response.append("⏱️ **Duración:** $it\n")
        }

        // Evaluación
        response.append("\n🏆 **Evaluación:** ")
        when (analysis.storyType) {
            "exitosa" -> response.append("Buena Pesca! Excelentes resultados ")
            "regular" -> response.append("traqui, buenos momentos de pesca ")
            "mala" -> response.append("Día difícil, pero así es la pesca. La próxima será mejor! ")
            "técnica" -> response.append("Relato técnico interesante, buenos datos 📚")
        }

        return response.toString()
    }

    fun getAnalysisStats(): String {
        return try {
            val totalAnalysis = if (analysisLogFile.exists()) {
                analysisLogFile.readLines().size
            } else 0

            "📊 Relatos analizados: $totalAnalysis"
        } catch (e: Exception) {
            "📊 Estadísticas no disponibles"
        }
    }
}