package com.example.juka

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FishingData(
    var day: String? = null,  // e.g., "2025-08-20"
    var startTime: String? = null,  // e.g., "08:00"
    var endTime: String? = null,  // e.g., "12:00"
    var fishCount: Int? = null,  // Cantidad de piezas
    var type: String? = null,  // "embarcado" o "costa"
    var rodsCount: Int? = null,  // Cantidad de cañas
    var photoUri: String? = null  // URI de la foto
)

class FishingDataExtractor(private val application: Application) {

    private val currentSession = FishingData()  // Estado de la sesión actual
    private val dataLogFile = File(application.filesDir, "fishing_data_log.txt")

    // Patrones para extracción
    private val dayPatterns = mapOf(
        "hoy" to getTodayDate(),
        "ayer" to getYesterdayDate()
    )

    // Regex para fecha exacta (formato YYYY-MM-DD)
    private val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")

    private val timePatterns = Regex("(\\d{1,2}):?(\\d{2})?")  // Horas como "8:00" o "8"
    private val countPatterns = Regex("(\\d+)")  // Números para fishCount/rodsCount
    private val typePatterns = mapOf(
        "embarcado" to listOf("embarcado", "barco", "bote", "lancha"),
        "costa" to listOf("costa", "orilla", "playa", "muelle")
    )
    private val durationPatterns = Regex("(\\d+)hs?|(\\d+) horas?")  // e.g., "4hs"

    fun extractFromMessage(message: String, photoUri: String? = null): FishingData {
        val lowerMessage = message.lowercase()

        // Extraer día - primero buscar palabras clave, luego regex
        dayPatterns.entries.find { (key, _) -> lowerMessage.contains(key) }?.let {
            currentSession.day = it.value
        }

        // Si no encontró palabras clave, buscar fecha exacta
        if (currentSession.day == null) {
            dateRegex.find(lowerMessage)?.let { match ->
                currentSession.day = match.value
            }
        }

        // Extraer horas (inicio/fin o duración)
        val times = timePatterns.findAll(lowerMessage).toList()
        if (times.isNotEmpty()) {
            // Formatear la primera hora encontrada
            val firstTime = formatTime(times.first().value)
            if (firstTime != null) {
                currentSession.startTime = firstTime
            }

            // Si hay una segunda hora, usarla como hora de fin
            if (times.size > 1) {
                val secondTime = formatTime(times[1].value)
                if (secondTime != null) {
                    currentSession.endTime = secondTime
                }
            } else {
                // Si solo hay una hora, buscar duración para calcular la hora de fin
                durationPatterns.find(lowerMessage)?.let { match ->
                    val hours = match.groupValues[1].toIntOrNull() ?: match.groupValues[2].toIntOrNull() ?: 0
                    currentSession.endTime = addHoursToTime(currentSession.startTime ?: "00:00", hours)
                }
            }
        }

        // Extraer cantidades (buscar contexto: cerca de "piezas" o "cañas")
        val numbers = countPatterns.findAll(lowerMessage).map { it.value.toIntOrNull() }.filterNotNull()

        // Buscar palabras clave para determinar qué número corresponde a qué
        if (lowerMessage.contains("piezas") || lowerMessage.contains("pescados") || lowerMessage.contains("pescado")) {
            val fishKeywordIndex = maxOf(
                lowerMessage.indexOf("piezas"),
                lowerMessage.indexOf("pescados"),
                lowerMessage.indexOf("pescado")
            )
            // Buscar el número más cercano a estas palabras
            countPatterns.findAll(lowerMessage).minByOrNull {
                kotlin.math.abs(it.range.first - fishKeywordIndex)
            }?.value?.toIntOrNull()?.let {
                currentSession.fishCount = it
            }
        }

        if (lowerMessage.contains("cañas") || lowerMessage.contains("varas") || lowerMessage.contains("caña")) {
            val rodKeywordIndex = maxOf(
                lowerMessage.indexOf("cañas"),
                lowerMessage.indexOf("varas"),
                lowerMessage.indexOf("caña")
            )
            // Buscar el número más cercano a estas palabras
            countPatterns.findAll(lowerMessage).minByOrNull {
                kotlin.math.abs(it.range.first - rodKeywordIndex)
            }?.value?.toIntOrNull()?.let {
                currentSession.rodsCount = it
            }
        }

        // Si no hay contexto específico, usar el primer número para peces
        //if ((currentSession.fishCount == null) && numbers.isNotEmpty()) {
          //  currentSession.fishCount = numbers.first()
       // }

        // Extraer tipo
        typePatterns.entries.find { (_, patterns) ->
            patterns.any { pattern -> lowerMessage.contains(pattern) }
        }?.let {
            currentSession.type = it.key
        }

        // Foto
        if (photoUri != null) {
            currentSession.photoUri = photoUri
        }

        saveToLog(currentSession)
        return currentSession.copy()  // Retorna copia para no mutar
    }

    fun getMissingFields(data: FishingData): List<String> {
        return listOfNotNull(
            if (data.day == null) "¿Qué día fue la pesca? (e.g., hoy, ayer, 2025-08-20)" else null,
            if (data.startTime == null) "¿A qué hora empezaste?" else null,
            if (data.endTime == null) "¿A qué hora terminaste? (o duración en hs)" else null,
            if (data.fishCount == null) "¿Cuántas piezas pescaste?" else null,
            if (data.type == null) "¿Fue pesca embarcado o de costa?" else null,
            if (data.rodsCount == null) "¿Cuántas cañas usaste?" else null,
            if (data.photoUri == null) "¿Quieres subir una foto?" else null
        )
    }

    fun resetSession() {
        currentSession.apply {
            day = null
            startTime = null
            endTime = null
            fishCount = null
            type = null
            rodsCount = null
            photoUri = null
        }
    }

    private fun saveToLog(data: FishingData) {
        try {
            val timestamp = getCurrentTimestamp()
            val entry = "$timestamp | Día: ${data.day} | Inicio: ${data.startTime} | Fin: ${data.endTime} | Piezas: ${data.fishCount} | Tipo: ${data.type} | Cañas: ${data.rodsCount} | Foto: ${data.photoUri}\n"
            dataLogFile.appendText(entry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Helpers
    private fun getTodayDate() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun getYesterdayDate(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun formatTime(timeString: String): String? {
        return try {
            // Eliminar caracteres no numéricos excepto ":"
            val cleanTime = timeString.replace(Regex("[^\\d:]"), "")
            val parts = cleanTime.split(":").map { it.toIntOrNull() ?: 0 }

            if (parts.isNotEmpty()) {
                val hour = parts[0]
                val minute = if (parts.size > 1) parts[1] else 0

                // Validar hora (0-23) y minutos (0-59)
                if (hour in 0..23 && minute in 0..59) {
                    String.format("%02d:%02d", hour, minute)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun addHoursToTime(time: String, hours: Int): String {
        return try {
            val parts = time.split(":").map { it.toIntOrNull() ?: 0 }
            val newHour = (parts[0] + hours) % 24  // Usar módulo para manejar overflow
            val newMin = parts.getOrElse(1) { 0 }
            String.format("%02d:%02d", newHour, newMin)
        } catch (e: Exception) {
            "00:00"  // Valor por defecto en caso de error
        }
    }

    private fun getCurrentTimestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}