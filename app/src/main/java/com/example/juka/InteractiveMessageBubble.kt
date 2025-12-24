// InteractiveMessageBubble.kt  →  VERSIÓN FINAL QUE FUNCIONA CON TU VIEWMODEL ACTUAL
package com.example.juka.ui.components // o el package que uses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.domain.model.ChatMessageWithMode
import com.example.juka.data.ChatOption

// ← ESTE ES EL QUE USÁS VOS

@Composable
fun InteractiveMessageBubble(
    message: ChatMessageWithMode,
    onOptionClick: (ChatOption) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFromUser = message.isFromUser  // ← AHORA SÍ EXISTE

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromUser) Alignment.End else Alignment.Start
    ) {
        // Burbuja del mensaje
        Surface(
            color = if (isFromUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromUser) 16.dp else 4.dp,
                bottomEnd = if (isFromUser) 4.dp else 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    fontSize = 15.5.sp,
                    color = if (isFromUser)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 22.sp
                )

                Text(
                    text = message.timestamp,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

/*        // OPCIONES SOLO SI ES DEL BOT Y TIENE OPTIONS
        if (!isFromUser && !message.options.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            InteractiveOptionsGrid(
                options = message.options,
                onOptionClick = onOptionClick
            )
        }
    }*/
}}