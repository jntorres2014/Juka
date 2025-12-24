package com.example.juka.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.ParteSessionChat
import com.google.firebase.auth.FirebaseUser

@Composable
fun EnhancedChatHeader(
    user: FirebaseUser,
    currentMode: ChatMode,
    parteSession: ParteSessionChat?,
    firebaseStatus: String?,
    onModeChange: (ChatMode) -> Unit,
    onCancelarParte: () -> Unit,
    onInfoClick: () -> Unit,
    showMenuButton: Boolean = false,
    onMenuClick: () -> Unit = {}
) {
    Surface(
        shadowElevation = 4.dp,
        color = when (currentMode) {
            ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
            ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
        }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Icono del modo
                        Icon(
                            when (currentMode) {
                                ChatMode.GENERAL -> Icons.Default.Chat
                                ChatMode.CREAR_PARTE -> Icons.Default.Assignment
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = when (currentMode) {
                                ChatMode.GENERAL -> "Chat General"
                                ChatMode.CREAR_PARTE -> "Crear Parte"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Text(
                        text = when (currentMode) {
                            ChatMode.GENERAL -> "Hola ${user.displayName?.split(" ")?.first() ?: "Pescador"} 🎣"
                            ChatMode.CREAR_PARTE -> parteSession?.let {
                                "Progreso: ${it.parteData.porcentajeCompletado}"
                            } ?: "Iniciando nuevo parte..."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Estado de Firebase
                    firebaseStatus?.let { status ->
                        Surface(
                            color = when {
                                status.contains("Guardado") || status.contains("exitosamente") -> Color(0xFF4CAF50)
                                status.contains("Error") -> Color(0xFFFF5722)
                                else -> Color(0xFFFF9800)
                            }.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = when {
                                    status.contains("Guardado") || status.contains("exitosamente") -> "✅"
                                    status.contains("Error") -> "❌"
                                    else -> "⏳"
                                },
                                modifier = Modifier.padding(6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (showMenuButton) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Volver al menú",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Botón de cancelar (solo en modo parte)
                    if (currentMode == ChatMode.CREAR_PARTE) {
                        IconButton(onClick = onCancelarParte) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancelar parte",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Botón de info
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Información",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Indicador de modo con animación
            AnimatedVisibility(
                visible = currentMode == ChatMode.CREAR_PARTE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                    }
                }
            }
        }
    }
}
