// ChatScreenMejorado.kt - VERSIÓN SIN ERRORES DE IMPORT
package com.example.juka

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.*
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

// ===== VERSIÓN SIMPLIFICADA COMPATIBLE CON TU CÓDIGO =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenMejorado(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    var mostrarSugerencias by remember { mutableStateOf(true) }

    // Estados del ViewModel (los que ya tienes)
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()

    // Sugerencias inteligentes
    val sugerenciasActuales = remember(messages.size) {
        when {
            messages.isEmpty() -> listOf(
                "🎣 ¿Cómo estuvo tu última jornada?",
                "📍 ¿Dónde pescaste?",
                "🐟 ¿Qué especies capturaste?",
                "⏰ ¿A qué hora empezaste?"
            )
            messages.size < 3 -> listOf(
                "Ayer pesqué 3 dorados",
                "Hoy fui a la laguna",
                "Usé lombriz como carnada",
                "Fue desde costa"
            )
            else -> listOf(
                "¿Consejos para el próximo fin de semana?",
                "¿Qué carnada me recomendás?",
                "Subir foto de mi captura"
            )
        }
    }

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
        // ===== HEADER MEJORADO =====
        Surface(
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.primary
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
                        Text(
                            text = "Hola ${user.displayName?.split(" ")?.first() ?: "Pescador"} 🎣",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )

                        Text(
                            text = when {
                                isAnalyzing -> "🔄 Analizando imagen..."
                                isTyping -> "✍️ Juka está escribiendo..."
                                firebaseStatus != null -> firebaseStatus!!
                                else -> "Tu asistente de pesca inteligente"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Indicador de Firebase
                        firebaseStatus?.let { status ->
                            Surface(
                                color = when {
                                    status.contains("Guardado") -> Color(0xFF4CAF50)
                                    status.contains("Error") -> Color(0xFFFF5722)
                                    else -> Color(0xFFFF9800)
                                }.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(end = 8.dp)
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
                        }

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
                }

                // Progreso del parte (simplificado)
                if (messages.isNotEmpty()) {
                    val progreso = calcularProgresoSimple(messages)
                    if (progreso.porcentaje < 100 && progreso.porcentaje > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📝 Completando reporte",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = "${progreso.porcentaje}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = progreso.porcentaje / 100f,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ===== MENSAJE DE BIENVENIDA =====
        if (messages.isEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    MensajeBienvenida(userName = user.displayName?.split(" ")?.first() ?: "Pescador")
                }

                item {
                    Text(
                        text = "💡 Empezá con una de estas opciones:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                item {
                    SugerenciasGrid(
                        sugerencias = sugerenciasActuales,
                        onSugerenciaClick = { sugerencia ->
                            messageText = sugerencia
                            mostrarSugerencias = false
                        }
                    )
                }
            }
        } else {
            // ===== LISTA DE MENSAJES =====
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    MessageBubbleMejorado(message = message)
                }

                if (isAnalyzing) {
                    item { IndicadorAnalizando() }
                }

                if (isTyping) {
                    item { IndicadorEscribiendo() }
                }
            }
        }

        // ===== SUGERENCIAS CONTEXTUALES =====
        if (mostrarSugerencias && messageText.isBlank() && messages.isNotEmpty()) {
            SugerenciasHorizontales(
                sugerencias = sugerenciasActuales.take(3),
                onSugerenciaClick = { sugerencia ->
                    messageText = sugerencia
                    mostrarSugerencias = false
                }
            )
        }

        // ===== INPUT MEJORADO =====
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
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
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
                    onValueChange = {
                        messageText = it
                        mostrarSugerencias = it.isBlank()
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                isTyping -> "Juka está escribiendo..."
                                isAnalyzing -> "Analizando imagen..."
                                else -> "Contame sobre tu jornada de pesca..."
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = !isTyping && !isAnalyzing,
                    trailingIcon = {
                        if (messageText.isNotEmpty()) {
                            IconButton(onClick = { messageText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // TU AudioRecordButton (sin cambios)
                WorkingAudioButton(
                    onAudioTranscribed = { transcript ->
                        viewModel.sendAudioTranscript(transcript)
                    },
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Botón de enviar
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendTextMessage(messageText.trim())
                            messageText = ""
                            mostrarSugerencias = false
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (messageText.isNotBlank() && !isTyping && !isAnalyzing)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    when {
                        isTyping || isAnalyzing -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar texto",
                            tint = if (messageText.isNotBlank())
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ===== COMPONENTES AUXILIARES =====

@Composable
fun MensajeBienvenida(userName: String) {
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
            Text(text = "🎣", fontSize = 48.sp)
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

            Column(horizontalAlignment = Alignment.Start) {
                listOf(
                    "📝 Registrar tus jornadas de pesca",
                    "🐟 Identificar especies argentinas",
                    "🎯 Darte consejos personalizados",
                    "📊 Guardar todo en Firebase automáticamente"
                ).forEach { feature ->
                    Text(
                        text = feature,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SugerenciasGrid(
    sugerencias: List<String>,
    onSugerenciaClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(200.dp)
    ) {
        items(sugerencias) { sugerencia ->
            SugerenciaCard(
                text = sugerencia,
                onClick = { onSugerenciaClick(sugerencia) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SugerenciaCard(
    text: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun SugerenciasHorizontales(
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
                }
            )
        }
    }
}

@Composable
fun MessageBubbleMejorado(message: ChatMessage) {
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

        Column(modifier = Modifier.widthIn(max = 300.dp)) {
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
                Column(modifier = Modifier.padding(12.dp)) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            TextoConFormato(
                                texto = message.content,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MessageType.AUDIO -> {
                            MensajeAudio(content = message.content, isUser = isUser)
                        }
                        MessageType.IMAGE -> {
                            MensajeImagen(imagePath = message.content, isUser = isUser)
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
fun TextoConFormato(texto: String, color: Color) {
    val textoFormateado = remember(texto) {
        buildAnnotatedString {
            val boldRegex = """\*\*(.*?)\*\*""".toRegex()
            val matches = boldRegex.findAll(texto).toList()

            if (matches.isEmpty()) {
                append(texto)
            } else {
                var currentIndex = 0
                matches.forEach { match ->
                    if (match.range.first > currentIndex) {
                        append(texto.substring(currentIndex, match.range.first))
                    }
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groupValues[1])
                    }
                    currentIndex = match.range.last + 1
                }
                if (currentIndex < texto.length) {
                    append(texto.substring(currentIndex))
                }
            }
        }
    }

    Text(
        text = textoFormateado,
        color = color,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
}

@Composable
fun MensajeAudio(content: String, isUser: Boolean) {
    val transcript = content.removePrefix("🎤 \"").removeSuffix("\"")

    Row(verticalAlignment = Alignment.CenterVertically) {
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
fun MensajeImagen(imagePath: String, isUser: Boolean) {
    Column {
        Image(
            painter = rememberAsyncImagePainter(imagePath),
            contentDescription = "Imagen de pesca",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
fun IndicadorEscribiendo() {
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
            shadowElevation = 2.dp
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
fun IndicadorAnalizando() {
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
            shadowElevation = 2.dp
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

// ===== FUNCIÓN AUXILIAR PARA CALCULAR PROGRESO =====
data class ProgresoSimple(val porcentaje: Int, val siguiente: String?)

fun calcularProgresoSimple(messages: List<ChatMessage>): ProgresoSimple {
    val ultimoMensajeUsuario = messages.lastOrNull { it.isFromUser }?.content?.lowercase() ?: ""

    var progreso = 0
    var siguiente: String? = null

    val campos = mapOf(
        "Día" to (ultimoMensajeUsuario.contains("ayer") || ultimoMensajeUsuario.contains("hoy") ||
                ultimoMensajeUsuario.contains("lunes") || ultimoMensajeUsuario.contains("martes") ||
                ultimoMensajeUsuario.contains("miércoles") || ultimoMensajeUsuario.contains("jueves") ||
                ultimoMensajeUsuario.contains("viernes") || ultimoMensajeUsuario.contains("sábado") ||
                ultimoMensajeUsuario.contains("domingo")),
        "Horario" to (ultimoMensajeUsuario.contains(Regex("""\d+:\d+""")) ||
                ultimoMensajeUsuario.contains("mañana") || ultimoMensajeUsuario.contains("tarde")),
        "Cantidad" to (ultimoMensajeUsuario.contains(Regex("""\d+""")) &&
                (ultimoMensajeUsuario.contains("pez") || ultimoMensajeUsuario.contains("dorado"))),
        "Tipo" to (ultimoMensajeUsuario.contains("embarcado") || ultimoMensajeUsuario.contains("costa") ||
                ultimoMensajeUsuario.contains("orilla") || ultimoMensajeUsuario.contains("barco"))
    )

    progreso = campos.values.count { it } * 25 // 25% por cada campo
    siguiente = campos.entries.find { !it.value }?.key

    return ProgresoSimple(progreso, siguiente)
}