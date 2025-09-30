package com.example.juka.navigation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.WorkingAudioButton
import com.example.juka.chat.AnalyzingIndicator
import com.example.juka.chat.MessageBubble
import com.example.juka.chat.TypingIndicator
import com.example.juka.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

// ✅ TU CHATSCREEN CON PEQUEÑO CAMBIO
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenWithUser(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: ChatViewModel = viewModel()
) {
    // TODO: Aquí va tu ChatScreen actual, solo agregamos el nombre del usuario en el header

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
        // ✅ HEADER CON NOMBRE DEL USUARIO
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Hola ${user.displayName?.split(" ")?.first() ?: "Pescador"} 🎣",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (isTyping) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "escribiendo...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            actions = {
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
            }
        )

        // ✅ RESTO DE TU CHATSCREEN ACTUAL (sin cambios)
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

            if (isAnalyzing) {
                item {
                    AnalyzingIndicator()
                }
            }

            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Input area (tu código actual)
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
                    // Botón de audio (tu componente actual)
                    WorkingAudioButton(
                        onAudioTranscribed = { transcript ->
                            viewModel.sendAudioTranscript(transcript)
                        },
                        modifier = Modifier.size(48.dp)
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
