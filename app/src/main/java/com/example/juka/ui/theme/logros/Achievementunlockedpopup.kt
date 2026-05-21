package com.example.juka.ui.theme.logros

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juka.data.Achievement
import kotlinx.coroutines.delay

/**
 * Popup que aparece en la parte INFERIOR de la pantalla cuando se desbloquea un logro.
 * Se muestra automáticamente y desaparece a los 4 segundos.
 *
 * Uso en HukaAppWithUser.kt:
 *   AchievementUnlockedPopup(achievement = achievement, onDismiss = { ... })
 */
@Composable
fun AchievementUnlockedPopup(
    achievement: Achievement,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    // Aparece con animación al montarse
    LaunchedEffect(achievement.id) {
        visible = true
        delay(4_000)          // Visible 4 segundos
        visible = false
        delay(400)            // Esperar que termine la animación de salida
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 90.dp),   // Encima de la barra de navegación
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1A1A2E),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Imagen del logro (o emoji fallback)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (achievement.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = achievement.imageUrl,
                                contentDescription = achievement.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = getEmojiForAchievement(achievement),
                                fontSize = 28.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Textos
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🏆 ¡Logro desbloqueado!",
                            fontSize = 11.sp,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = achievement.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = achievement.description,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}