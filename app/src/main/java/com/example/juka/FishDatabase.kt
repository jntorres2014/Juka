// FishDatabase.kt - VERSIÓN ARREGLADA PARA CARGAR JSON CORRECTAMENTE
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
        private const val JSON_FILE = "peces_argentinos.json"
    }

    /**
     * Inicializa la base de datos cargando desde archivo JSON
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isLoaded) {
            android.util.Log.d(TAG, "✅ Base de datos ya inicializada")
            return@withContext
        }

        android.util.Log.d(TAG, "🔄 Inicializando base de datos de peces desde JSON...")

        try {
            // 📄 CARGAR JSON DESDE ASSETS
            val jsonString = cargarJsonDesdeAssets()

            if (jsonString.isBlank()) {
                android.util.Log.e(TAG, "❌ Archivo JSON vacío o no encontrado")
                cargarDatosPorDefecto()
                return@withContext
            }

            // 🔄 PARSEAR JSON
            val jsonArray = JSONArray(jsonString)
            val pecesMap = mutableMapOf<String, FishInfo>()

            android.util.Log.d(TAG, "📊 Procesando ${jsonArray.length()} especies del JSON...")

            for (i in 0 until jsonArray.length()) {
                try {
                    val pezJson = jsonArray.getJSONObject(i)
                    val fishInfo = parsearPezDesdeJson(pezJson)

                    // Agregar el nombre principal (normalizado)
                    val nombreNormalizado = fishInfo.name.lowercase().trim()
                    pecesMap[nombreNormalizado] = fishInfo

                    // 🔄 AGREGAR SINÓNIMOS SI EXISTEN
                    if (pezJson.has("sinonimos")) {
                        val sinonimos = pezJson.getJSONArray("sinonimos")
                        for (j in 0 until sinonimos.length()) {
                            val sinonimo = sinonimos.getString(j).lowercase().trim()
                            if (sinonimo.isNotBlank()) {
                                pecesMap[sinonimo] = fishInfo
                                android.util.Log.v(TAG, "  📝 Sinónimo: '$sinonimo' → ${fishInfo.name}")
                            }
                        }
                    }

                    // 🔄 AGREGAR VARIANTES AUTOMÁTICAS
                    agregarVariantesAutomaticas(pecesMap, fishInfo)

                } catch (e: Exception) {
                    android.util.Log.w(TAG, "⚠️ Error procesando pez $i: ${e.message}")
                }
            }

            fishSpeciesDB = pecesMap
            isLoaded = true

            android.util.Log.i(TAG, "✅ Base de datos inicializada exitosamente!")
            android.util.Log.i(TAG, "📊 Total entradas: ${fishSpeciesDB.size}")
            android.util.Log.i(TAG, "🐟 Especies únicas: ${fishSpeciesDB.values.distinctBy { it.name }.size}")

            // 🔍 MOSTRAR ALGUNAS ESPECIES CARGADAS (DEBUG)
            android.util.Log.d(TAG, "🔍 Especies principales cargadas:")
            fishSpeciesDB.values.distinctBy { it.name }.take(10).forEach { fish ->
                android.util.Log.d(TAG, "  - ${fish.name} (${fish.scientificName})")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error crítico cargando JSON: ${e.message}", e)
            cargarDatosPorDefecto()
        }
    }

    /**
     * 📄 CARGAR JSON DESDE ASSETS CON MANEJO DE ERRORES
     */
    private fun cargarJsonDesdeAssets(): String {
        return try {
            android.util.Log.d(TAG, "📂 Intentando cargar: $JSON_FILE desde assets/")

            val inputStream = context.assets.open(JSON_FILE)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val jsonString = reader.readText()
            reader.close()
            inputStream.close()

            android.util.Log.d(TAG, "📄 JSON cargado: ${jsonString.length} caracteres")
            android.util.Log.d(TAG, "🔍 Primeros 200 chars: ${jsonString.take(200)}...")

            jsonString

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error leyendo archivo JSON: ${e.message}", e)

            // 🔍 VERIFICAR QUÉ ARCHIVOS ESTÁN DISPONIBLES
            try {
                val assetsList = context.assets.list("")
                android.util.Log.d(TAG, "📁 Archivos disponibles en assets: ${assetsList?.joinToString(", ")}")
            } catch (e2: Exception) {
                android.util.Log.e(TAG, "No se pueden listar assets: ${e2.message}")
            }

            ""
        }
    }

    /**
     * 🐟 PARSEAR PEZ DESDE JSON CON VALIDACIONES
     */
    private fun parsearPezDesdeJson(pezJson: JSONObject): FishInfo {
        return FishInfo(
            name = pezJson.optString("nombre", "Pez desconocido").trim(),
            scientificName = pezJson.optString("cientifico", "").trim(),
            habitat = pezJson.optString("habitat", "Aguas argentinas").trim(),
            bestBaits = jsonArrayToList(pezJson.optJSONArray("carnadas")),
            bestTime = pezJson.optString("mejor_horario", "Todo el día").trim(),
            technique = pezJson.optString("tecnica", "Pesca general").trim(),
            avgSize = pezJson.optString("tamaÃ±o", "Variable").trim(), // Nota: conservar tamaÃ±o del JSON original
            season = pezJson.optString("temporada", "Todo el año").trim()
        )
    }

    /**
     * 🔄 CONVERTIR JSON ARRAY A LIST
     */
    private fun jsonArrayToList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null || jsonArray.length() == 0) {
            return listOf("carnada natural")
        }

        val lista = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            val carnada = jsonArray.optString(i, "").trim()
            if (carnada.isNotBlank()) {
                lista.add(carnada)
            }
        }

        return if (lista.isEmpty()) listOf("carnada natural") else lista
    }

    /**
     * 🔄 AGREGAR VARIANTES AUTOMÁTICAS (plural, diminutivos, etc.)
     */
    private fun agregarVariantesAutomaticas(mapa: MutableMap<String, FishInfo>, fishInfo: FishInfo) {
        val nombreBase = fishInfo.name.lowercase().trim()

        // Variantes comunes
        val variantes = mutableSetOf<String>()

        // Plurales simples
        if (!nombreBase.endsWith("s")) {
            variantes.add("${nombreBase}s")
        }

        // Casos especiales conocidos
        when (nombreBase) {
            "dorado" -> variantes.addAll(listOf("dorados", "doradito", "doraditos"))
            "surubí" -> variantes.addAll(listOf("surubís", "surubi", "surubis", "pintado", "pintados"))
            "pacú" -> variantes.addAll(listOf("pacús", "pacu", "pacus"))
            "pejerrey" -> variantes.addAll(listOf("pejerreyes", "pejerrei"))
            "tararira" -> variantes.addAll(listOf("tarariras", "tarira", "tariras"))
            "sábalo" -> variantes.addAll(listOf("sábalos", "sabalo", "sabalos"))
            "bagre" -> variantes.addAll(listOf("bagres", "gato", "gatos"))
        }

        // Agregar variantes al mapa
        variantes.forEach { variante ->
            if (variante.isNotBlank() && !mapa.containsKey(variante)) {
                mapa[variante] = fishInfo
                android.util.Log.v(TAG, "  🔄 Variante automática: '$variante' → ${fishInfo.name}")
            }
        }
    }

    /**
     * 🔄 CARGAR DATOS POR DEFECTO SI FALLA EL JSON
     */
    private fun cargarDatosPorDefecto() {
        android.util.Log.w(TAG, "⚠️ Usando datos por defecto como fallback")

        val especiesBasicas = mapOf(
            "dorado" to FishInfo("Dorado", "Salminus brasiliensis", "Ríos", listOf("carnada viva"), "amanecer", "spinning", "3-8 kg", "verano"),
            "dorados" to FishInfo("Dorado", "Salminus brasiliensis", "Ríos", listOf("carnada viva"), "amanecer", "spinning", "3-8 kg", "verano"),
            "surubí" to FishInfo("Surubí", "Pseudoplatystoma corruscans", "Pozones profundos", listOf("lombrices grandes"), "noche", "fondo", "5-25 kg", "todo el año"),
            "surubís" to FishInfo("Surubí", "Pseudoplatystoma corruscans", "Pozones profundos", listOf("lombrices grandes"), "noche", "fondo", "5-25 kg", "todo el año"),
            "pacú" to FishInfo("Pacú", "Piaractus mesopotamicus", "Remansos", listOf("frutas"), "mañana", "boya", "2-10 kg", "verano"),
            "pacús" to FishInfo("Pacú", "Piaractus mesopotamicus", "Remansos", listOf("frutas"), "mañana", "boya", "2-10 kg", "verano"),
            "pejerrey" to FishInfo("Pejerrey", "Odontesthes bonariensis", "Lagunas", listOf("lombriz"), "todo el día", "boya", "200g-1kg", "otoño-invierno"),
            "pejerreyes" to FishInfo("Pejerrey", "Odontesthes bonariensis", "Lagunas", listOf("lombriz"), "todo el día", "boya", "200g-1kg", "otoño-invierno")
        )

        fishSpeciesDB = especiesBasicas
        isLoaded = true

        android.util.Log.i(TAG, "📦 Datos por defecto cargados: ${especiesBasicas.size} entradas")
    }

    // ===== MÉTODOS PÚBLICOS (SIN CAMBIOS SIGNIFICATIVOS) =====

    fun findFishByKeyword(keyword: String): FishInfo? {
        val keywordLower = keyword.lowercase().trim()
        val resultado = fishSpeciesDB[keywordLower]

        if (resultado != null) {
            android.util.Log.d(TAG, "🔍 Especie encontrada: '$keyword' → ${resultado.name}")
        } else {
            android.util.Log.d(TAG, "❌ Especie NO encontrada: '$keyword'")
        }

        return resultado
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
     * 🔍 BUSCAR ESPECIES EN TEXTO (NUEVO MÉTODO MEJORADO)
     */
    fun buscarEspeciesEnTexto(texto: String): List<Pair<String, FishInfo>> {
        val textoLower = texto.lowercase().trim()
        val especiesEncontradas = mutableListOf<Pair<String, FishInfo>>()

        android.util.Log.d(TAG, "🔍 Buscando especies en: '$texto'")

        // Buscar todas las coincidencias
        fishSpeciesDB.forEach { (clave, fishInfo) ->
            if (textoLower.contains(clave)) {
                // Evitar duplicados por especie
                if (!especiesEncontradas.any { it.second.name == fishInfo.name }) {
                    especiesEncontradas.add(Pair(clave, fishInfo))
                    android.util.Log.d(TAG, "✅ Encontrada: '$clave' → ${fishInfo.name}")
                }
            }
        }

        android.util.Log.d(TAG, "📊 Total especies encontradas: ${especiesEncontradas.size}")
        return especiesEncontradas
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

    /**
     * 🔍 VERIFICAR SI ESTÁ INICIALIZADA
     */
    fun isInitialized(): Boolean = isLoaded

    /**
     * 🔍 OBTENER ESPECIES DISPONIBLES (PARA DEBUG)
     */
    fun getAvailableSpeciesNames(): List<String> {
        return fishSpeciesDB.keys.sorted()
    }
}