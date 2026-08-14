package com.example.juka.ui.theme.logros

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Catálogo de logros. La fuente de verdad para mostrar la lista en la
 * pantalla — incluye los bloqueados (que el VM no devuelve, porque sólo
 * conoce los desbloqueados desde Firestore).
 *
 * Cuando agreguen un nuevo logro al backend hay que sumarlo acá también
 * con su categoría e info legible.
 */
object AchievementCatalog {

    val all: List<CatalogEntry> = listOf(
        // === EVENTOS ESTACIONALES ===
        CatalogEntry("pescador_navideño", "Pescador Navideño", "Pescaste durante las fiestas navideñas (24-31 dic)", AchievementCategory.EVENTOS),
        CatalogEntry("pescador_año_nuevo", "Pescador de Año Nuevo", "Empezaste el año pescando (1-7 enero)", AchievementCategory.EVENTOS),
        CatalogEntry("regalo_de_reyes", "Regalo de Reyes", "Pescaste el día de Reyes Magos", AchievementCategory.EVENTOS),
        CatalogEntry("pescador_invernal", "Pescador Invernal", "Desafiaste el frío del invierno argentino", AchievementCategory.EVENTOS),
        CatalogEntry("pescador_primaveral", "Pescador Primaveral", "Aprovechaste la primavera para pescar", AchievementCategory.EVENTOS),

        // === ESPECIES ===
        CatalogEntry("variedad_es_vida", "La Variedad es Vida", "Pescaste 5 especies diferentes en un parte", AchievementCategory.ESPECIES),
        CatalogEntry("rey_del_rio", "Rey del Río", "Pescaste 5+ dorados en una jornada", AchievementCategory.ESPECIES),
        CatalogEntry("cazador_de_dorados", "Cazador de Dorados", "Registraste tu primer dorado", AchievementCategory.ESPECIES),
        CatalogEntry("amigo_del_surubi", "Amigo del Surubí", "Pescaste el gigante del río", AchievementCategory.ESPECIES),
        CatalogEntry("pejerreyes_master", "Maestro del Pejerrey", "Pescaste 10 o más pejerreyes", AchievementCategory.ESPECIES),

        // === HORARIOS ===
        CatalogEntry("madrugador", "Madrugador", "Pescaste antes del amanecer (5-7 AM)", AchievementCategory.HORARIOS),
        CatalogEntry("pescador_nocturno", "Pescador Nocturno", "Pescaste después del atardecer (19-23 hs)", AchievementCategory.HORARIOS),
        CatalogEntry("noctambulo", "Noctámbulo Extremo", "Pescaste de madrugada (0-4 AM)", AchievementCategory.HORARIOS),

        // === PESCADEX (colección de especies) ===
        CatalogEntry("pescadex_primer_pez", "Primer Pez", "Tu primera especie en el Pescadex", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_explorador", "Explorador del Pescadex", "Capturaste 5 especies diferentes", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_coleccionista", "Coleccionista", "10 especies diferentes en tu Pescadex", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_especialista", "Especialista", "15 especies diferentes en tu Pescadex", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_maestro", "Maestro Pescador", "20 especies diferentes en tu Pescadex", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_cazador_raros", "Cazador de Raros", "Capturaste al menos una especie rara", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_cazador_epicos", "Cazador Épico", "Capturaste al menos una especie épica o legendaria", AchievementCategory.ESPECIES),
        CatalogEntry("pescadex_completista", "Completista", "Capturaste 30 especies diferentes", AchievementCategory.ESPECIES),

        // === ESPECIALES (iniciación, hitos, exploración) ===
        CatalogEntry("mi_primer_parte", "Mi Primer Parte", "¡Bienvenido a Huka! Creaste tu primer reporte de pesca", AchievementCategory.ESPECIALES),
        CatalogEntry("solo_un_pez", "Solo Un Pez", "No pescaste nada... bueno, casi nada", AchievementCategory.ESPECIALES),
        CatalogEntry("zapatero_wade", "Zapatero Wade", "¡No tuviste suerte hoy, espero esto te ayude la próxima!", AchievementCategory.ESPECIALES),
        CatalogEntry("pesca_abundante", "Pesca Abundante", "¡Pescaste 10 o más peces en una salida!", AchievementCategory.ESPECIALES),
        CatalogEntry("explorador", "Explorador", "Compartiste la ubicación de tu pesca", AchievementCategory.ESPECIALES)
    )

    val byId: Map<String, CatalogEntry> = all.associateBy { it.id }

    /** Cuántos logros existen en total (para mostrar "X / total"). */
    val total: Int get() = all.size

    /** Devuelve los del catálogo que pertenecen a la categoría. */
    fun byCategory(category: AchievementCategory?): List<CatalogEntry> =
        if (category == null) all else all.filter { it.category == category }
}

data class CatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory
)

/**
 * Categorías para los chips de filtro. Cada una trae su ícono, color y
 * etiqueta corta para el chip que se muestra dentro de cada card.
 */
enum class AchievementCategory(
    val displayName: String,
    val shortLabel: String,
    val icon: ImageVector,
    val color: Color,
    val containerColor: Color
) {
    EVENTOS(
        displayName = "Eventos",
        shortLabel = "EVENTO",
        icon = Icons.Default.CardGiftcard,
        color = Color(0xFFE53935),
        containerColor = Color(0xFFFFEBEE)
    ),
    ESPECIES(
        displayName = "Especies",
        shortLabel = "ESPECIES",
        icon = Icons.Default.SetMeal,
        color = Color(0xFF2E7D32),
        containerColor = Color(0xFFE8F5E9)
    ),
    HORARIOS(
        displayName = "Horarios",
        shortLabel = "HORARIOS",
        icon = Icons.Default.Nightlight,
        color = Color(0xFF6A4C93),
        containerColor = Color(0xFFEDE7F6)
    ),
    ESPECIALES(
        displayName = "Especiales",
        shortLabel = "ESPECIAL",
        icon = Icons.Default.Star,
        color = Color(0xFFEF6C00),
        containerColor = Color(0xFFFFF3E0)
    );

    companion object {
        /** Categoría especial usada para el chip "Todos". */
        val TODOS_DISPLAY = "Todos"
        val TODOS_ICON: ImageVector = Icons.Default.Apps
    }
}

/** Agrega un emoji ilustrativo coherente con la categoría/título del logro. */
fun emojiFor(entry: CatalogEntry): String = when {
    entry.id.contains("invernal") -> "❄️"
    entry.id.contains("navideño") -> "🎄"
    entry.id.contains("año_nuevo") -> "🎆"
    entry.id.contains("reyes") -> "👑"
    entry.id.contains("primaveral") -> "🌸"
    entry.id.contains("noctambulo") -> "🌙"
    entry.id.contains("nocturno") -> "🌃"
    entry.id.contains("madrugador") -> "🌅"
    entry.id.contains("dorado") -> "🐟"
    entry.id.contains("surubi") -> "🐠"
    entry.id.contains("pejerrey") -> "🎣"
    entry.id.contains("variedad") -> "🌈"
    entry.id.contains("primer") -> "✨"
    entry.id.contains("zapatero") || entry.id.contains("solo_un_pez") -> "🥲"
    entry.id.contains("abundante") -> "🎯"
    entry.id.contains("explorador") -> "🗺️"
    else -> "🏆"
}
