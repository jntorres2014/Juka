// AudioButtonSimple.kt - VERSIÓN SIMPLIFICADA QUE FUNCIONA
package com.example.juka

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioButtonSimple(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Animación
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1.0f,
        animationSpec = tween(150)
    )

    // Launcher para permisos
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
                    onAudioTranscribed(result)
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
            errorMessage = "Necesitas permisos de micrófono"
        }
    }

    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Botón principal
        FloatingActionButton(
            onClick = {
                errorMessage = null
                when {
                    isRecording -> {
                        // Detener grabación
                        speechRecognizer?.stopListening()
                    }
                    isProcessing -> {
                        // No hacer nada si está procesando
                    }
                    else -> {
                        // Iniciar grabación
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
                                    onAudioTranscribed(result)
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
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 3.dp
                    )
                }
                isRecording -> {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Detener grabación",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(28.dp)
                    )
                }
                errorMessage != null -> {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Grabar audio",
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estado
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

        // Auto-limpiar errores
        if (errorMessage != null) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(3000)
                errorMessage = null
            }
        }
    }
}

// Función para verificar permisos
private fun hasAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

// Función para iniciar grabación
private fun startRecording(
    context: Context,
    onRecording: () -> Unit,
    onProcessing: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    speechRecognizer: SpeechRecognizer?,
    onSpeechRecognizerSet: (SpeechRecognizer) -> Unit
) {
    android.util.Log.d("🎤 AudioButton", "🚀 === INICIANDO GRABACIÓN ===")

    try {
        android.util.Log.d("🎤 AudioButton", "🔍 Verificando disponibilidad...")

        // Verificar disponibilidad
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            android.util.Log.e("🎤 AudioButton", "❌ SpeechRecognizer NO DISPONIBLE")
            onError("Reconocimiento de voz no disponible")
            return
        } else {
            android.util.Log.d("🎤 AudioButton", "✅ SpeechRecognizer DISPONIBLE")
        }

        // Crear SpeechRecognizer si no existe
        val recognizer = speechRecognizer ?: run {
            android.util.Log.d("🎤 AudioButton", "🔧 Creando nuevo SpeechRecognizer...")
            SpeechRecognizer.createSpeechRecognizer(context).also {
                onSpeechRecognizerSet(it)
                android.util.Log.d("🎤 AudioButton", "✅ SpeechRecognizer creado")
            }
        }

        if (recognizer == null) {
            android.util.Log.e("🎤 AudioButton", "❌ No se pudo crear SpeechRecognizer")
            onError("Error creando reconocedor")
            return
        }

        // Intent de reconocimiento
        android.util.Log.d("🎤 AudioButton", "📋 Creando Intent...")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        android.util.Log.d("🎤 AudioButton", "✅ Intent configurado")

        // Listener
        android.util.Log.d("🎤 AudioButton", "👂 Configurando listener...")
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.i("🎤 AudioButton", "🎙️ READY FOR SPEECH - Micrófono activado")
                onRecording()
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.i("🎤 AudioButton", "🗣️ BEGINNING OF SPEECH - Voz detectada")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Log de nivel de volumen cada 10 calls para no saturar
                if (System.currentTimeMillis() % 1000 < 100) {
                    android.util.Log.v("🎤 AudioButton", "🔊 Volumen: ${rmsdB}dB")
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                android.util.Log.v("🎤 AudioButton", "📡 Buffer recibido: ${buffer?.size} bytes")
            }

            override fun onEndOfSpeech() {
                android.util.Log.i("🎤 AudioButton", "🔚 END OF SPEECH - Procesando...")
                onProcessing()
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio - ¿Funciona el micrófono?"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
                    SpeechRecognizer.ERROR_NETWORK -> "Sin internet"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó voz clara"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor Google"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz (timeout)"
                    else -> "Error desconocido ($error)"
                }
                android.util.Log.e("🎤 AudioButton", "💥 ERROR: $errorMsg (código: $error)")
                onError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                android.util.Log.i("🎤 AudioButton", "🏆 === RESULTADOS RECIBIDOS ===")

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                android.util.Log.d("🎤 AudioButton", "📝 Matches totales: ${matches?.size}")

                matches?.forEachIndexed { index, match ->
                    android.util.Log.d("🎤 AudioButton", "📝 Resultado $index: '$match'")
                }

                val text = matches?.firstOrNull()?.trim() ?: ""

                android.util.Log.i("🎤 AudioButton", "🎯 TEXTO FINAL: '$text' (longitud: ${text.length})")

                if (text.isNotBlank()) {
                    android.util.Log.i("🎤 AudioButton", "✅ ÉXITO - Enviando resultado")
                    onResult(text)
                } else {
                    android.util.Log.w("🎤 AudioButton", "⚠️ Texto vacío")
                    onError("No se detectó texto")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                android.util.Log.d("🎤 AudioButton", "🔄 Resultado parcial: '${partial?.firstOrNull()}'")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                android.util.Log.v("🎤 AudioButton", "🎭 Evento: $eventType")
            }
        })

        // Iniciar reconocimiento
        android.util.Log.i("🎤 AudioButton", "🎬 INICIANDO RECONOCIMIENTO...")
        recognizer.startListening(intent)
        android.util.Log.i("🎤 AudioButton", "🚀 ¡Reconocimiento INICIADO!")

    } catch (e: SecurityException) {
        android.util.Log.e("🎤 AudioButton", "🔒 SecurityException: ${e.message}", e)
        onError("Sin permisos de micrófono")
    } catch (e: Exception) {
        android.util.Log.e("🎤 AudioButton", "💥 Excepción general: ${e.message}", e)
        onError("Error iniciando: ${e.localizedMessage}")
    }
}