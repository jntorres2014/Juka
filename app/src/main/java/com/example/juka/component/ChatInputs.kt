package com.example.juka.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
    onNavigateToWizard: () -> Unit,

) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {

            // ── Botones de creación de parte (solo en modo general) ──
            if (currentMode == ChatMode.GENERAL) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primario: wizard paso a paso
                    Button(
                        onClick = onNavigateToWizard,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear parte asistido", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Secundario: flujo conversacional por chat
                    OutlinedButton(
                        onClick = onCreateParte,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Crear parte por chat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // ── Botón de ubicación (solo en modo crear parte) ──
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

            // ── Barra de input principal ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Botón imagen: SOLO se muestra en modo CREAR_PARTE, donde la
                // foto se agrega a parteData.imagenes y queda asociada al parte.
                // En modo GENERAL antes existía pero solo guardaba la foto en
                // memoria interna sin procesarla (no Gemini Vision, no upload),
                // o sea decorativo y generaba expectativas falsas. Para
                // identificar especies está la pantalla "Identificar pez" del
                // menú principal.
                if (currentMode == ChatMode.CREAR_PARTE) {
                    IconButton(
                        onClick = onSendImage,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                CircleShape
                            ),
                        enabled = !isProcessing
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Agregar foto al parte",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Campo de texto
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

                // Botón audio
                WorkingAudioButton(
                    onAudioTranscribed = onSendAudio,
                    modifier = Modifier.size(48.dp),
                    compact = true
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Botón enviar
                FloatingActionButton(
                    onClick = onSendMessage,
                    modifier = Modifier.size(48.dp),
                    containerColor = when {
                        messageText.isNotBlank() && !isProcessing -> when (currentMode) {
                            ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
                            ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
                        }
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (messageText.isNotBlank() && !isProcessing)
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

// ── Input simplificado para modo crear parte ──
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when (currentField) {
                                CampoParte.ESPECIES -> "Ej: 2 pejerreyes y 1 róbalo"
                                CampoParte.FECHA -> "Ej: hoy, ayer, 25/10/2024"
                                CampoParte.HORARIOS -> "Ej: de 6 a 11"
                                CampoParte.MODALIDAD -> "Ej: De costa, embarcado, con red, submarina..."
                                CampoParte.CANAS -> "Ej: Con 3 cañas"
                                CampoParte.OBSERVACIONES -> "Ej: llovía mucho"
                                else -> "Escribí tu respuesta..."
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                WorkingAudioButton(
                    onAudioTranscribed = onSendAudio,
                    modifier = Modifier.size(48.dp),
                    compact = true
                )

                Spacer(modifier = Modifier.width(4.dp))

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