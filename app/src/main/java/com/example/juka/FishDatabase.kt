// FishDatabase.kt - VERSIÓN ACTUALIZADA PARA CARGAR DESDE JSON
package com.example.juka

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class FishInfo(
    val name: String,
    val scientificName: String,
    val habitat: String,
    val bestBaits: List<String>,
    val bestTime: String,
    val technique: String,
    val avgSize: String,
    val season: String
)

class FishDatabase(private val context: Context) {

    var fishSpeciesDB: Map<String, FishInfo> = emptyMap()
    private var isLoaded = false

    companion object {
        private const val TAG = "🐟 FishDatabase"
    }

    /**
     * Inicializa la base de datos cargando desde archivo JSON
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        android.util.Log.d(TAG, "🔄 Inicializando base de datos de peces desde JSON")

        try {
            // Cargar desde assets/peces_argentinos.json
            val inputStream = context.assets.open("peces_argentinos.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()

            val jsonArray = JSONArray(jsonString)
            val pecesMap = mutableMapOf<String, FishInfo>()

            for (i in 0 until jsonArray.length()) {
                val pezJson = jsonArray.getJSONObject(i)
                val fishInfo = parsearPezDesdeJson(pezJson)

                // Agregar el nombre principal
                pecesMap[fishInfo.name.lowercase()] = fishInfo

                // Agregar sinónimos si existen
                if (pezJson.has("sinonimos")) {
                    val sinonimos = pezJson.getJSONArray("sinonimos")
                    for (j in 0 until sinonimos.length()) {
                        val sinonimo = sinonimos.getString(j).lowercase()
                        pecesMap[sinonimo] = fishInfo
                    }
                }
            }

            fishSpeciesDB = pecesMap
            isLoaded = true

            android.util.Log.i(TAG, "✅ Base de datos inicializada con ${fishSpeciesDB.size} especies y sinónimos")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error cargando peces desde JSON: ${e.message}", e)
            // Cargar datos por defecto como fallback
            fishSpeciesDB = cargarPecesPorDefecto()
            isLoaded = true
            android.util.Log.w(TAG, "⚠️ Usando datos por defecto como fallback")
        }
    }

    private fun parsearPezDesdeJson(pezJson: JSONObject): FishInfo {
        return FishInfo(
            name = pezJson.getString("nombre"),
            scientificName = pezJson.optString("cientifico", ""),
            habitat = pezJson.optString("habitat", "Aguas argentinas"),
            bestBaits = jsonArrayToList(pezJson.optJSONArray("carnadas")),
            bestTime = pezJson.optString("mejor_horario", "Todo el día"),
            technique = pezJson.optString("tecnica", "Pesca general"),
            avgSize = pezJson.optString("tamaño", "Variable"),
            season = pezJson.optString("temporada", "Todo el año")
        )
    }

    private fun jsonArrayToList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return listOf("carnada natural")
        return (0 until jsonArray.length()).map { jsonArray.getString(it) }
    }

    private fun cargarPecesPorDefecto(): Map<String, FishInfo> {
        android.util.Log.i(TAG, "🔄 Cargando peces por defecto")
        return mapOf(
            "dorado" to FishInfo(
                name = "Dorado",
                scientificName = "Salminus brasiliensis",
                habitat = "Ríos de corriente fuerte",
                bestBaits = listOf("carnada viva", "señuelos plateados"),
                bestTime = "amanecer y atardecer",
                technique = "spinning",
                avgSize = "3-8 kg",
                season = "octubre a abril"
            ),
            "pejerrey" to FishInfo(
                name = "Pejerrey",
                scientificName = "Odontesthes bonariensis",
                habitat = "Lagunas pampeanas",
                bestBaits = listOf("lombriz", "cascarudos"),
                bestTime = "todo el día",
                technique = "boya",
                avgSize = "200g-1kg",
                season = "marzo a noviembre"
            ),
            "corvina" to FishInfo(
                name = "Corvina",
                scientificName = "Micropogonias furnieri",
                habitat = "Costas atlánticas",
                bestBaits = listOf("camarones", "lombriz de mar"),
                bestTime = "mañana y tarde",
                technique = "surf casting",
                avgSize = "30-80 cm",
                season = "verano"
            )
        )
    }

    fun findFishByKeyword(keyword: String): FishInfo? {
        return fishSpeciesDB[keyword.lowercase()]
    }

    fun findLocalFishInfo(scientificName: String, commonName: String): FishInfo? {
        // Buscar por nombre científico exacto
        fishSpeciesDB.values.find {
            it.scientificName.equals(scientificName, ignoreCase = true)
        }?.let { return it }

        // Buscar por nombre común
        val lowerCommonName = commonName.lowercase()
        fishSpeciesDB.entries.find { (key, value) ->
            lowerCommonName.contains(key) || lowerCommonName.contains(value.name.lowercase())
        }?.value?.let { return it }

        return null
    }

    fun getAllSpecies(): List<FishInfo> {
        return fishSpeciesDB.values.distinctBy { it.name }
    }

    fun searchSpecies(query: String): List<FishInfo> {
        val lowerQuery = query.lowercase()
        return fishSpeciesDB.values.filter { fishInfo ->
            fishInfo.name.lowercase().contains(lowerQuery) ||
                    fishInfo.scientificName.lowercase().contains(lowerQuery) ||
                    fishInfo.habitat.lowercase().contains(lowerQuery)
        }.distinctBy { it.name }
    }

    /**
     * Obtiene todas las claves (nombres y sinónimos) para una especie
     */
    fun getKeysForSpecies(speciesName: String): List<String> {
        return fishSpeciesDB.entries
            .filter { it.value.name.equals(speciesName, ignoreCase = true) }
            .map { it.key }
    }

    /**
     * Recargar base de datos (útil para desarrollo)
     */
    suspend fun reload() {
        isLoaded = false
        initialize()
    }

    /**
     * Obtiene estadísticas de la base de datos
     */
    fun getStats(): String {
        val totalEntries = fishSpeciesDB.size
        val uniqueSpecies = fishSpeciesDB.values.distinctBy { it.name }.size
        val synonyms = totalEntries - uniqueSpecies

        return "📊 Especies únicas: $uniqueSpecies | Total con sinónimos: $totalEntries | Sinónimos: $synonyms"
    }
}