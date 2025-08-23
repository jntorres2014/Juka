// AudioButton.kt - Componente UI para audio
package com.example.juka

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AudioRecordButton(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { AudioManager(context) }

    var audioState by remember { mutableStateOf<AudioState>(AudioState.Idle) }
    var isListening by remember { mutableStateOf(false) }

    // Inicializar AudioManager
    LaunchedEffect(Unit) {
        audioManager.initialize()
    }

    // Cleanup al destruir
    DisposableEffect(Unit) {
        onDispose {
            audioManager.destroy()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botón principal
        FloatingActionButton(
            onClick = {
                if (isListening) {
                    // Detener grabación
                    audioManager.cancelListening()
                    isListening = false
                    audioState = AudioState.Idle
                } else {
                    // Iniciar grabación
                    isListening = true
                    audioState = AudioState.Listening

                    scope.launch {
                        try {
                            audioState = AudioState.Processing
                            val result = audioManager.startListening()

                            when (result) {
                                is AudioResult.Success -> {
                                    onAudioTranscribed(result.transcript)
                                    audioState = AudioState.Idle
                                }
                                is AudioResult.Error -> {
                                    audioState = AudioState.Error(result.message)
                                }
                                is AudioResult.PermissionDenied -> {
                                    audioState = AudioState.Error("Necesitás dar permisos de micrófono")
                                }
                                is AudioResult.Cancelled -> {
                                    audioState = AudioState.Idle
                                }
                            }
                            isListening = false
                        } catch (e: Exception) {
                            audioState = AudioState.Error("Error inesperado: ${e.message}")
                            isListening = false
                        }
                    }
                }
            },
            modifier = modifier
                .size(if (isListening) 60.dp else 56.dp)
                .scale(if (isListening) 1.1f else 1.0f),
            containerColor = when (audioState) {
                is AudioState.Listening -> MaterialTheme.colorScheme.error
                is AudioState.Processing -> MaterialTheme.colorScheme.tertiary
                is AudioState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
        ) {
            when (audioState) {
                is AudioState.Listening -> {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Detener grabación",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                }
                is AudioState.Processing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 3.dp
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Grabar audio",
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Indicador de estado
        Text(
            text = when (audioState) {
                is AudioState.Idle -> "Presioná para hablar"
                is AudioState.Listening -> "🎤 Escuchando..."
                is AudioState.Processing -> "✨ Procesando..."
                is AudioState.Error -> "⚠️ ${(audioState as AudioState.Error).message}"
            },
            fontSize = 12.sp,
            color = when (audioState) {
                is AudioState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            },
            fontWeight = if (audioState is AudioState.Error) FontWeight.Bold else FontWeight.Normal
        )

        // Reset error después de unos segundos
        if (audioState is AudioState.Error) {
            LaunchedEffect(audioState) {
                kotlinx.coroutines.delay(4000)
                audioState = AudioState.Idle
            }
        }
    }
}