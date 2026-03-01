package com.example.juka.ui.theme.logros

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juka.data.Achievement
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AchievementItem(
    achievement: Achievement,
    onItemClick: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked

    // Animaciones
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(key1 = achievement.id) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Animación de brillo para logros desbloqueados
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    // Determinamos la categoría basándonos en el tipo o puntos
    val category = when {
        achievement.points >= 100 -> "legendary"  // 100+ puntos = legendario
        achievement.points >= 50 -> "epic"        // 50-99 puntos = épico
        achievement.points >= 25 -> "rare"        // 25-49 puntos = raro
        else -> "common"                          // <25 puntos = común
    }

    // Colores según estado
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isUnlocked -> Color(0xFFE0E0E0) // Gris para bloqueados
            category == "legendary" -> Color(0xFFFFF8DC) // Dorado para legendarios
            category == "epic" -> Color(0xFFE8D8FF) // Púrpura para épicos
            category == "rare" -> Color(0xFFE3F2FD) // Azul para raros
            else -> Color(0xFFFFFFFF) // Blanco para comunes
        },
        label = "background"
    )

    val borderColor = when {
        !isUnlocked -> Color(0xFFBDBDBD)
        category == "legendary" -> Color(0xFFFFD700)
        category == "epic" -> Color(0xFF9C27B0)
        category == "rare" -> Color(0xFF2196F3)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .scale(scale.value)
            .clickable(enabled = true) {
                onItemClick()
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isUnlocked) 4.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Mantener proporción cuadrada
                .border(
                    width = if (isUnlocked) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Fondo con patrón para logros especiales
            if (isUnlocked && category in listOf("legendary", "epic")) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawStarPattern(borderColor.copy(alpha = 0.1f))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Contenedor de imagen/ícono
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    when {
                        !isUnlocked -> {
                            // Ícono de candado para bloqueados
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bloqueado",
                                modifier = Modifier.size(32.dp),
                                tint = Color.Gray
                            )
                        }
                        achievement.imageUrl.isNotEmpty() -> {
                            // Imagen del logro con efecto de brillo
                            Box {
                                AsyncImage(
                                    model = achievement.imageUrl,
                                    contentDescription = achievement.title,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .graphicsLayer {
                                            if (category == "legendary") {
                                                this.alpha = shimmerAlpha
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                // Efecto de brillo overlay para legendarios
                                if (isUnlocked && category == "legendary") {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = shimmerAlpha * 0.3f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                        else -> {
                            // Emoji según tipo de logro
                            val emoji = getEmojiForAchievement(achievement)
                            Text(
                                text = emoji,
                                fontSize = 36.sp
                            )
                        }
                    }

                    // Indicador de rareza (estrellitas alrededor del ícono)
                    if (isUnlocked && category in listOf("legendary", "epic", "rare")) {
                        RarityIndicator(
                            category = category,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Título del logro
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (isUnlocked) 1f else 0.5f),
                    fontSize = 10.sp
                )

                // Puntos si está desbloqueado
                if (isUnlocked && achievement.points > 0) {
                    Text(
                        text = "${achievement.points} pts",
                        fontSize = 8.sp,
                        color = borderColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Indicador de "NUEVO" si fue desbloqueado recientemente
                // (Podrías agregar un campo lastUnlockedDate al Achievement)
                if (isUnlocked && achievement.dateUnlocked != null) {
                    val daysSinceUnlock = calculateDaysSince(achievement.dateUnlocked.toString())
                    if (daysSinceUnlock <= 1) { // Mostrar "NUEVO" si se desbloqueó en las últimas 24 horas
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = Color.Red,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "NUEVO",
                                fontSize = 8.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RarityIndicator(
    category: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rarity")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (category) {
                    "legendary" -> 3000
                    "epic" -> 4000
                    else -> 5000
                }
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val color = when (category) {
        "legendary" -> Color(0xFFFFD700)
        "epic" -> Color(0xFF9C27B0)
        "rare" -> Color(0xFF2196F3)
        else -> Color.Transparent
    }

    Canvas(
        modifier = modifier.rotate(rotation)
    ) {
        val starCount = when (category) {
            "legendary" -> 8
            "epic" -> 6
            "rare" -> 4
            else -> 0
        }

        repeat(starCount) { index ->
            val angle = (360f / starCount) * index
            val radians = Math.toRadians(angle.toDouble())
            val x = center.x + cos(radians).toFloat() * (size.width / 2.2f)
            val y = center.y + sin(radians).toFloat() * (size.height / 2.2f)

            drawCircle(
                color = color.copy(alpha = 0.7f),
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

fun DrawScope.drawStarPattern(color: Color) {
    val patternSize = 30.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            rotate(degrees = 45f, pivot = Offset(x + patternSize / 2, y + patternSize / 2)) {
                drawStar(
                    center = Offset(x + patternSize / 2, y + patternSize / 2),
                    size = 8.dp.toPx(),
                    color = color
                )
            }
            x += patternSize
        }
        y += patternSize
    }
}

fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        val angle = 72f
        val halfAngle = angle / 2f
        val radius = size
        val innerRadius = radius * 0.5f

        moveTo(center.x, center.y - radius)

        for (i in 0..4) {
            val outerAngle = Math.toRadians((angle * i - 90).toDouble())
            val innerAngle = Math.toRadians((angle * i + halfAngle - 90).toDouble())

            if (i > 0) {
                lineTo(
                    center.x + (radius * cos(outerAngle)).toFloat(),
                    center.y + (radius * sin(outerAngle)).toFloat()
                )
            }

            lineTo(
                center.x + (innerRadius * cos(innerAngle)).toFloat(),
                center.y + (innerRadius * sin(innerAngle)).toFloat()
            )
        }
        close()
    }

    drawPath(path, color)
}

fun getEmojiForAchievement(achievement: Achievement): String {
    // Basado en el tipo o título del logro
    return when {
        achievement.title.contains("primera", ignoreCase = true) -> "🎣"
        achievement.title.contains("pejerrey", ignoreCase = true) -> "🐟"
        achievement.title.contains("dorado", ignoreCase = true) -> "🦈"
        achievement.title.contains("surubí", ignoreCase = true) -> "🐠"
        achievement.title.contains("noche", ignoreCase = true) -> "🌙"
        achievement.title.contains("amanecer", ignoreCase = true) -> "🌅"
        achievement.title.contains("100", ignoreCase = true) -> "💯"
        achievement.title.contains("foto", ignoreCase = true) -> "📸"
        achievement.title.contains("compartir", ignoreCase = true) -> "📱"
        else -> "🏆"
    }
}

fun calculateDaysSince(dateUnlocked: String?): Int {
    // Implementación simple, ajusta según tu formato de fecha
    return try {
        // Por ahora retorna 0 para mostrar siempre "NUEVO" si hay fecha
        0
    } catch (e: Exception) {
        999 // No mostrar "NUEVO" si hay error
    }
}

@Composable
fun AchievementItemCompact(
    achievement: Achievement,
    onItemClick: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked

    Card(
        modifier = Modifier
            .size(60.dp) // Tamaño fijo pequeño
            .clickable { onItemClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color.White else Color(0xFFE0E0E0)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = if (isUnlocked) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                !isUnlocked -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueado",
                        modifier = Modifier.size(20.dp),
                        tint = Color.Gray
                    )
                }
                achievement.imageUrl.isNotEmpty() -> {
                    AsyncImage(
                        model = achievement.imageUrl,
                        contentDescription = achievement.title,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Text(
                        text = getEmojiForAchievement(achievement),
                        fontSize = 24.sp
                    )
                }
            }

            // Badge pequeño "NUEVO" en la esquina
            if (isUnlocked && achievement.timestamp != null) {
                val currentTime = System.currentTimeMillis()
                val dayInMillis = 24 * 60 * 60 * 1000
                if (currentTime - achievement.timestamp < dayInMillis) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            color = Color.Red,
                            shape = CircleShape,
                            modifier = Modifier.size(12.dp)
                        ) {
                            // Solo un punto rojo indicador
                        }
                    }
                }
            }
        }
    }
}