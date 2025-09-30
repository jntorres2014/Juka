// AudioTranscriptionButton.kt
package com.example.juka.common.composables

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Un botón de grabación de audio que transcribe la voz a texto.
 *
 * Este componente gestiona los permisos del micrófono, los estados de grabación/procesamiento,
 * y muestra feedback visual al usuario.
 *
 * @param onTranscriptionResult Callback que se ejecuta cuando se obtiene una transcripción exitosa.
 * @param modifier Modificador de Compose.
 */
@Composable
fun AudioTranscriptionButton(
    onTranscriptionResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Animación de escala para el botón
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1.0f,
        animationSpec = tween(200)
    )

    // Launcher para solicitar permisos de micrófono
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording(
                context = context,
                onRecording = { isRecording = true },
                onProcessing = {
                    isRecording = false
                    isProcessing = true
                },
                onResult = { result ->
                    isProcessing = false
                    errorMessage = null
                    onTranscriptionResult(result)
                },
                onError = { error ->
                    isRecording = false
                    isProcessing = false
                    errorMessage = error
                },
                speechRecognizer = speechRecognizer,
                onSpeechRecognizerSet = { speechRecognizer = it }
            )
        } else {
            errorMessage = "El permiso para usar el micrófono es necesario."
        }
    }

    // Limpieza del SpeechRecognizer al salir del Composable
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Botón flotante principal
        FloatingActionButton(
            onClick = {
                errorMessage = null // Limpiar errores previos

                when {
                    isRecording -> {
                        speechRecognizer?.stopListening()
                    }
                    isProcessing -> {
                        // No hacer nada, ya está trabajando
                    }
                    else -> {
                        if (hasAudioPermission(context)) {
                            startRecording(
                                context = context,
                                onRecording = { isRecording = true },
                                onProcessing = {
                                    isRecording = false
                                    isProcessing = true
                                },
                                onResult = { result ->
                                    isProcessing = false
                                    errorMessage = null
                                    onTranscriptionResult(result)
                                },
                                onError = { error ->
                                    isRecording = false
                                    isProcessing = false
                                    errorMessage = error
                                },
                                speechRecognizer = speechRecognizer,
                                onSpeechRecognizerSet = { speechRecognizer = it }
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            },
            modifier = Modifier
                .size(64.dp)
                .scale(scale),
            containerColor = when {
                isProcessing -> MaterialTheme.colorScheme.tertiary
                isRecording -> MaterialTheme.colorScheme.error
                errorMessage != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 3.dp
                    )
                }
                isRecording -> {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Detener grabación",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
                errorMessage != null -> {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(28.dp)
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Grabar audio",
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Texto de estado
        Text(
            text = when {
                isRecording -> "Grabando... Toca para detener"
                isProcessing -> "Procesando audio..."
                errorMessage != null -> "⚠️ $errorMessage"
                else -> "Toca para grabar"
            },
            fontSize = 12.sp,
            color = when {
                errorMessage != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            },
            fontWeight = if (errorMessage != null) FontWeight.Bold else FontWeight.Normal
        )

        // Limpieza automática de mensajes de error
        if (errorMessage != null) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(4000)
                errorMessage = null
            }
        }
    }
}

/**
 * Verifica si la app tiene permiso para grabar audio.
 */
private fun hasAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Inicia el proceso de reconocimiento de voz.
 *
 * Crea o reutiliza un [SpeechRecognizer], configura los callbacks y comienza a escuchar.
 */
private fun startRecording(
    context: Context,
    onRecording: () -> Unit,
    onProcessing: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    speechRecognizer: SpeechRecognizer?,
    onSpeechRecognizerSet: (SpeechRecognizer) -> Unit
) {
    try {
        // Asegurarse de que el reconocimiento está disponible
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconocimiento de voz no disponible")
            return
        }

        // Destruir el reconocedor anterior si existe para evitar conflictos
        speechRecognizer?.destroy()

        // Crear una nueva instancia del reconocedor
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            onSpeechRecognizerSet(it)
        }

        // Configurar el Intent para el reconocimiento de voz
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR") // Español (Argentina)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            // Timeouts para la entrada de voz
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }

        // Configurar el listener para los eventos de reconocimiento
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onRecording()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onProcessing()
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió lo que dijiste"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El servicio está ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                    else -> "Error desconocido"
                }
                onError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onError("No se pudo transcribir el audio.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Iniciar la escucha
        recognizer.startListening(intent)

    } catch (e: Exception) {
        onError("Ocurrió un error: ${e.localizedMessage}")
    }
}
