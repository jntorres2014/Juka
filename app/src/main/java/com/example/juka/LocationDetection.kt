// LocationDetector.kt - Detector de zonas de pesca (versión simplificada)
package com.example.juka

import android.app.Application
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class FishingZone(
    val name: String,
    val type: String, // "río", "laguna", "embalse", etc.
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val commonSpecies: List<String>,
    val bestSeasons: List<String>
)

data class LocationInfo(
    val zoneName: String,
    val zoneType: String,
    val distance: Double? = null,
    val description: String,
    val nearbySpecies: List<String>,
    val isKnownZone: Boolean,
    val coordinates: Pair<Double, Double>? = null
)

class LocationDetector(private val application: Application) {

    private val captureLocationsFile = File(application.filesDir, "capture_locations.txt")

    // 🗺️ Base de datos offline de zonas de pesca argentinas
    private val knownFishingZones = listOf(
        FishingZone(
            name = "Río Paraná - Rosario",
            type = "río",
            latitude = -32.9442,
            longitude = -60.6505,
            description = "Zona excelente para dorados y surubís",
            commonSpecies = listOf("dorado", "surubí", "sábalo", "boga"),
            bestSeasons = listOf("verano", "otoño")
        ),
        FishingZone(
            name = "Río Paraná - Santa Fe",
            type = "río",
            latitude = -31.6107,
            longitude = -60.6973,
            description = "Zona histórica de pesca deportiva",
            commonSpecies = listOf("dorado", "surubí", "pacú"),
            bestSeasons = listOf("verano", "primavera")
        ),
        FishingZone(
            name = "Río de la Plata - Tigre",
            type = "río",
            latitude = -34.4264,
            longitude = -58.5732,
            description = "Delta con gran variedad de especies",
            commonSpecies = listOf("tararira", "pejerrey", "boga"),
            bestSeasons = listOf("otoño", "invierno")
        ),
        FishingZone(
            name = "Lago San Roque - Córdoba",
            type = "embalse",
            latitude = -31.3754,
            longitude = -64.4592,
            description = "Embalse ideal para pejerrey",
            commonSpecies = listOf("pejerrey", "carpa"),
            bestSeasons = listOf("otoño", "invierno")
        ),
        FishingZone(
            name = "Río Uruguay - Concordia",
            type = "río",
            latitude = -31.3934,
            longitude = -58.0209,
            description = "Excelente para dorados grandes",
            commonSpecies = listOf("dorado", "surubí", "boga"),
            bestSeasons = listOf("verano", "otoño")
        ),
        FishingZone(
            name = "Laguna de Chascomús",
            type = "laguna",
            latitude = -35.5707,
            longitude = -58.0265,
            description = "Laguna pampeana clásica",
            commonSpecies = listOf("pejerrey", "carpa", "tararira"),
            bestSeasons = listOf("otoño", "invierno", "primavera")
        ),
        FishingZone(
            name = "Río Paraná - Puerto Iguazú",
            type = "río",
            latitude = -25.5947,
            longitude = -54.5776,
            description = "Zona subtropical con especies únicas",
            commonSpecies = listOf("dorado", "surubí", "pacú"),
            bestSeasons = listOf("verano", "otoño")
        )
    )

    // 🔍 Función principal simplificada
    suspend fun detectLocationFromGPS(): LocationInfo = withContext(Dispatchers.IO) {
        try {
            // Intentar obtener ubicación actual
            val currentLocation = getCurrentLocation()
            if (currentLocation != null) {
                return@withContext analyzeLocation(currentLocation.first, currentLocation.second)
            }

            // Si no hay ubicación disponible
            return@withContext LocationInfo(
                zoneName = "Ubicación no disponible",
                zoneType = "desconocido",
                description = "Para detectar zona automáticamente, activá GPS y permisos de ubicación 📍",
                nearbySpecies = listOf("pejerrey", "carpa", "boga"),
                isKnownZone = false
            )

        } catch (e: Exception) {
            android.util.Log.e("LOCATION", "Error detectando ubicación: ${e.message}")

            // Ubicación por defecto si falla todo
            return@withContext LocationInfo(
                zoneName = "Argentina - Zona General",
                zoneType = "cuerpo de agua",
                description = "Ubicación estimada - Especies comunes de Argentina",
                nearbySpecies = listOf("pejerrey", "dorado", "carpa", "tararira"),
                isKnownZone = false
            )
        }
    }

    private suspend fun analyzeLocation(latitude: Double, longitude: Double): LocationInfo {
        // 1. Buscar en zonas conocidas offline
        val nearbyKnownZone = findNearbyKnownZone(latitude, longitude)

        if (nearbyKnownZone != null) {
            // Guardar la captura en zona conocida
            saveCaptureLocation(latitude, longitude, nearbyKnownZone.name)

            return LocationInfo(
                zoneName = nearbyKnownZone.name,
                zoneType = nearbyKnownZone.type,
                distance = calculateDistance(latitude, longitude, nearbyKnownZone.latitude, nearbyKnownZone.longitude),
                description = nearbyKnownZone.description,
                nearbySpecies = nearbyKnownZone.commonSpecies,
                isKnownZone = true,
                coordinates = Pair(latitude, longitude)
            )
        }

        // 2. Si no hay zona conocida, usar análisis offline
        val offlineInfo = getLocationInfoOffline(latitude, longitude)
        saveCaptureLocation(latitude, longitude, offlineInfo.zoneName)
        return offlineInfo
    }

    private fun findNearbyKnownZone(latitude: Double, longitude: Double): FishingZone? {
        return knownFishingZones.minByOrNull { zone ->
            calculateDistance(latitude, longitude, zone.latitude, zone.longitude)
        }?.let { nearestZone ->
            val distance = calculateDistance(latitude, longitude, nearestZone.latitude, nearestZone.longitude)
            if (distance <= 50.0) nearestZone else null // 50km radius
        }
    }

    private fun getLocationInfoOffline(latitude: Double, longitude: Double): LocationInfo {
        // Análisis offline basado en coordenadas de Argentina
        val region = getArgentineRegion(latitude, longitude)
        val waterType = estimateWaterType(latitude, longitude)

        return LocationInfo(
            zoneName = "${region.first} - ${region.second}",
            zoneType = waterType,
            description = "Zona estimada offline basada en coordenadas GPS",
            nearbySpecies = getPossibleSpeciesForRegion(region.second),
            isKnownZone = false,
            coordinates = Pair(latitude, longitude)
        )
    }

    private fun getArgentineRegion(latitude: Double, longitude: Double): Pair<String, String> {
        return when {
            latitude > -24 && longitude > -58 -> Pair("Mesopotamia Norte", "Misiones")
            latitude > -29 && longitude > -60 -> Pair("Mesopotamia", "Santa Fe")
            latitude > -35 && longitude > -65 -> Pair("Pampa Húmeda", "Buenos Aires")
            latitude > -40 && longitude > -70 -> Pair("Patagonia Norte", "Neuquén")
            latitude > -32 && longitude < -65 -> Pair("Cuyo", "Mendoza")
            latitude > -28 && longitude < -65 -> Pair("NOA", "Salta")
            else -> Pair("Interior", "Argentina Central")
        }
    }

    private fun estimateWaterType(latitude: Double, longitude: Double): String {
        return when {
            // Zona del Paraná
            longitude > -61 && longitude < -58 && latitude > -35 && latitude < -25 -> "río"
            // Zona de lagunas pampeanas
            latitude > -38 && latitude < -34 && longitude > -62 && longitude < -57 -> "laguna"
            // Zona de embalses cordilleranos
            longitude < -68 -> "embalse"
            // Zona costera
            longitude > -58 && latitude > -38 -> "estuario"
            else -> "cuerpo de agua"
        }
    }

    private fun getPossibleSpeciesForRegion(region: String): List<String> {
        return when (region.lowercase()) {
            "misiones", "corrientes" -> listOf("dorado", "surubí", "pacú", "sábalo")
            "santa fe", "entre ríos" -> listOf("dorado", "surubí", "boga", "sábalo")
            "buenos aires" -> listOf("pejerrey", "tararira", "carpa")
            "córdoba", "mendoza" -> listOf("pejerrey", "trucha", "carpa")
            "neuquén", "río negro" -> listOf("trucha", "salmón", "perca")
            else -> listOf("pejerrey", "carpa", "boga")
        }
    }

    /**
     * Devuelve las coordenadas actuales del dispositivo (lat, lon) sin
     * enriquecer con info de zona. Pensado para casos como "centrar el mapa
     * en mi ubicación" en MapPickerScreen, donde solo se necesitan las
     * coordenadas.
     *
     * Returns null si:
     *  - No hay permisos de ubicación,
     *  - GPS y Network apagados,
     *  - No hay último fix conocido.
     */
    suspend fun obtenerLatLonActual(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        getCurrentLocation()
    }

    /**
     * Reverse geocoding: dado un punto (lat, lon), devuelve un nombre legible.
     * Estrategia en cascada — gana lo más específico:
     *
     *  1. Si el punto está a ≤50 km de una `knownFishingZones`, devolvemos
     *     el nombre de esa zona ("Río Paraná - Rosario"). Esto le gana al
     *     geocoder porque es más útil para pescadores que "Centro, Rosario".
     *  2. Si no, intentamos `Geocoder` de Android (servicio nativo, requiere
     *     internet) y armamos "Localidad, Provincia". En Argentina suele
     *     devolver tipo "Rosario, Santa Fe" o "San Carlos de Bariloche, Río Negro".
     *  3. Si Geocoder falla (sin internet, sin resultado, no instalado en
     *     el device), fallback a la grilla `getArgentineRegion` ("Mesopotamia
     *     Norte - Misiones") — gruesa pero al menos da una pista.
     *  4. Si todo falla, null. El caller decide qué texto mostrar.
     */
    suspend fun obtenerNombreLugar(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            // 1. Zona de pesca conocida cerca → usamos ese nombre
            findNearbyKnownZone(lat, lon)?.let { return@withContext it.name }

            // 2. Geocoder nativo → "Localidad, Provincia"
            try {
                if (android.location.Geocoder.isPresent()) {
                    val geocoder = android.location.Geocoder(
                        application,
                        java.util.Locale("es", "AR")
                    )
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val locality = addr.locality
                            ?: addr.subAdminArea
                            ?: addr.subLocality
                        val province = addr.adminArea
                        val nombre = when {
                            !locality.isNullOrBlank() && !province.isNullOrBlank() ->
                                "$locality, $province"
                            !province.isNullOrBlank() -> province
                            !locality.isNullOrBlank() -> locality
                            else -> null
                        }
                        if (nombre != null) return@withContext nombre
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("LOCATION", "Geocoder falló: ${e.message}")
            }

            // 3. Fallback: grilla gruesa de regiones argentinas
            val region = getArgentineRegion(lat, lon)
            "${region.first} - ${region.second}"
        }

    private fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Verificar permisos antes de acceder
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                androidx.core.content.ContextCompat.checkSelfPermission(
                    application,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )) {
                android.util.Log.w("LOCATION", "Sin permisos de ubicación")
                return null
            }

            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            lastKnownLocation?.let { Pair(it.latitude, it.longitude) }

        } catch (e: SecurityException) {
            android.util.Log.w("LOCATION", "Sin permisos de ubicación")
            null
        } catch (e: Exception) {
            android.util.Log.e("LOCATION", "Error obteniendo ubicación: ${e.message}")
            null
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Radio de la Tierra en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun saveCaptureLocation(latitude: Double, longitude: Double, zoneName: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp | $zoneName | $latitude,$longitude\n"
            captureLocationsFile.appendText(logEntry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun buildLocationResponse(locationInfo: LocationInfo): String {
        val distanceText = locationInfo.distance?.let {
            if (it < 1.0) "${(it * 1000).toInt()}m" else "${it.toInt()}km"
        }

        return """
🗺️ **Análisis de zona de pesca:**

📍 **Ubicación:** ${locationInfo.zoneName}
🌊 **Tipo:** ${locationInfo.zoneType}
${if (distanceText != null) "📏 **Distancia:** $distanceText de zona conocida" else ""}

ℹ️ **Descripción:** ${locationInfo.description}

🐟 **Especies probables en la zona:**
${locationInfo.nearbySpecies.joinToString("\n") { "• $it" }}

${if (locationInfo.isKnownZone) {
            "✅ **Zona registrada** en nuestra base de datos de pesca"
        } else {
            "📝 **Nueva zona** - Agregada a tu registro personal"
        }}
        """.trimIndent()
    }

    fun getZoneStats(): String {
        return try {
            val captureCount = if (captureLocationsFile.exists()) {
                captureLocationsFile.readLines().size
            } else 0

            val knownZonesCount = knownFishingZones.size

            "🗺️ Zonas conocidas: $knownZonesCount | Capturas registradas: $captureCount"
        } catch (e: Exception) {
            "🗺️ Estadísticas no disponibles"
        }
    }
}