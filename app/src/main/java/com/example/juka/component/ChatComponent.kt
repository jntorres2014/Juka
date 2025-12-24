// ChatComponents.kt - Componentes reutilizables para el chat
package com.example.juka.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.juka.domain.model.ChatMode
import com.example.juka.data.ActionType
import com.example.juka.data.ChatOption
import com.example.juka.domain.model.ChatMessageWithMode
import com.example.juka.domain.model.IMessage
import com.example.juka.viewmodel.MessageType
import kotlinx.coroutines.delay


@Composable
fun MessageBubble(
    message: IMessage,
    currentMode: ChatMode = ChatMode.GENERAL,
    onOptionClick: (ChatOption) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.isFromUser

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // Avatar del bot
        if (!isUser) {
            BotAvatar(currentMode = currentMode)
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Contenido del mensaje
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
                color = getBubbleColor(isUser, currentMode),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            FormattedText(
                                text = message.content,
                                color = getTextColor(isUser),
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

            // ← AGREGAR AQUÍ LOS BOTONES INTERACTIVOS
            // Mostrar botones si es un mensaje del bot con opciones
            if (!isUser && message is ChatMessageWithMode && message.options != null) {
                Spacer(modifier = Modifier.height(8.dp))
                InteractiveOptionsGrid(
                    options = message.options!!,
                    onOptionClick = onOptionClick,
                    //modifier = Modifier.widthIn(max = 320.dp)
                )
            }

            // Timestamp
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.timestamp,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Indicador de ML Kit si es modo crear parte
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

        // Avatar del usuario
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(currentMode = currentMode)
        }
    }
}
// ==================== AVATARS ====================

@Composable
private fun BotAvatar(currentMode: ChatMode) {
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
}

@Composable
private fun UserAvatar(currentMode: ChatMode) {
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

// ==================== TEXTO FORMATEADO ====================

/**
 * Texto con soporte para markdown simple (**negrita**)
 */
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

// ==================== AUDIO MESSAGE ====================

@Composable
fun AudioMessageContent(
    content: String,
    isUser: Boolean,
    currentMode: ChatMode = ChatMode.GENERAL
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

// ==================== IMAGE MESSAGE ====================

@Composable
fun ImageMessageContent(
    imagePath: String,
    isUser: Boolean
) {
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

// ==================== TYPING INDICATOR ====================

/**
 * Indicador animado de "escribiendo..."
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                // Animación de puntos
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
                    "Huka está escribiendo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ==================== ANALYZING INDICATOR ====================

/**
 * Indicador de análisis con ML Kit
 */
@Composable
fun AnalyzingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                    "Analizando con IA...",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ==================== WELCOME MESSAGE ====================

/**
 * Mensaje de bienvenida inicial
 */
@Composable
fun WelcomeMessage(
    userName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                text = "Soy Huka, tu asistente de pesca inteligente",
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

// ==================== HELPERS ====================

@Composable
private fun getBubbleColor(isUser: Boolean, currentMode: ChatMode): Color {
    return if (isUser) {
        when (currentMode) {
            ChatMode.GENERAL -> MaterialTheme.colorScheme.primary
            ChatMode.CREAR_PARTE -> MaterialTheme.colorScheme.tertiary
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun getTextColor(isUser: Boolean): Color {
    return if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}
@Composable
fun InteractiveOptionsGrid(
    options: List<ChatOption>,
    onOptionClick: (ChatOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilledTonalButton(
                onClick = { onOptionClick(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = when (option.action) {
                        ActionType.START_PARTE -> MaterialTheme.colorScheme.tertiaryContainer
                        ActionType.DOWNLOAD -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    }
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    option.icon?.let {
                        Text(it, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                    }

                    Text(
                        text = option.label,
                        fontWeight = if (option.action == ActionType.START_PARTE) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp
                    )

                    Spacer(Modifier.weight(1f))

                    if (option.action == ActionType.EXTERNAL_LINK) {
                        Icon(
                            Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }}