// ChatScreen.kt - VERSIÓN COMPLETA MEJORADA
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.ChatViewModel
import com.example.juka.viewmodel.MessageType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Data classes para el sistema mejorado
data class ProgresoPartePesca(
    val campos: List<Pair<String, Boolean>>,
    val porcentajeCompletado: Int,
    val siguiente: String?
)

enum class ChatState {
    IDLE,
    LISTENING_AUDIO,
    PROCESSING_AUDIO,
    UPLOADING_IMAGE,
    WAITING_RESPONSE,
    TYPING_RESPONSE,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenCompleto(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Estados del chat
    var messageText by remember { mutableStateOf("") }
    var chatState by remember { mutableStateOf(ChatState.IDLE) }
    var mostrarSugerencias by remember { mutableStateOf(true) }

    // Estados del ViewModel
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()

    // Progreso del parte actual (simulado - puedes integrarlo en tu ViewModel)
    var progresoActual by remember { mutableStateOf<ProgresoPartePesca?>(null) }

    // Sugerencias inteligentes
    val sugerenciasIniciales = listOf(
        "🎣 ¿Cómo estuvo tu última jornada?",
        "📍 ¿Dónde pescaste?",
        "🐟 ¿Qué especies capturaste?",
        "⏰ ¿A qué hora empezaste?",
        "🚤 ¿Fue embarcado o desde costa?"
    )

    val sugerenciasContextuales = remember(messages.size) {
        when {
            messages.isEmpty() -> sugerenciasIniciales
            messages.size < 3 -> listOf(
                "Ayer pesqué 3 dorados",
                "Hoy fui a la laguna",
                "Usé lombriz como carnada"
            )
            else -> listOf(
                "¿Consejos para el próximo fin de semana?",
                "¿Qué carnada me recomendás?",
                "Subir foto de mi captura"
            )
        }
    }

    // Launcher para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            chatState = ChatState.UPLOADING_IMAGE
            scope.launch {
                delay(1000) // Simular upload
                viewModel.sendImageMessage(it.toString())
                chatState = ChatState.IDLE
            }
        }
    }

    // Actualizar estado del chat según ViewModel
    LaunchedEffect(isTyping, isAnalyzing) {
        chatState = when {
            isAnalyzing -> ChatState.PROCESSING_AUDIO
            isTyping -> ChatState.TYPING_RESPONSE
            else -> ChatState.IDLE
        }
    }

    // Auto-scroll al último mensaje
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Simular progreso del parte (integra con tu lógica real)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val ultimoMensaje = messages.lastOrNull { it.isFromUser }?.content ?: ""
            progresoActual = simularProgreso(ultimoMensaje)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header mejorado
        ChatHeader(
            user = user,
            chatState = chatState,
            firebaseStatus = firebaseStatus,
            onInfoClick = {
                val stats = viewModel.getConversationStats()
                android.widget.Toast.makeText(context, stats, android.widget.Toast.LENGTH_LONG).show()
            }
        )

        // Indicador de progreso del parte
        AnimatedVisibility(
            visible = progresoActual != null && progresoActual!!.porcentajeCompletado < 100,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            progresoActual?.let { progreso ->
                IndicadorProgresoFecha(
                    progreso = progreso,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Sugerencias rápidas
        AnimatedVisibility(
            visible = mostrarSugerencias && messages.isEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            SugerenciasRapidas(
                sugerencias = sugerenciasIniciales,
                onSugerenciaClick = { sugerencia ->
                    messageText = sugerencia
                    mostrarSugerencias = false
                }
            )
        }

        // Lista de mensajes
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mensaje de bienvenida si no hay mensajes
            if (messages.isEmpty()) {
                item {
                    WelcomeMessage(userName = user.displayName?.split(" ")?.first() ?: "Pescador")
                }
            }

            // Mensajes del chat
            items(messages) { message ->
                MessageBubbleEnhanced(message = message)
            }

            // Indicadores de estado
            if (isAnalyzing) {
                item {
                    AnalyzingIndicatorEnhanced()
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

        // Input mejorado
        EnhancedMessageInput(
            messageText = messageText,
            onMessageChange = {
                messageText = it
                mostrarSugerencias = it.isBlank() && messages.isEmpty()
            },
            onSendMessage = {
                if (messageText.isNotBlank()) {
                    viewModel.sendTextMessage(messageText.trim())
                    messageText = ""
                    mostrarSugerencias = false
                }
            },
            onSendImage = {
                imagePickerLauncher.launch("image/*")
            },
            onSendAudio = { transcript ->
                chatState = ChatState.PROCESSING_AUDIO
                viewModel.sendAudioTranscript(transcript)
            },
            chatState = chatState,
            suggestions = if (messageText.isBlank()) sugerenciasContextuales else emptyList()
        )
    }
}

@Composable
fun ChatHeader(
    user: com.google.firebase.auth.FirebaseUser,
    chatState: ChatState,
    firebaseStatus: String?,
    onInfoClick: () -> Unit
) {
    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hola ${user.displayName?.split(" ")?.first() ?: "Pescador"} 🎣",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                Text(
                    text = when (chatState) {
                        ChatState.LISTENING_AUDIO -> "🎤 Escuchando..."
                        ChatState.PROCESSING_AUDIO -> "🔄 Procesando audio..."
                        ChatState.TYPING_RESPONSE -> "✍️ Juka está escribiendo..."
                        ChatState.UPLOADING_IMAGE -> "📤 Subiendo imagen..."
                        ChatState.WAITING_RESPONSE -> "⏳ Esperando respuesta..."
                        ChatState.ERROR -> "❌ Error de conexión"
                        else -> firebaseStatus ?: "Tu asistente de pesca inteligente"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }

            Row {
                // Indicador de Firebase
                firebaseStatus?.let { status ->
                    Surface(
                        color = when {
                            status.contains("Guardado") -> Color(0xFF4CAF50)
                            status.contains("Error") -> Color(0xFFFF5722)
                            else -> Color(0xFFFF9800)
                        }.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                status.contains("Guardado") -> "✅"
                                status.contains("Error") -> "❌"
                                else -> "⏳"
                            },
                            modifier = Modifier.padding(6.dp),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Información",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeMessage(userName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎣",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "¡Hola $userName!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Soy Juka, tu asistente de pesca inteligente",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "✨ Puedo ayudarte a:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                horizontalAlignment = Alignment.Start
            ) {
                listOf(
                    "📝 Registrar tus jornadas de pesca",
                    "🐟 Identificar especies argentinas",
                    "🎯 Darte consejos personalizados",
                    "📊 Guardar todo en Firebase automáticamente"
                ).forEach { feature ->
                    Text(
                        text = feature,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "¡Empezá contándome sobre tu última jornada! 🌊",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SugerenciasRapidas(
    sugerencias: List<String>,
    onSugerenciaClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(sugerencias) { sugerencia ->
            SuggestionChip(
                onClick = { onSugerenciaClick(sugerencia) },
                label = {
                    Text(
                        text = sugerencia,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                },
                modifier = Modifier.wrapContentWidth()
            )
        }
    }
}

@Composable
fun IndicadorProgresoFecha(
    progreso: ProgresoPartePesca,
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
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📝 Completando reporte",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Text(
                    text = "${progreso.porcentajeCompletado}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progreso.porcentajeCompletado / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            if (progreso.siguiente != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⏭️ Siguiente: ${progreso.siguiente}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun MessageBubbleEnhanced(message: ChatMessage) {
    val isUser = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    Icons.Default.SmartToy,
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
                color = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
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
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MessageType.AUDIO -> {
                            AudioMessageContent(
                                content = message.content,
                                isUser = isUser
                            )
                        }
                        MessageType.IMAGE -> {
                            ImageMessageContentEnhanced(
                                imagePath = message.content,
                                isUser = isUser
                            )
                        }
                    }
                }
            }

            Text(
                text = message.timestamp,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
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
fun AudioMessageContent(
    content: String,
    isUser: Boolean
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
            tint = if (isUser)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = "Mensaje de voz",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

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
fun ImageMessageContentEnhanced(imagePath: String, isUser: Boolean) {
    Column {
        Image(
            painter = rememberAsyncImagePainter(imagePath),
            contentDescription = "Imagen enviada",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isUser)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Imagen de pesca",
                fontSize = 12.sp,
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun TypingIndicatorEnhanced() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    var alpha by remember { mutableStateOf(0.3f) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(400L * index)
                            alpha = 1f
                            delay(1200L)
                            alpha = 0.3f
                            delay(400L)
                        }
                    }

                    Text(
                        text = "●",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    "Juka está escribiendo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun AnalyzingIndicatorEnhanced() {
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
                Icons.Default.Psychology,
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
                    "Analizando con IA...",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendImage: () -> Unit,
    onSendAudio: (String) -> Unit,
    chatState: ChatState,
    suggestions: List<String> = emptyList()
) {
    var showSuggestions by remember { mutableStateOf(false) }

    Column {
        // Sugerencias contextuales
        if (showSuggestions && suggestions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onMessageChange(suggestion)
                            showSuggestions = false
                        },
                        label = { Text(suggestion, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Input principal
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
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
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    enabled = chatState == ChatState.IDLE
                ) {
                    Icon(
                        when (chatState) {
                            ChatState.UPLOADING_IMAGE -> Icons.Default.CloudUpload
                            else -> Icons.Default.Image
                        },
                        contentDescription = "Enviar imagen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Campo de texto mejorado
                OutlinedTextField(
                    value = messageText,
                    onValueChange = {
                        onMessageChange(it)
                        showSuggestions = it.isBlank() && suggestions.isNotEmpty()
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(when (chatState) {
                            ChatState.WAITING_RESPONSE -> "Juka está pensando..."
                            ChatState.TYPING_RESPONSE -> "Juka está escribiendo..."
                            ChatState.PROCESSING_AUDIO -> "Procesando audio..."
                            ChatState.UPLOADING_IMAGE -> "Subiendo imagen..."
                            else -> "Contame sobre tu jornada de pesca..."
                        })
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = chatState !in listOf(
                        ChatState.WAITING_RESPONSE,
                        ChatState.TYPING_RESPONSE,
                        ChatState.PROCESSING_AUDIO,
                        ChatState.UPLOADING_IMAGE
                    ),
                    trailingIcon = {
                        if (messageText.isNotEmpty()) {
                            IconButton(onClick = { onMessageChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botones de envío
                Row {
                    // Botón de audio (usando tu componente existente)
                    WorkingAudioButton(
                        onAudioTranscribed = onSendAudio,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Botón de texto
                    FloatingActionButton(
                        onClick = onSendMessage,
                        modifier = Modifier.size(48.dp),
                        containerColor = if (messageText.isNotBlank() && chatState == ChatState.IDLE)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (messageText.isNotBlank() && chatState == ChatState.IDLE)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        when (chatState) {
                            ChatState.WAITING_RESPONSE,
                            ChatState.TYPING_RESPONSE -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp
                            )
                            else -> Icon(
                                Icons.Default.Send,
                                contentDescription = "Enviar texto"
                            )
                        }
                    }
                }
            }
        }
    }
}

// Función auxiliar para simular progreso del parte
// (Integra esto con tu FishingDataExtractor real)
private fun simularProgreso(ultimoMensaje: String): ProgresoPartePesca? {
    if (ultimoMensaje.isBlank()) return null

    val mensaje = ultimoMensaje.lowercase()

    // Simular detección de campos completados
    val campos = listOf(
        "Día" to (mensaje.contains("ayer") || mensaje.contains("hoy") || mensaje.contains("lunes") ||
                mensaje.contains("martes") || mensaje.contains("miércoles") || mensaje.contains("jueves") ||
                mensaje.contains("viernes") || mensaje.contains("sábado") || mensaje.contains("domingo")),
        "Hora inicio" to (mensaje.contains(Regex("""\d+:\d+""")) || mensaje.contains("mañana") ||
                mensaje.contains("tarde") || mensaje.contains("noche")),
        "Hora fin" to (mensaje.contains("hasta") || mensaje.contains("terminé") || mensaje.contains("volví")),
        "Cantidad peces" to (mensaje.contains(Regex("""\d+""")) && (mensaje.contains("pez") ||
                mensaje.contains("dorado") || mensaje.contains("pejerrey") || mensaje.contains("bagre"))),
        "Tipo pesca" to (mensaje.contains("embarcado") || mensaje.contains("costa") || mensaje.contains("orilla")),
        "Cañas usadas" to (mensaje.contains("caña") || mensaje.contains("vara"))
    )

    val completados = campos.count { it.second }
    val porcentaje = (completados.toFloat() / campos.size * 100).toInt()

    if (porcentaje == 0) return null

    return ProgresoPartePesca(
        campos = campos,
        porcentajeCompletado = porcentaje,
        siguiente = campos.find { !it.second }?.first
    )
}

// ===== COMPONENTES ADICIONALES PARA MEJORAR LA EXPERIENCIA =====

@Composable
fun QuickActionButtons(
    onRapidReport: () -> Unit,
    onTakePhoto: () -> Unit,
    onViewReports: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        item {
            QuickActionCard(
                icon = Icons.Default.Speed,
                text = "Reporte\nRápido",
                color = MaterialTheme.colorScheme.primary,
                onClick = onRapidReport
            )
        }
        item {
            QuickActionCard(
                icon = Icons.Default.PhotoCamera,
                text = "Foto de\nCaptura",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onTakePhoto
            )
        }
        item {
            QuickActionCard(
                icon = Icons.Default.Book,
                text = "Mis\nReportes",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onViewReports
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 80.dp, height = 70.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                color = color,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ChatMetrics(
    totalMessages: Int,
    reportesCompletos: Int,
    especiesIdentificadas: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem("💬", totalMessages.toString(), "Mensajes")
            MetricItem("📋", reportesCompletos.toString(), "Reportes")
            MetricItem("🐟", especiesIdentificadas.toString(), "Especies")
        }
    }
}

@Composable
fun MetricItem(
    emoji: String,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            fontSize = 16.sp
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// ===== VERSIÓN SIMPLIFICADA PARA INTEGRACIÓN FÁCIL =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenSimplificado(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }

    // Estados del ViewModel (los que ya tienes)
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()

    // Launcher para imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendImageMessage(it.toString()) }
    }

    // Auto-scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header simple
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Hola ${user.displayName?.split(" ")?.first() ?: "Pescador"} 🎣",
                        fontWeight = FontWeight.Bold
                    )
                    firebaseStatus?.let { status ->
                        Text(
                            text = status,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            actions = {
                IconButton(onClick = {
                    val stats = viewModel.getConversationStats()
                    android.widget.Toast.makeText(context, stats, android.widget.Toast.LENGTH_LONG).show()
                }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Estadísticas",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )

        // Lista de mensajes (usando tus componentes existentes)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageBubbleEnhanced(message = message)
            }

            if (isAnalyzing) {
                item { AnalyzingIndicatorEnhanced() }
            }

            if (isTyping) {
                item { TypingIndicatorEnhanced() }
            }
        }

        // Input (tu diseño actual con pequeñas mejoras)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Enviar imagen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Contame sobre tu jornada de pesca...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Tu AudioRecordButton existente
                // REEMPLAZA POR:
                WorkingAudioButton(
                    onAudioTranscribed = { transcript ->
                        viewModel.sendAudioTranscript(transcript)
                    },
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendTextMessage(messageText.trim())
                            messageText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Enviar texto",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}