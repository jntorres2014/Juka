// AudioButton.kt - Botón estilo WhatsApp "Mantener presionado"
package com.example.juka

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordButton(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val audioManager = remember { AudioManager(context) }

    var audioState by remember { mutableStateOf<AudioState>(AudioState.Idle) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var isCancelZone by remember { mutableStateOf(false) }

    // Animaciones
    val scale by animateFloatAsState(
        targetValue = when {
            isRecording -> 1.3f
            isDragging -> 1.1f
            else -> 1.0f
        },
        animationSpec = tween(150),
        label = "button_scale"
    )

    val pulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Inicializar AudioManager
    LaunchedEffect(Unit) {
        audioManager.initialize()
    }

    // Timer de grabación (solo para mostrar tiempo transcurrido)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingTime++
            }
        } else {
            recordingTime = 0
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            audioManager.destroy()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // 🚨 INDICADOR DE CANCELAR (cuando está arrastrando)
        if (isRecording && isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp)
                    .background(
                        color = if (isCancelZone)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isCancelZone) Icons.Default.Cancel else Icons.Default.SwipeLeft,
                        contentDescription = null,
                        tint = if (isCancelZone)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCancelZone)
                            "¡Suelta para cancelar!"
                        else
                            "← Arrastra para cancelar",
                        color = if (isCancelZone)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = if (isCancelZone) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 🎤 BOTÓN PRINCIPAL
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Círculo de fondo para grabación
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size((80 * pulseScale).dp)
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }

            // Botón principal
            FloatingActionButton(
                onClick = { }, // No usamos onClick, solo drag
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // 🎬 INICIAR GRABACIÓN
                                isRecording = true
                                isDragging = false
                                dragOffset = Offset.Zero
                                isCancelZone = false
                                audioState = AudioState.Listening

                                // Vibración de inicio
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                // Iniciar grabación (sin límite de tiempo)
                                scope.launch {
                                    try {
                                        val result = audioManager.startListening()

                                        // Solo procesar si la grabación se completó (no fue cancelada)
                                        if (result !is AudioResult.Cancelled) {
                                            when (result) {
                                                is AudioResult.Success -> {
                                                    onAudioTranscribed(result.transcript)
                                                    audioState = AudioState.Idle
                                                }
                                                is AudioResult.Error -> {
                                                    audioState = AudioState.Error(result.message)
                                                }
                                                is AudioResult.PermissionDenied -> {
                                                    audioState = AudioState.Error("Necesitás permisos de micrófono")
                                                }
                                                else -> {
                                                    audioState = AudioState.Idle
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (!isCancelZone) {
                                            audioState = AudioState.Error("Error: ${e.message}")
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                // 🎯 DETECTAR ARRASTRE PARA CANCELAR
                                dragOffset += dragAmount
                                isDragging = true

                                // Zona de cancelar: arrastrar hacia la izquierda > 100px
                                isCancelZone = dragOffset.x < -100f

                                if (isCancelZone) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                // 🛑 FINALIZAR GRABACIÓN
                                isDragging = false

                                if (isCancelZone) {
                                    // ❌ CANCELAR
                                    audioManager.cancelListening()
                                    audioState = AudioState.Idle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    // ✅ COMPLETAR GRABACIÓN
                                    audioManager.stopListening()
                                    audioState = AudioState.Processing
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }

                                isRecording = false
                                dragOffset = Offset.Zero
                                isCancelZone = false
                            }
                        )
                    },
                containerColor = when {
                    isCancelZone -> MaterialTheme.colorScheme.error
                    isRecording -> MaterialTheme.colorScheme.error
                    audioState is AudioState.Processing -> MaterialTheme.colorScheme.tertiary
                    audioState is AudioState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                },
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = if (isRecording) 12.dp else 6.dp
                )
            ) {
                when {
                    isCancelZone -> {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancelar grabación",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    isRecording -> {
                        // Mostrar tiempo o icono de grabación
                        if (recordingTime > 0) {
                            Text(
                                text = "${recordingTime}s",
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Grabando",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    audioState is AudioState.Processing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            strokeWidth = 3.dp
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Mantener presionado para grabar",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 📝 INSTRUCCIONES Y ESTADO
        Text(
            text = when {
                isRecording && isCancelZone -> "🚫 Cancelando..."
                isRecording -> "🎤 Grabando... ${recordingTime}s"
                audioState is AudioState.Processing -> "✨ Procesando audio..."
                audioState is AudioState.Error -> "⚠️ ${(audioState as AudioState.Error).message}"
                else -> "Mantené presionado para grabar"
            },
            fontSize = 12.sp,
            color = when {
                isRecording && isCancelZone -> MaterialTheme.colorScheme.error
                audioState is AudioState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            },
            fontWeight = if (audioState is AudioState.Error || isCancelZone) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Instrucciones adicionales
        if (!isRecording && audioState !is AudioState.Error && audioState !is AudioState.Processing) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Arrastra ← para cancelar",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        // Reset automático de errores
        if (audioState is AudioState.Error) {
            LaunchedEffect(audioState) {
                kotlinx.coroutines.delay(4000)
                audioState = AudioState.Idle
            }
        }
    }
}