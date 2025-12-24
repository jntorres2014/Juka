package com.example.juka.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.CampoParte
import com.example.juka.domain.model.ChatMode

@Composable
fun EnhancedMessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendImage: () -> Unit,
    onSendAudio: (String) -> Unit,
    onSendLocation: () -> Unit,
    currentMode: ChatMode,
    isProcessing: Boolean,
    onCreateParte: () -> Unit,
    onOpenCounter: () -> Unit = {} // ✅ 1. Nuevo parámetro recibido
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // === BOTÓN GIGANTE "CREAR PARTE" (Igual que antes) ===
            if (currentMode == ChatMode.GENERAL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onCreateParte,
                        modifier = Modifier.fillMaxWidth(0.6f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear Parte de Pesca", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // === BOTÓN UBICACIÓN (Igual que antes) ===
            if (currentMode == ChatMode.CREAR_PARTE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onSendLocation,
                        modifier = Modifier.fillMaxWidth(0.8f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quiero cargar mi ubicación", fontSize = 14.sp)
                    }
                }
            }

            // === BARRA DE INPUT PRINCIPAL ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 1. BOTÓN IMAGEN (Existente)
                IconButton(
                    onClick = onSendImage,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            when (currentMode) {
                                ChatMode.GENERAL -> MaterialTheme.colorScheme.primaryContainer
                                ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiaryContainer
                            },
                            CircleShape
                        ),
                    enabled = !isProcessing
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Enviar imagen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ✅ 2. NUEVO BOTÓN CONTADOR (Solo en modo General)
                // Lo ponemos aquí para agrupar "adjuntos" a la izquierda
                if (currentMode == ChatMode.GENERAL) {
                // 3. CAMPO DE TEXTO (El resto sigue igual)
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                isProcessing -> "Procesando..."
                                currentMode == ChatMode.GENERAL -> "Pregúntame..."
                                else -> "Contame los detalles..."
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = !isProcessing,
                    trailingIcon = {
                        if (messageText.isNotEmpty()) {
                            IconButton(onClick = { onMessageChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 4. BOTÓN AUDIO
                WorkingAudioButton(
                    onAudioTranscribed = onSendAudio,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 5. BOTÓN ENVIAR (FAB)
                FloatingActionButton(
                    onClick = onSendMessage,
                    modifier = Modifier.size(48.dp),
                    containerColor = when {
                        !messageText.isBlank() && !isProcessing -> when (currentMode) {
                            ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
                            ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
                        }
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (!messageText.isBlank() && !isProcessing)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    when {
                        isProcessing -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp
                        )
                        else -> Icon(Icons.Default.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
}
// NUEVO: Input simplificado para modo parte
@Composable
fun SimpleParteInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendAudio: (String) -> Unit,
    isWaitingForResponse: Boolean,
    currentField: CampoParte?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Indicador del campo actual
            AnimatedVisibility(visible = currentField != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Respondiendo: ${currentField?.displayName ?: ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Campo de texto
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when (currentField) {
                                CampoParte.ESPECIES -> "Ej: 2 pejerreyes y 1 róbalo"
                                CampoParte.FECHA -> "Ej: hoy, ayer, 25/10"
                                CampoParte.HORARIOS -> "Ej: de 6 a 11"
                                else -> "Escribí tu respuesta..."
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botón de audio
                WorkingAudioButton(
                    onAudioTranscribed = onSendAudio,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Botón enviar
                FloatingActionButton(
                    onClick = onSendMessage,
                    modifier = Modifier.size(48.dp),
                    containerColor = if (messageText.isNotBlank())
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Enviar",
                        tint = if (messageText.isNotBlank())
                            MaterialTheme.colorScheme.onTertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}