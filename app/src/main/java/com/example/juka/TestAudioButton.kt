// TestAudioButton.kt - Para debuggear la vista
package com.example.juka

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TestAudioButton(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var clickCount by remember { mutableStateOf(0) }

    // Log inicial para verificar que se renderiza
    LaunchedEffect(Unit) {
        android.util.Log.d("🧪 TEST", "=== TestAudioButton RENDERIZADO ===")
        android.util.Log.d("🧪 TEST", "Context: ${context.javaClass.simpleName}")
        android.util.Log.d("🧪 TEST", "Modifier: $modifier")
    }

    // Log de recomposición
    LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            android.util.Log.d("🧪 TEST", "Recomposición - Clicks: $clickCount")
        }
    }

    // FloatingActionButton simple (como el original pero que funciona)
    FloatingActionButton(
        onClick = {
            clickCount++
            android.util.Log.d("🧪 TEST", "🔥🔥🔥 BOTÓN TOCADO - Click #$clickCount 🔥🔥🔥")
            android.util.Log.d("🧪 TEST", "Ejecutando callback...")

            // Llamar al callback para simular audio
            val testMessage = "Prueba de audio número $clickCount"
            android.util.Log.d("🧪 TEST", "Enviando: '$testMessage'")
            onAudioTranscribed(testMessage)
            android.util.Log.d("🧪 TEST", "Callback ejecutado ✅")
        },
        modifier = modifier,
        containerColor = when (clickCount % 4) {
            0 -> MaterialTheme.colorScheme.secondary
            1 -> MaterialTheme.colorScheme.primary
            2 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        }
    ) {
        when (clickCount % 3) {
            0 -> Icon(
                Icons.Default.Mic,
                contentDescription = "Test Audio",
                modifier = Modifier.size(24.dp)
            )
            1 -> Text(
                text = "$clickCount",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            else -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Funcionando",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Versión extendida para debugging completo (usa cuando tengas más espacio)
@Composable
fun TestAudioButtonExtended(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var clickCount by remember { mutableStateOf(0) }
    var lastMessage by remember { mutableStateOf("Esperando...") }

    // Log inicial para verificar que se renderiza
    LaunchedEffect(Unit) {
        android.util.Log.d("🧪 TEST", "=== TestAudioButtonExtended RENDERIZADO ===")
        android.util.Log.d("🧪 TEST", "Context: ${context.javaClass.simpleName}")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(16.dp)
    ) {
        // Log de recomposición
        LaunchedEffect(clickCount) {
            android.util.Log.d("🧪 TEST", "Recomposición - Clicks: $clickCount")
        }

        // Botón de prueba simple
        Button(
            onClick = {
                clickCount++
                lastMessage = "¡Botón tocado $clickCount veces!"
                android.util.Log.d("🧪 TEST", "🔥 BOTÓN SIMPLE TOCADO - Click #$clickCount")
                android.util.Log.d("🧪 TEST", "🔥 onAudioTranscribed será llamado...")

                // Llamar al callback para simular audio
                onAudioTranscribed("Prueba de audio #$clickCount")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("TEST BUTTON - Clicks: $clickCount")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FloatingActionButton (como el original)
        FloatingActionButton(
            onClick = {
                clickCount++
                lastMessage = "¡FAB tocado $clickCount veces!"
                android.util.Log.d("🧪 TEST", "🔥 FAB TOCADO - Click #$clickCount")
                onAudioTranscribed("Prueba FAB #$clickCount")
            },
            containerColor = MaterialTheme.colorScheme.secondary
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Test FAB",
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Estado visual
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🧪 DEBUG INFO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Clicks: $clickCount",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = lastMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (clickCount > 0) "✅ UI RESPONDE" else "⏳ Esperando click...",
                    fontSize = 12.sp,
                    color = if (clickCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // El botón original para comparar
        AudioButtonSimple(
            onAudioTranscribed = { result ->
                android.util.Log.d("🧪 TEST", "🎤 AudioButtonSimple callback: '$result'")
                onAudioTranscribed("AudioSimple: $result")
                lastMessage = "AudioSimple funcionó: $result"
            },
            modifier = Modifier.wrapContentSize()
        )
    }
}