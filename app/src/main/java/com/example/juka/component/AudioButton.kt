// WorkingAudioButton.kt
package com.example.juka.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun WorkingAudioButton(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // ✅ NUEVO: acumulador de texto entre sesiones
    var accumulatedText by remember { mutableStateOf("") }
    // ✅ Flag para saber si el usuario detuvo manualmente
    var userStopped by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.3f else 1.0f,
        animationSpec = tween(200)
    )

    // ✅ Función de inicio extraída como lambda para poder llamarla recursivamente
    val startContinuousRecording = remember<() -> Unit> {
        {
            // se define abajo en el LaunchedEffect / función local
        }
    }

    // Función local que relanza el recognizer acumulando texto
    fun launchRecognizer() {
        if (userStopped) return   // El usuario ya detuvo, no reiniciar

        speechRecognizer?.destroy()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            isRecording = false
            isProcessing = false
            errorMessage = "Reconocimiento de voz no disponible"
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context) ?: run {
            isRecording = false
            errorMessage = "No se pudo crear el reconocedor"
            return
        }
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i("🎤", "Micrófono activo")
                isRecording = true
                isProcessing = false
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.i("🎤", "Fin de voz, procesando segmento...")
                isProcessing = true
            }

            override fun onResults(results: Bundle?) {
                val match = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    ?: ""

                Log.i("🎤", "Segmento: '$match'")

                if (match.isNotBlank()) {
                    // ✅ Acumular texto con espacio entre segmentos
                    accumulatedText = if (accumulatedText.isBlank()) match
                    else "$accumulatedText $match"
                }

                if (userStopped) {
                    // El usuario detuvo: entregar todo el texto acumulado
                    isRecording = false
                    isProcessing = false
                    val finalText = accumulatedText.trim()
                    accumulatedText = ""
                    if (finalText.isNotBlank()) {
                        onAudioTranscribed(finalText)
                    } else {
                        errorMessage = "No se detectó texto claro"
                    }
                } else {
                    // ✅ Continuar grabando automáticamente
                    Log.i("🎤", "Reiniciando segmento...")
                    launchRecognizer()
                }
            }

            override fun onError(error: Int) {
                val isRecoverableError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                Log.w("🎤", "Error código: $error, recuperable: $isRecoverableError")

                if (isRecoverableError && !userStopped) {
                    // ✅ Sin voz en este segmento → simplemente reiniciar
                    launchRecognizer()
                } else if (userStopped) {
                    // Usuario detuvo durante un error: entregar lo acumulado
                    isRecording = false
                    isProcessing = false
                    val finalText = accumulatedText.trim()
                    accumulatedText = ""
                    if (finalText.isNotBlank()) onAudioTranscribed(finalText)
                    else errorMessage = "No se detectó texto claro"
                } else {
                    isRecording = false
                    isProcessing = false
                    accumulatedText = ""
                    errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión a internet"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado, esperá un momento"
                        SpeechRecognizer.ERROR_SERVER -> "Error del servidor de Google"
                        else -> "Error desconocido ($error)"
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        Log.i("🎤", "Segmento de reconocimiento iniciado")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            userStopped = false
            accumulatedText = ""
            launchRecognizer()
        } else {
            errorMessage = "Necesitás permisos de micrófono"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = {
                errorMessage = null
                when {
                    isRecording || isProcessing -> {
                        // ✅ Usuario detiene: marcar flag y esperar onResults/onError
                        Log.i("🎤", "Usuario detuvo grabación")
                        userStopped = true
                        isProcessing = true
                        isRecording = false
                        speechRecognizer?.stopListening()
                    }
                    else -> {
                        userStopped = false
                        accumulatedText = ""
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchRecognizer()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            },
            modifier = Modifier.size(64.dp).scale(scale),
            containerColor = when {
                isProcessing -> MaterialTheme.colorScheme.tertiary
                isRecording -> MaterialTheme.colorScheme.error
                errorMessage != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
        ) {
            when {
                isProcessing -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.onTertiary,
                    strokeWidth = 3.dp
                )
                isRecording -> Icon(
                    Icons.Default.Stop,
                    contentDescription = "Detener grabación",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(32.dp)
                )
                errorMessage != null -> Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(28.dp)
                )
                else -> Icon(
                    Icons.Default.Mic,
                    contentDescription = "Grabar audio",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isRecording -> "🎤 Grabando... Toca para detener"
                isProcessing -> "⚡ Procesando audio..."
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

        if (errorMessage != null) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(4000)
                errorMessage = null
            }
        }
    }
}