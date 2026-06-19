package com.example.juka.ui.theme.logros

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juka.data.Achievement
import com.example.juka.data.AchievementsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Paleta del header (verde Huka)
private val GREEN_HUKA = Color(0xFF1D9E75)
private val GREEN_LIGHT = Color(0xFFE1F5EE)
private val GREEN_DARK = Color(0xFF0F6E56)

/**
 * UI item: combina catálogo (todos los logros posibles) con desbloqueados
 * (los que el usuario ya tiene, vienen del VM/Firestore). Los bloqueados
 * se muestran con candado y placeholder.
 */
private data class AchievementUi(
    val entry: CatalogEntry,
    val unlockedData: Achievement?
) {
    val isUnlocked: Boolean get() = unlockedData != null
    val isNew: Boolean get() = unlockedData?.timestamp?.let {
        // "Nuevo" si se desbloqueó en las últimas 48 horas
        System.currentTimeMillis() - it < 48L * 60 * 60 * 1000
    } ?: false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel()
) {
    val context = LocalContext.current
    val unlocked by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<Achievement?>(null) }
    var filtroCategoria by remember { mutableStateOf<AchievementCategory?>(null) }
    var filtroEstado by remember { mutableStateOf(FiltroEstadoLogro.TODOS) }

    // Mergeamos catálogo + desbloqueados en items de UI. Después aplicamos
    // el filtro de estado (todos / obtenidos / pendientes) al resultado.
    val unlockedById = unlocked.associateBy { it.id }
    val items = remember(unlocked, filtroCategoria, filtroEstado) {
        AchievementCatalog.byCategory(filtroCategoria)
            .map { entry -> AchievementUi(entry = entry, unlockedData = unlockedById[entry.id]) }
            .filter { ui ->
                when (filtroEstado) {
                    FiltroEstadoLogro.TODOS -> true
                    FiltroEstadoLogro.OBTENIDOS -> ui.isUnlocked
                    FiltroEstadoLogro.PENDIENTES -> !ui.isUnlocked
                }
            }
    }
    // Para la barra de progreso usamos siempre el total absoluto, no el filtrado
    val totalUnlocked = unlocked.size
    val totalCatalog = AchievementCatalog.total

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F5))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { HeaderSection(onTrofeoClick = onBack) }
            item { ProgresoCard(unlocked = totalUnlocked, total = totalCatalog) }
            item {
                CategoriasChips(
                    selected = filtroCategoria,
                    onSelect = { filtroCategoria = it }
                )
            }
            // Segundo filtro: estado (obtenido / pendiente / todos).
            // Se combina con el de categoría arriba.
            item {
                EstadoLogroChips(
                    selected = filtroEstado,
                    onSelect = { filtroEstado = it }
                )
            }
            // La grilla 2 columnas en CommonComposables. Como la cantidad de
            // items es chica y cabe en una sola pantalla scrolleable, usamos
            // chunking en filas dentro del LazyColumn (más simple que
            // anidar LazyVerticalGrid en LazyColumn).
            items(items.chunked(2)) { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    fila.forEach { ui ->
                        Box(modifier = Modifier.weight(1f)) {
                            AchievementCardNuevo(
                                ui = ui,
                                onClick = { ui.unlockedData?.let { selected = it } }
                            )
                        }
                    }
                    // Si la fila tiene 1 solo item, rellenamos para que no se
                    // estire al ancho completo.
                    if (fila.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            // Espacio extra abajo para que el último item no quede tapado por
            // el bottom nav.
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        selected?.let { ach ->
            AchievementDetailDialog(
                achievement = ach,
                onDismiss = { selected = null },
                onShare = { viewModel.shareAchievement(context, it) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header — Título + subtítulo + botón trofeo
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(onTrofeoClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Mis Logros Huka",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF14213D)
            )
            Text(
                "Tus mejores momentos de pesca",
                fontSize = 14.sp,
                color = Color(0xFF6E7780)
            )
        }
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(48.dp)
                .clickable { onTrofeoClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Volver",
                    tint = GREEN_HUKA,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card de progreso — Avatar + número grande + barra + total
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProgresoCard(unlocked: Int, total: Int) {
    val progressTarget = if (total > 0) unlocked.toFloat() / total.toFloat() else 0f
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 800),
        label = "progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circular con cinta HUKA debajo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(110.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = GREEN_LIGHT,
                    border = androidx.compose.foundation.BorderStroke(3.dp, GREEN_HUKA),
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎣", fontSize = 44.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GREEN_HUKA
                ) {
                    Text(
                        "HUKA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$unlocked",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14213D),
                    lineHeight = 48.sp
                )
                Text(
                    "logros desbloqueados",
                    fontSize = 13.sp,
                    color = Color(0xFF6E7780)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = GREEN_HUKA,
                    trackColor = Color(0xFFE0E0E0)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Text(
                        "$unlocked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GREEN_HUKA
                    )
                    Text(
                        " / $total",
                        fontSize = 14.sp,
                        color = Color(0xFF888780)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chips de categoría
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoriasChips(
    selected: AchievementCategory?,
    onSelect: (AchievementCategory?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CategoriaChip(
                label = AchievementCategory.TODOS_DISPLAY,
                icon = AchievementCategory.TODOS_ICON,
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
        }
        items(AchievementCategory.values().toList()) { categoria ->
            CategoriaChip(
                label = categoria.displayName,
                icon = categoria.icon,
                isSelected = selected == categoria,
                onClick = { onSelect(categoria) }
            )
        }
    }
}

@Composable
private fun CategoriaChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) GREEN_HUKA else Color.White
    val fg = if (isSelected) Color.White else Color(0xFF14213D)
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        shadowElevation = if (isSelected) 0.dp else 1.dp,
        modifier = Modifier
            .height(40.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card de logro (estilo nuevo)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementCardNuevo(
    ui: AchievementUi,
    onClick: () -> Unit
) {
    val isUnlocked = ui.isUnlocked
    val entry = ui.entry

    Card(
        onClick = onClick,
        enabled = isUnlocked,
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Sección visual superior (cuadrada, ratio 1:1 más cómoda en grid)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                // Fondo: imagen real si hay; sino gradiente temático.
                val imageUrl = ui.unlockedData?.imageUrl?.takeIf { it.isNotBlank() }
                if (isUnlocked && imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Sin imagen: gradiente de la categoría + emoji grande.
                    // Si está bloqueado, gris con candado y signo de pregunta.
                    val (top, bottom) = if (isUnlocked) {
                        entry.category.containerColor to entry.category.color.copy(alpha = 0.25f)
                    } else {
                        Color(0xFFB7BCC2) to Color(0xFF6F7782)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(top, bottom))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUnlocked) {
                            Text(emojiFor(entry), fontSize = 56.sp)
                        } else {
                            Text(
                                "?",
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // (Cuando un logro está bloqueado el VM no devuelve imageUrl,
                // así que ya pintamos el placeholder gris arriba. No hace
                // falta overlay extra sobre el AsyncImage.)

                // Badge "NUEVO" arriba a la izquierda (solo si está desbloqueado y reciente)
                if (ui.isNew) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp),
                        color = Color(0xFF14213D),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            "NUEVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Ícono de categoría arriba a la derecha (o candado si bloqueado)
                Surface(
                    shape = CircleShape,
                    color = if (isUnlocked) entry.category.color else Color(0xFF555555),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isUnlocked) entry.category.icon else Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Footer: título + chip de categoría + fecha
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    entry.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14213D),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (isUnlocked) 1f else 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (isUnlocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = entry.category.containerColor
                        ) {
                            Text(
                                entry.category.shortLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = entry.category.color,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = ui.unlockedData?.timestamp?.let { formatFecha(it) } ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFF888780)
                        )
                    }
                } else {
                    // Bloqueado: descripción del logro como hint para que el
                    // usuario sepa qué hay que hacer para desbloquearlo.
                    Text(
                        "${shorten(entry.description, 36)}...",
                        fontSize = 11.sp,
                        color = Color(0xFF888780),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatFecha(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "AR"))
    return sdf.format(Date(timestamp))
        // capitalizar el mes (en es_AR sale en minúsculas: "14 jul 2026")
        .replaceFirstChar { it.uppercase() }
        .let { s ->
            // capitalizar la primera letra del mes (3er token)
            val parts = s.split(" ")
            if (parts.size == 3) {
                "${parts[0]} ${parts[1].replaceFirstChar { it.uppercase() }} ${parts[2]}"
            } else s
        }
}

private fun shorten(text: String, max: Int): String =
    if (text.length <= max) text else text.substring(0, max).trimEnd()

// ─────────────────────────────────────────────────────────────────────────────
// Filtro por estado del logro (obtenidos / pendientes / todos)
// ─────────────────────────────────────────────────────────────────────────────

private enum class FiltroEstadoLogro(val displayName: String) {
    TODOS("Todos"),
    OBTENIDOS("Obtenidos"),
    PENDIENTES("Pendientes")
}

@Composable
private fun EstadoLogroChips(
    selected: FiltroEstadoLogro,
    onSelect: (FiltroEstadoLogro) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(FiltroEstadoLogro.values().toList()) { estado ->
            EstadoChip(
                label = estado.displayName,
                isSelected = selected == estado,
                onClick = { onSelect(estado) }
            )
        }
    }
}

@Composable
private fun EstadoChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Mismos colores y forma que CategoriaChip pero sin ícono — es solo
    // texto, para que se distinga visualmente de la fila de categorías.
    val bg = if (isSelected) GREEN_HUKA else Color.White
    val fg = if (isSelected) Color.White else Color(0xFF14213D)
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        shadowElevation = if (isSelected) 0.dp else 1.dp,
        modifier = Modifier
            .height(36.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}
