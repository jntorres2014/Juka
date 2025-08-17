package com.example.juka

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.juka.ui.theme.JukaTheme
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private val RECORD_AUDIO_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        setContent {
            JukaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    // Launcher para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.sendImageMessage(it.toString())
        }
    }

    // Auto-scroll al último mensaje
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header con indicador de estado
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Juka 🎣",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (isTyping) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "escribiendo...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    if (isAnalyzing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "analizando...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            actions = {
                // Botón de estadísticas
                IconButton(onClick = {
                    val stats = viewModel.getConversationStats()
                    Toast.makeText(context, stats, Toast.LENGTH_LONG).show()
                }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Estadísticas",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                IconButton(onClick = { viewModel.clearMessages() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Limpiar chat",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )

        // Lista de mensajes
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }

            // Indicador de análisis de imagen
            if (isAnalyzing) {
                item {
                    AnalyzingIndicator()
                }
            }

            // Indicador de escritura
            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Input area
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
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
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
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botones de envío
                Row {
                    // Botón de audio
                    AudioRecordButton(
                        onAudioRecorded = { audioPath ->
                            viewModel.sendAudioMessage(audioPath)
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Botón de texto
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
}

// 🔥 COMPONENTE MessageBubble COMPLETO 🔥
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Avatar del bot
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
            modifier = Modifier.widthIn(max = 280.dp)
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
                            Text(
                                text = message.content,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        MessageType.AUDIO -> {
                            AudioMessageContent(
                                audioPath = message.content,
                                isUser = isUser
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

            // Timestamp
            Text(
                text = message.timestamp,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // Avatar del usuario
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

// 🔥 INDICADORES PARA IA 🔥
@Composable
fun TypingIndicator() {
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
                Text(
                    "Juka está escribiendo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AnalyzingIndicator() {
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
                Icons.Default.Search,
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
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Analizando imagen con IA...",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// 🔥 COMPONENTES DE AUDIO E IMAGEN 🔥
@Composable
fun AudioRecordButton(onAudioRecorded: (String) -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }

    FloatingActionButton(
        onClick = {
            if (isRecording) {
                // Detener grabación
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                    val audioFile = File(context.filesDir, "audio_${System.currentTimeMillis()}.m4a")
                    onAudioRecorded(audioFile.absolutePath)
                    isRecording = false
                    mediaRecorder = null
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al detener grabación", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Iniciar grabación
                try {
                    val audioFile = File(context.filesDir, "temp_audio_${System.currentTimeMillis()}.m4a")
                    mediaRecorder = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(audioFile.absolutePath)
                        prepare()
                        start()
                    }
                    isRecording = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al iniciar grabación", Toast.LENGTH_SHORT).show()
                }
            }
        },
        modifier = Modifier.size(48.dp),
        containerColor = if (isRecording)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.secondary
    ) {
        Icon(
            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Detener grabación" else "Grabar audio",
            tint = if (isRecording)
                MaterialTheme.colorScheme.onError
            else
                MaterialTheme.colorScheme.onSecondary
        )
    }
}

@Composable
fun AudioMessageContent(audioPath: String, isUser: Boolean) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    isPlaying = false
                } else {
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(audioPath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                isPlaying = false
                                release()
                                mediaPlayer = null
                            }
                        }
                        isPlaying = true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al reproducir audio", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Detener" else "Reproducir",
                tint = if (isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = if (isPlaying) "Reproduciendo..." else "Mensaje de audio",
            color = if (isUser)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageMessageContent(imagePath: String, isUser: Boolean) {
    Column {
        Image(
            painter = rememberAsyncImagePainter(imagePath),
            contentDescription = "Imagen enviada",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Imagen enviada",
            fontSize = 12.sp,
            color = if (isUser)
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}