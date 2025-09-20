// EnhancedChatScreen.kt - UI con separación de dos modos de chat
package com.example.juka

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedChatScreen(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: EnhancedChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Estados del ViewModel
    val currentMode by viewModel.currentMode.collectAsState()
    val generalMessages by viewModel.generalMessages.collectAsState()
    val parteSession by viewModel.parteSession.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()

    // Estados locales
    var messageText by remember { mutableStateOf("") }
    var showParteActions by remember { mutableStateOf(false) }

    // Determinar qué mensajes mostrar según el modo
    val currentMessages = when (currentMode) {
        ChatMode.GENERAL -> generalMessages
        ChatMode.CREAR_PARTE -> parteSession?.messages ?: emptyList()
    }

    // Launcher para imágenes
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendImageMessage(it.toString()) }
    }

    // Auto-scroll cuando cambian los mensajes
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ================== HEADER DINÁMICO ==================
        EnhancedChatHeader(
            user = user,
            currentMode = currentMode,
            parteSession = parteSession,
            firebaseStatus = firebaseStatus,
            onModeChange = { mode ->
                when (mode) {
                    ChatMode.GENERAL -> viewModel.volverAChatGeneral()
                    ChatMode.CREAR_PARTE -> viewModel.iniciarCrearParte()
                }
            },
            onCancelarParte = { viewModel.cancelarParte() },
            onInfoClick = {
                val stats = viewModel.getConversationStats()
                android.widget.Toast.makeText(context, stats, android.widget.Toast.LENGTH_LONG).show()
            }
        )

        // ================== PROGRESO DEL PARTE (SI APLICA) ==================
        if (currentMode == ChatMode.CREAR_PARTE && parteSession != null) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                ParteProgressIndicator(
                    parteData = parteSession!!.parteData,
                    onGuardarBorrador = { viewModel.guardarParteBorrador() },
                    onCompletarParte = { viewModel.completarYEnviarParte() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // ================== LISTA DE MENSAJES ==================
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(currentMessages) { message ->
                EnhancedMessageBubble(
                    message = message,
                    currentMode = currentMode
                )
            }

            // Indicadores de estado
            if (isAnalyzing) {
                item {
                    MLKitAnalyzingIndicator()
                }
            }

            if (isTyping) {
                item {
                    TypingIndicatorEnhanced()
                }
            }

            // Espaciador final
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ================== INPUT MEJORADO ==================
        EnhancedMessageInput(
            messageText = messageText,
            onMessageChange = { messageText = it },
            onSendMessage = {
                if (messageText.isNotBlank()) {
                    viewModel.sendTextMessage(messageText.trim())
                    messageText = ""
                }
            },
            onSendImage = { imagePickerLauncher.launch("image/*") },
            onSendAudio = { transcript -> viewModel.sendAudioTranscript(transcript) },
            currentMode = currentMode,
            isProcessing = isTyping || isAnalyzing,
            onCreateParte = { viewModel.iniciarCrearParte() }
        )
    }
}

@Composable
fun EnhancedChatHeader(
    user: com.google.firebase.auth.FirebaseUser,
    currentMode: ChatMode,
    parteSession: ParteSessionChat?,
    firebaseStatus: String?,
    onModeChange: (ChatMode) -> Unit,
    onCancelarParte: () -> Unit,
    onInfoClick: () -> Unit
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
                                "Progreso: ${it.parteData.porcentajeCompletado}% • ML Kit activo 🤖"
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
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
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
                        Text(
                            text = "Extracción automática con ML Kit activada",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ParteProgressIndicator(
    parteData: ParteEnProgreso,
    onGuardarBorrador: () -> Unit,
    onCompletarParte: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📋 Progreso del parte",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Text(
                        text = "${parteData.porcentajeCompletado}% completado",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Botón guardar borrador
                    OutlinedButton(
                        onClick = onGuardarBorrador,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Borrador", fontSize = 12.sp)
                    }

                    // Botón completar (solo si está suficientemente completo)
                    Button(
                        onClick = onCompletarParte,
                        enabled = parteData.porcentajeCompletado >= 70,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enviar", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra de progreso
            LinearProgressIndicator(
                progress = parteData.porcentajeCompletado / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    parteData.porcentajeCompletado >= 80 -> Color(0xFF4CAF50)
                    parteData.porcentajeCompletado >= 50 -> Color(0xFFFF9800)
                    else -> Color(0xFFFF5722)
                },
                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
            )

            // Campos faltantes (solo mostrar algunos)
            if (parteData.camposFaltantes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Falta: ${parteData.camposFaltantes.take(3).joinToString(", ")}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EnhancedMessageBubble(
    message: ChatMessageWithMode,
    currentMode: ChatMode
) {
    val isUser = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = when (currentMode) {
                    ChatMode.GENERAL -> MaterialTheme.colorScheme.secondary
                    ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
                }
            ) {
                Icon(
                    when (currentMode) {
                        ChatMode.GENERAL -> Icons.Default.SmartToy
                        ChatMode.CREAR_PARTE -> Icons.Default.Assignment
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = if (isUser) {
                    when (currentMode) {
                        ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
                        ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
                    }
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            FormattedText(
                                text = message.content,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MessageType.AUDIO -> {
                            AudioMessageContent(
                                content = message.content,
                                isUser = isUser,
                                currentMode = currentMode
                            )
                        }
                        MessageType.IMAGE -> {
                            ImageMessageContent(
                                imagePath = message.content,
                                isUser = isUser
                            )
                        }
                    }
                }
            }

            // Timestamp y modo
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.timestamp,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (currentMode == ChatMode.CREAR_PARTE && !isUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = when (currentMode) {
                    ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
                    ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
                }
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun MLKitAnalyzingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onTertiary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Extrayendo información con ML Kit...",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun AudioMessageContent(
    content: String,
    isUser: Boolean,
    currentMode: ChatMode
) {
    val transcript = content.removePrefix("🎤 \"").removeSuffix("\"")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                when (currentMode) {
                    ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
                    ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
                }
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mensaje de voz",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                if (currentMode == ChatMode.CREAR_PARTE && !isUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "\"$transcript\"",
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EnhancedMessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendImage: () -> Unit,
    onSendAudio: (String) -> Unit,
    currentMode: ChatMode,
    isProcessing: Boolean,
    onCreateParte: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Botón "Crear Parte" prominente (solo en modo general)
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
                        Text(
                            "Crear Parte de Pesca",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Input principal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Botón de imagen
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

                // Campo de texto
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                isProcessing -> "Procesando..."
                                currentMode == ChatMode.GENERAL -> "Pregúntame sobre pesca..."
                                else -> "Contame los detalles de tu jornada..."
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

                // Botón de audio
                WorkingAudioButton(
                    onAudioTranscribed = onSendAudio,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Botón de envío
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
                        else -> Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar"
                        )
                    }
                }
            }
        }
    }
}

// ================== COMPONENTES AUXILIARES ==================

@Composable
fun FormattedText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val formattedText = remember(text) {
        buildAnnotatedString {
            var currentIndex = 0
            val boldRegex = """\*\*(.*?)\*\*""".toRegex()
            val boldMatches = boldRegex.findAll(text).toList()

            if (boldMatches.isEmpty()) {
                append(text)
            } else {
                boldMatches.forEach { match ->
                    if (match.range.first > currentIndex) {
                        append(text.substring(currentIndex, match.range.first))
                    }
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groupValues[1])
                    }
                    currentIndex = match.range.last + 1
                }
                if (currentIndex < text.length) {
                    append(text.substring(currentIndex))
                }
            }
        }
    }

    Text(
        text = formattedText,
        color = color,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        modifier = modifier
    )
}

