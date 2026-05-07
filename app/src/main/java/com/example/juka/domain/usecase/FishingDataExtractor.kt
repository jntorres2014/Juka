package com.example.juka.domain.usecase

import android.app.Application
import com.example.juka.util.DateUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FishingData(
    var day: String? = null,
    var startTime: String? = null,
    var endTime: String? = null,
    var fishCount: Int? = null,
    var type: String? = null,
    var rodsCount: Int? = null,
    var photoUri: String? = null
)

class FishingDataExtractor(private val application: Application) {

    private val currentSession = FishingData()
    private val dataLogFile = File(application.filesDir, "fishing_data_log.txt")

    // 🐟 ESPECIES ARGENTINAS COMUNES (para detectar cantidades)
    private val fishSpecies = listOf(
        "dorado", "dorados", "doradito", "doraditos",
        "surubí", "surubís", "surubi", "surubis", "pintado", "pintados",
        "pacú", "pacús", "pacu", "pacus",
        "pejerrey", "pejerreyes", "pejerrei",
        "tararira", "tarariras", "tarira", "tariras",
        "sábalo", "sábalos", "sabalo", "sabalos",
        "boga", "bogas",
        "bagre", "bagres", "gato", "gatos",
        "carpa", "carpas",
        "trucha", "truchas",
        "salmón", "salmones", "salmon", "salmones",
        "mojarra", "mojarras", "mojarrita", "mojarritas"
    )

    // 📅 PATRONES DE DÍAS MEJORADOS
    private val dayPatterns = mapOf(
        "hoy" to getTodayDate(),
        "ayer" to getYesterdayDate(),
        "anteayer" to getDaysAgo(2),
        "antes de ayer" to getDaysAgo(2),
        "el lunes" to getLastWeekday(Calendar.MONDAY),
        "el martes" to getLastWeekday(Calendar.TUESDAY),
        "el miércoles" to getLastWeekday(Calendar.WEDNESDAY),
        "el jueves" to getLastWeekday(Calendar.THURSDAY),
        "el viernes" to getLastWeekday(Calendar.FRIDAY),
        "el sábado" to getLastWeekday(Calendar.SATURDAY),
        "el domingo" to getLastWeekday(Calendar.SUNDAY)
    )

    // ⏰ PATRONES DE HORARIO FLEXIBLES
    private val timeRangePatterns = listOf(
        // "de 7 a 11", "desde las 6 hasta las 10"
        Regex("""de\s+(\d{1,2}):?(\d{2})?\s+a\s+(?:las\s+)?(\d{1,2}):?(\d{2})?"""),
        Regex("""desde\s+(?:las\s+)?(\d{1,2}):?(\d{2})?\s+hasta\s+(?:las\s+)?(\d{1,2}):?(\d{2})?"""),
        Regex("""entre\s+(?:las\s+)?(\d{1,2}):?(\d{2})?\s+hasta\s+(?:las\s+)?(\d{1,2}):?(\d{2})?"""),
        Regex("""de\s+las\s+(\d{1,2}):?(\d{2})?\s+a\s+las\s+(\d{1,2}):?(\d{2})?"""),
        // "de 8 a 12hs", "desde las 6 hasta mediodía"
        Regex("""de\s+(\d{1,2})\s+a\s+(\d{1,2})(?:hs?)?"""),
        Regex("""desde\s+(?:las\s+)?(\d{1,2})\s+hasta\s+(?:el\s+)?(?:mediodía|mediodia)""")
    )

    // 🕐 PATRONES DE HORA INDIVIDUAL
    private val singleTimePatterns = listOf(
        Regex("""(?:empec[éeí]|comenc[éeí]|arranqu[éeí])\s+(?:a\s+las\s+)?(\d{1,2}):?(\d{2})?"""),
        Regex("""(?:termin[éeí]|acab[éeí])\s+(?:a\s+las\s+)?(\d{1,2}):?(\d{2})?"""),
        Regex("""(?:a\s+las\s+)?(\d{1,2}):?(\d{2})?\s+(?:de\s+la\s+)?(?:mañana|tarde|noche)""")
    )

    // 🐟 PATRONES DE CANTIDAD CON ESPECIES
    private val fishCountPatterns = listOf(
        // "2 pejerreyes", "tres dorados", "un surubí"
        Regex(
            """(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s+(${
                fishSpecies.joinToString(
                    "|"
                )
            })"""
        ),
        // "saqué 3 dorados", "pesqué dos bagres"
        Regex(
            """(?:saqu[éeí]|pesqu[éeí]|captur[éeí])\s+(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s+(${
                fishSpecies.joinToString(
                    "|"
                )
            })"""
        ),
        // "dorados: 2", "pejerreyes 3"
        Regex("""(${fishSpecies.joinToString("|")})\s*:?\s*(\d+)"""),
        // Patrones generales con "piezas"
        Regex("""(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s+(?:piezas|pescados?|capturas?)"""),
        Regex("""(?:saqu[éeí]|pesqu[éeí]|captur[éeí])\s+(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s+(?:piezas|pescados?|capturas?)""")
    )

    // 🎣 PATRONES DE CAÑAS MEJORADOS
    private val rodCountPatterns = listOf(
        Regex("""con\s+(\d+|un|una|dos|tres|cuatro|cinco)\s+(?:cañas?|varas?)"""),
        Regex("""(?:usando|use)\s+(\d+|un|una|dos|tres|cuatro|cinco)\s+(?:cañas?|varas?)"""),
        Regex("""(\d+|un|una|dos|tres|cuatro|cinco)\s+(?:cañas?|varas?)""")
    )

    // 🚤 PATRONES DE TIPO AMPLIADOS
    private val typePatterns = mapOf(
        "embarcado" to listOf(
            "embarcado", "embarcados", "en barco", "en bote", "en lancha",
            "navegando", "desde el barco", "desde la lancha", "en el río navegando"
        ),
        "costa" to listOf(
            "costa", "de costa", "desde la costa", "orilla", "desde la orilla",
            "playa", "muelle", "desde el muelle", "barranca", "margen", "desde tierra"
        )
    )

    // 🔢 NÚMEROS EN PALABRAS
    private val wordToNumber = mapOf(
        "un" to 1, "una" to 1, "uno" to 1,
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20
    )

    fun extractFromMessage(message: String, photoUri: String? = null): FishingData {
        val lowerMessage = message.lowercase().trim()

        // 📅 EXTRAER DÍA
        extractDay(lowerMessage)

        // ⏰ EXTRAER HORARIOS
        extractTimeRanges(lowerMessage)
        extractSingleTimes(lowerMessage)

        // 🐟 EXTRAER CANTIDADES DE PECES
        extractFishCounts(lowerMessage)

        // 🎣 EXTRAER CANTIDAD DE CAÑAS
        extractRodCounts(lowerMessage)

        // 🚤 EXTRAER TIPO DE PESCA
        extractFishingType(lowerMessage)

        // 📸 FOTO
        if (photoUri != null) {
            currentSession.photoUri = photoUri
        }

        saveToLog(currentSession)
        return currentSession.copy()
    }

    private fun extractDay(message: String) {
        if (currentSession.day != null) return

        // Buscar patrones de días conocidos
        dayPatterns.entries.find { (pattern, _) ->
            message.contains(pattern)
        }?.let {
            currentSession.day = it.value
            return
        }

        // Buscar fechas exactas
        Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})""").find(message)?.let { match ->
            currentSession.day = formatDate(match.value)
            return
        }

        // Buscar "el [día] pasado"
        Regex("""el\s+(lunes|martes|miércoles|jueves|viernes|sábado|domingo)\s+pasado""")
            .find(message)?.let { match ->
                val weekday = match.groupValues[1]
                currentSession.day = dayPatterns["el $weekday"]
                return
            }
    }

    private fun extractTimeRanges(message: String) {
        timeRangePatterns.forEach { pattern ->
            pattern.find(message)?.let { match ->
                val groups = match.groupValues

                when {
                    groups.size >= 4 -> {
                        // Formato completo: "de 7:30 a 11:45"
                        val startHour = groups[1].toIntOrNull() ?: return@let
                        val startMin = groups[2].toIntOrNull() ?: 0
                        val endHour = groups[3].toIntOrNull() ?: return@let
                        val endMin = groups[4].toIntOrNull() ?: 0

                        currentSession.startTime = String.format("%02d:%02d", startHour, startMin)
                        currentSession.endTime = String.format("%02d:%02d", endHour, endMin)
                    }

                    groups.size >= 3 -> {
                        // Formato simple: "de 7 a 11"
                        val startHour = groups[1].toIntOrNull() ?: return@let
                        val endHour = groups[2].toIntOrNull() ?: return@let

                        currentSession.startTime = String.format("%02d:00", startHour)
                        currentSession.endTime = String.format("%02d:00", endHour)
                    }
                }
                return // Encontrado, salir
            }
        }
    }

    private fun extractSingleTimes(message: String) {
        singleTimePatterns.forEach { pattern ->
            pattern.find(message)?.let { match ->
                val hour = match.groupValues[1].toIntOrNull() ?: return@let
                val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val timeStr = String.format("%02d:%02d", hour, minute)

                when {
                    match.value.contains("empec") || match.value.contains("comenc") || match.value.contains(
                        "arranqu"
                    ) -> {
                        currentSession.startTime = timeStr
                    }

                    match.value.contains("termin") || match.value.contains("acab") -> {
                        currentSession.endTime = timeStr
                    }
                }
            }
        }
    }

    private fun extractFishCounts(message: String) {
        if (currentSession.fishCount != null) return

        fishCountPatterns.forEach { pattern ->
            pattern.find(message)?.let { match ->
                val countStr = match.groupValues[1].lowercase()
                val count = wordToNumber[countStr] ?: countStr.toIntOrNull() ?: return@let

                if (count > 0 && count <= 50) { // Validación razonable
                    currentSession.fishCount = count
                    return // Encontrado, salir
                }
            }
        }
    }

    private fun extractRodCounts(message: String) {
        if (currentSession.rodsCount != null) return

        rodCountPatterns.forEach { pattern ->
            pattern.find(message)?.let { match ->
                val countStr = match.groupValues[1].lowercase()
                val count = wordToNumber[countStr] ?: countStr.toIntOrNull() ?: return@let

                if (count > 0 && count <= 10) { // Validación razonable
                    currentSession.rodsCount = count
                    return // Encontrado, salir
                }
            }
        }
    }

    private fun extractFishingType(message: String) {
        if (currentSession.type != null) return

        typePatterns.entries.forEach { (type, patterns) ->
            patterns.forEach { pattern ->
                if (message.contains(pattern)) {
                    currentSession.type = type
                    return // Encontrado, salir
                }
            }
        }
    }

    fun getMissingFields(data: FishingData): List<String> {
        return listOfNotNull(
            if (data.day == null) "¿Qué día fue la pesca?" else null,
            if (data.startTime == null) "¿A qué hora empezaste?" else null,
            if (data.endTime == null) "¿A qué hora terminaste?" else null,
            if (data.fishCount == null) "¿Cuántos pescados sacaste?" else null,
            if (data.type == null) "¿Fue de costa embarcado?" else null,
            if (data.rodsCount == null) "¿Cuántas cañas usaste?" else null,
            if (data.photoUri == null) "¿Querés subir una foto?" else null
        )
    }

    fun resetSession() {
        currentSession.apply {
            day = null; startTime = null; endTime = null
            fishCount = null; type = null; rodsCount = null; photoUri = null
        }
    }

    // HELPER FUNCTIONS
    private fun getTodayDate() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun getYesterdayDate(): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun getDaysAgo(days: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun getLastWeekday(weekday: Int): String {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != weekday) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun formatDate(dateStr: String): String {
        // Convertir diferentes formatos a yyyy-MM-dd
        return try {
            val parts = dateStr.split(Regex("[/\\-]"))
            if (parts.size == 3) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
                "$year-$month-$day"
            } else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun saveToLog(data: FishingData) {
        try {
            val timestamp = DateUtils.timestampFull()
            val entry =
                "$timestamp | Día: ${data.day} | Inicio: ${data.startTime} | Fin: ${data.endTime} | Piezas: ${data.fishCount} | Tipo: ${data.type} | Cañas: ${data.rodsCount} | Foto: ${data.photoUri}\n"
            dataLogFile.appendText(entry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}

// Al final del archivo
fun FishingData.copy(
    day: String? = this.day,
    startTime: String? = this.startTime,
    endTime: String? = this.endTime,
    fishCount: Int? = this.fishCount,
    type: String? = this.type,
    rodsCount: Int? = this.rodsCount,
    photoUri: String? = this.photoUri
) = FishingData(day, startTime, endTime, fishCount, type, rodsCount, photoUri)