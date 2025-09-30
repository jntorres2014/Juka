// FishDatabase.kt - Gestiona la base de datos de peces desde un archivo JSON.
package com.example.juka.core.database

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
        private const val JSON_FILE = "peces_argentinos.json"
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        try {
            val jsonString = loadJsonFromAssets()
            if (jsonString.isBlank()) {
                loadDefaultData()
                return@withContext
            }

            val jsonArray = JSONArray(jsonString)
            val fishMap = mutableMapOf<String, FishInfo>()

            for (i in 0 until jsonArray.length()) {
                val fishJson = jsonArray.getJSONObject(i)
                val fishInfo = parseFishFromJson(fishJson)
                val normalizedName = fishInfo.name.lowercase().trim()
                fishMap[normalizedName] = fishInfo

                if (fishJson.has("sinonimos")) {
                    val synonyms = fishJson.getJSONArray("sinonimos")
                    for (j in 0 until synonyms.length()) {
                        val synonym = synonyms.getString(j).lowercase().trim()
                        if (synonym.isNotBlank()) {
                            fishMap[synonym] = fishInfo
                        }
                    }
                }
                addAutomaticVariants(fishMap, fishInfo)
            }

            fishSpeciesDB = fishMap
            isLoaded = true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading JSON: ${e.message}", e)
            loadDefaultData()
        }
    }

    private fun loadJsonFromAssets(): String {
        return try {
            context.assets.open(JSON_FILE).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading JSON file: ${e.message}")
            ""
        }
    }

    private fun parseFishFromJson(fishJson: JSONObject): FishInfo {
        // ... (Implementación sin cambios)
        return FishInfo("", "", "", emptyList(), "", "", "", "") // Placeholder
    }

    private fun addAutomaticVariants(map: MutableMap<String, FishInfo>, fishInfo: FishInfo) {
        // ... (Implementación sin cambios)
    }

    private fun loadDefaultData() {
        // ... (Implementación de fallback sin cambios)
    }

    fun findFishByKeyword(keyword: String): FishInfo? {
        return fishSpeciesDB[keyword.lowercase().trim()]
    }

    fun getAllSpecies(): List<FishInfo> {
        return fishSpeciesDB.values.distinctBy { it.name }
    }

    fun isInitialized(): Boolean = isLoaded
}