
package com.example.juka

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.viewmodel.ChatMessage
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedChatScreen(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: EnhancedChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Estados del ViewModel
    //val currentMode by viewModel.currentMode.collectAsState()
    val generalMessages by viewModel.generalMessages.collectAsState()
    val parteSession by viewModel.parteSession.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val chatEnabled by viewModel.chatEnabled.collectAsState()


    // NUEVOS estados
    val currentFieldInProgress by viewModel.currentFieldInProgress.collectAsState()
    val showMapPickerFromViewModel by viewModel.showMapPicker.collectAsState()
    val showImagePickerFromViewModel by viewModel.showImagePicker.collectAsState()

    // Estados locales
    var messageText by remember { mutableStateOf("") }
    var showMapPicker by remember { mutableStateOf(false) }
    if (showMapPicker || showMapPickerFromViewModel) {
        MapPickerScreen(
            onDismiss = {
                showMapPicker = false
                viewModel.dismissMapPicker()
            },
            onLocationSelected = { lat, lon, name ->
                viewModel.saveLocation(lat, lon, name)
                showMapPicker = false
                viewModel.dismissMapPicker()
            }
        )
    }

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
                android.widget.Toast.makeText(context, stats, android.widget.Toast.LENGTH_LONG)
                    .show()
            },  // ← CERRAR EL onInfoClick AQUÍ
            showMenuButton = chatEnabled && currentMode == ChatMode.GENERAL,  // ← ESTOS VAN FUERA
            onMenuClick = { viewModel.volverAlMenuPrincipal() }  // ← ESTOS VAN FUERA
        )


        // ================== PROGRESO DEL PARTE (SI APLICA) ==================
        if (currentMode == ChatMode.CREAR_PARTE && parteSession != null) {
            AnimatedVisibility(
                visible = true,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    // Progreso simplificado
                    LinearProgressIndicator(
                        progress = parteSession!!.parteData.porcentajeCompletado / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = when {
                            parteSession!!.parteData.porcentajeCompletado >= 80 -> Color(0xFF4CAF50)
                            parteSession!!.parteData.porcentajeCompletado >= 50 -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )

                    // NUEVO: Botones de acción rápida
                    ParteQuickActions(
                        parteData = parteSession!!.parteData,
                        onCampoSelected = { campo ->
                            viewModel.onCampoParteSelected(campo)
                        },
                        currentFieldInProgress = currentFieldInProgress,
                        onGuardarBorrador = {
                           //viewModel.guardarParteBorrador()
                        },
                        onCompletarParte = {
                            viewModel.completarYEnviarParte()
                        },
                        firebaseStatus = firebaseStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
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
                // ✅ USANDO COMPONENTE CENTRALIZADO
                MessageBubble(
                    message = message,
                    currentMode = currentMode,
                    onOptionClick = { option ->  // ← AGREGAR ESTE HANDLER
                        viewModel.handleOptionClick(option)
                    }
                )

            }

            // Indicadores de estado
            if (isAnalyzing) {
                item {
                    // ✅ COMPONENTE CENTRALIZADO
                    AnalyzingIndicator()
                }
            }

            if (isTyping) {
                item {
                    // ✅ COMPONENTE CENTRALIZADO
                    TypingIndicator()
                }
            }

            // Espaciador final
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ================== INPUT MEJORADO ==================
                when {
                    // Si estamos en modo crear parte, mostrar input de parte
                    currentMode == ChatMode.CREAR_PARTE -> {
                        SimpleParteInput(
                            messageText = messageText,
                            onMessageChange = { messageText = it },
                            onSendMessage = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendTextMessage(messageText.trim())
                                    messageText = ""
                                }
                            },
                            onSendAudio = { transcript ->
                                viewModel.sendAudioTranscript(transcript)
                            },
                            isWaitingForResponse = currentFieldInProgress != null,
                            currentField = currentFieldInProgress
                        )
                    }

                    // Si el chat está habilitado en modo general, mostrar input
                    currentMode == ChatMode.GENERAL -> {
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
                            onSendLocation = { showMapPicker = true },
                            currentMode = currentMode,
                            isProcessing = isTyping || isAnalyzing,
                            onCreateParte = { viewModel.iniciarCrearParte() }
                        )
                    }

                    // Si no, no mostrar input (solo botones del menú)
                    else -> {
                        // Espacio vacío para mantener el layout consistente
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
    // Trigger para selector de imágenes
    LaunchedEffect(showImagePickerFromViewModel) {
        if (showImagePickerFromViewModel) {
            imagePickerLauncher.launch("image/*")
            viewModel.dismissImagePicker()
        }
    }
}

// ================== HEADER ==================

@Composable
fun EnhancedChatHeader(
    user: com.google.firebase.auth.FirebaseUser,
    currentMode: ChatMode,
    parteSession: ParteSessionChat?,
    firebaseStatus: String?,
    onModeChange: (ChatMode) -> Unit,
    onCancelarParte: () -> Unit,
    onInfoClick: () -> Unit,
    showMenuButton: Boolean = false,  // ← NUEVO PARÁMETRO
    onMenuClick: () -> Unit = {}      // ← NUEVO PARÁMETRO
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

// ================== PROGRESS INDICATOR ==================

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

// ================== MESSAGE INPUT ==================

@Composable
fun EnhancedMessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendImage: () -> Unit,
    onSendAudio: (String) -> Unit,
    onSendLocation: () -> Unit, // Nuevo parámetro
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
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Quiero cargar mi ubicación",
                            fontSize = 14.sp
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