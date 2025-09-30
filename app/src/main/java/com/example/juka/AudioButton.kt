// WorkingAudioButton.kt - VERSIÓN QUE SÍ FUNCIONA
package com.example.juka

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
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    android.util.Log.d("🎤 WorkingAudio", "Renderizando WorkingAudioButton")

    // Animación
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.3f else 1.0f,
        animationSpec = tween(200)
    )

    // Launcher para permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("🎤 WorkingAudio", "Permisos: $isGranted")
        if (isGranted) {
            startRecording(
                context = context,
                onRecording = {
                    android.util.Log.d("🎤 WorkingAudio", "🎤 GRABANDO")
                    isRecording = true
                },
                onProcessing = {
                    android.util.Log.d("🎤 WorkingAudio", "⚡ PROCESANDO")
                    isRecording = false
                    isProcessing = true
                },
                onResult = { result ->
                    android.util.Log.d("🎤 WorkingAudio", "✅ RESULTADO: '$result'")
                    isProcessing = false
                    errorMessage = null
                    onAudioTranscribed(result)
                },
                onError = { error ->
                    android.util.Log.e("🎤 WorkingAudio", "❌ ERROR: $error")
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

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("🎤 WorkingAudio", "🧹 Cleanup SpeechRecognizer")
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
                android.util.Log.d("🎤 WorkingAudio", "👆 BOTÓN TOCADO")
                android.util.Log.d("🎤 WorkingAudio", "Estado: Recording=$isRecording, Processing=$isProcessing")

                // Reset error
                errorMessage = null

                when {
                    isRecording -> {
                        android.util.Log.d("🎤 WorkingAudio", "🛑 Deteniendo grabación...")
                        speechRecognizer?.stopListening()
                        isRecording = false
                        isProcessing = true
                    }
                    isProcessing -> {
                        android.util.Log.d("🎤 WorkingAudio", "⏳ Procesando... no hacer nada")
                        // No hacer nada
                    }
                    else -> {
                        android.util.Log.d("🎤 WorkingAudio", "🚀 Iniciando grabación...")
                        if (hasAudioPermission(context)) {
                            startRecording(
                                context = context,
                                onRecording = {
                                    android.util.Log.d("🎤 WorkingAudio", "🎤 GRABANDO (directo)")
                                    isRecording = true
                                },
                                onProcessing = {
                                    android.util.Log.d("🎤 WorkingAudio", "⚡ PROCESANDO (directo)")
                                    isRecording = false
                                    isProcessing = true
                                },
                                onResult = { result ->
                                    android.util.Log.d("🎤 WorkingAudio", "✅ RESULTADO (directo): '$result'")
                                    isProcessing = false
                                    errorMessage = null
                                    onAudioTranscribed(result)
                                },
                                onError = { error ->
                                    android.util.Log.e("🎤 WorkingAudio", "❌ ERROR (directo): $error")
                                    isRecording = false
                                    isProcessing = false
                                    errorMessage = error
                                },
                                speechRecognizer = speechRecognizer,
                                onSpeechRecognizerSet = { speechRecognizer = it }
                            )
                        } else {
                            android.util.Log.w("🎤 WorkingAudio", "🔒 Solicitando permisos...")
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
                kotlinx.coroutines.delay(4000)
                errorMessage = null
            }
        }
    }
}

// Verificar permisos
private fun hasAudioPermission(context: Context): Boolean {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    android.util.Log.d("🎤 WorkingAudio", "🔒 Permisos: $hasPermission")
    return hasPermission
}

// Función simplificada y robusta para grabación
private fun startRecording(
    context: Context,
    onRecording: () -> Unit,
    onProcessing: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    speechRecognizer: SpeechRecognizer?,
    onSpeechRecognizerSet: (SpeechRecognizer) -> Unit
) {
    android.util.Log.d("🎤 WorkingAudio", "🚀 === INICIANDO GRABACIÓN ===")

    try {
        // Verificar disponibilidad PRIMERO
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            android.util.Log.e("🎤 WorkingAudio", "❌ SpeechRecognizer NO disponible")
            onError("Reconocimiento de voz no disponible en este dispositivo")
            return
        }
        android.util.Log.d("🎤 WorkingAudio", "✅ SpeechRecognizer disponible")

        // Destruir recognizer anterior si existe
        speechRecognizer?.destroy()

        // Crear nuevo SpeechRecognizer
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            android.util.Log.e("🎤 WorkingAudio", "❌ Error creando SpeechRecognizer: ${e.message}")
            onError("Error creando reconocedor de voz")
            return
        }

        if (recognizer == null) {
            android.util.Log.e("🎤 WorkingAudio", "❌ SpeechRecognizer es null")
            onError("No se pudo crear el reconocedor")
            return
        }

        onSpeechRecognizerSet(recognizer)
        android.util.Log.d("🎤 WorkingAudio", "✅ SpeechRecognizer creado")

        // Intent configurado para Argentina
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Timeouts más largos para mejor captura
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 200000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }
        android.util.Log.d("🎤 WorkingAudio", "✅ Intent configurado")

        // Listener simplificado y robusto
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.i("🎤 WorkingAudio", "🎙️ LISTO - Micrófono activo")
                onRecording()
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.i("🎤 WorkingAudio", "🗣️ VOZ DETECTADA")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Log de volumen solo cada segundo para no saturar
                if (System.currentTimeMillis() % 1000 < 50) {
                    android.util.Log.v("🎤 WorkingAudio", "🔊 Volumen: ${rmsdB.toInt()}dB")
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                android.util.Log.v("🎤 WorkingAudio", "📡 Buffer: ${buffer?.size} bytes")
            }

            override fun onEndOfSpeech() {
                android.util.Log.i("🎤 WorkingAudio", "🔚 FIN DE VOZ - Procesando...")
                onProcessing()
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio - ¿Está conectado el micrófono?"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente - Intenta de nuevo"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
                    SpeechRecognizer.ERROR_NETWORK -> "Sin conexión a internet"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red - Verifica tu conexión"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó voz clara - Intenta hablar más fuerte"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado - Espera un momento"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor de Google"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz - Intenta de nuevo"
                    else -> "Error desconocido ($error)"
                }
                android.util.Log.e("🎤 WorkingAudio", "💥 ERROR: $errorMsg (código: $error)")
                onError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                android.util.Log.i("🎤 WorkingAudio", "🏆 === RESULTADOS ===")

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                android.util.Log.d("🎤 WorkingAudio", "📝 Total resultados: ${matches?.size}")

                matches?.forEachIndexed { index, match ->
                    android.util.Log.d("🎤 WorkingAudio", "  $index: '$match'")
                }

                val bestResult = matches?.firstOrNull()?.trim() ?: ""

                android.util.Log.i("🎤 WorkingAudio", "🎯 MEJOR RESULTADO: '$bestResult'")

                if (bestResult.isNotBlank()) {
                    android.util.Log.i("🎤 WorkingAudio", "✅ ÉXITO - Enviando resultado")
                    onResult(bestResult)
                } else {
                    android.util.Log.w("🎤 WorkingAudio", "⚠️ Resultado vacío")
                    onError("No se detectó texto claro")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                android.util.Log.d("🎤 WorkingAudio", "🔄 Parcial: '${partial?.firstOrNull() ?: ""}'")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                android.util.Log.v("🎤 WorkingAudio", "🎭 Evento: $eventType")
            }
        })

        // INICIAR reconocimiento
        android.util.Log.i("🎤 WorkingAudio", "🎬 INICIANDO reconocimiento...")
        recognizer.startListening(intent)
        android.util.Log.i("🎤 WorkingAudio", "🚀 ¡RECONOCIMIENTO ACTIVO!")


    } catch (e: Exception) {
        android.util.Log.e("🎤 WorkingAudio", "💥 Excepción: ${e.message}", e)
        onError("Error iniciando grabación: ${e.localizedMessage}")
    }
}