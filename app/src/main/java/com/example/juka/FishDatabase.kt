package com.example.juka

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

class FishDatabase {

    val fishSpeciesDB = mapOf(
        "dorado" to FishInfo(
            name = "Dorado",
            scientificName = "Salminus brasiliensis",
            habitat = "Ríos de corriente fuerte",
            bestBaits = listOf("carnada viva", "señuelos plateados", "cucharitas rotativas"),
            bestTime = "amanecer y atardecer",
            technique = "spinning con recuperación irregular",
            avgSize = "3-8 kg",
            season = "octubre a abril"
        ),
        "surubí" to FishInfo(
            name = "Surubí",
            scientificName = "Pseudoplatystoma corruscans",
            habitat = "Pozones profundos del río",
            bestBaits = listOf("lombrices grandes", "bagre", "tararira"),
            bestTime = "noche",
            technique = "pesca de fondo con plomada",
            avgSize = "5-20 kg",
            season = "todo el año"
        ),
        "pacú" to FishInfo(
            name = "Pacú",
            scientificName = "Piaractus mesopotamicus",
            habitat = "Remansos y lagunas",
            bestBaits = listOf("frutas", "maíz", "pellets", "semillas"),
            bestTime = "mañana temprano",
            technique = "pesca con boya a media agua",
            avgSize = "2-8 kg",
            season = "noviembre a marzo"
        ),
        "pejerrey" to FishInfo(
            name = "Pejerrey",
            scientificName = "Odontesthes bonariensis",
            habitat = "Lagunas y embalses",
            bestBaits = listOf("lombriz", "cascarudos", "artificiales pequeños"),
            bestTime = "todo el día",
            technique = "pesca con boya o spinning liviano",
            avgSize = "200g-1kg",
            season = "marzo a noviembre"
        ),
        "tararira" to FishInfo(
            name = "Tararira",
            scientificName = "Hoplias malabaricus",
            habitat = "Vegetación acuática y juncales",
            bestBaits = listOf("carnada viva", "spinnerbaits", "poppers"),
            bestTime = "amanecer y anochecer",
            technique = "casting entre la vegetación",
            avgSize = "1-4 kg",
            season = "septiembre a abril"
        ),
        "sábalo" to FishInfo(
            name = "Sábalo",
            scientificName = "Prochilodus lineatus",
            habitat = "Ríos y arroyos",
            bestBaits = listOf("masa", "pan", "lombriz"),
            bestTime = "mañana y tarde",
            technique = "pesca de fondo liviana",
            avgSize = "1-3 kg",
            season = "todo el año"
        ),
        "boga" to FishInfo(
            name = "Boga",
            scientificName = "Leporinus obtusidens",
            habitat = "Correderas y pozones",
            bestBaits = listOf("masa", "maíz", "lombrices"),
            bestTime = "día completo",
            technique = "pesca al correntino",
            avgSize = "500g-2kg",
            season = "octubre a marzo"
        )
    )

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
        return fishSpeciesDB.values.toList()
    }
}