// WorkingAudioButton.kt
package com.example.juka.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.delay
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

/** Tope máximo de grabación. Pasado este límite, autodetenemos y entregamos
 *  el texto acumulado. Evita que el usuario se cuelgue grabando sin darse
 *  cuenta y nos protege de exceder cuotas del SpeechRecognizer. */
private const val MAX_RECORDING_SECONDS = 45

/** Tope de segmentos consecutivos sin voz antes de cortar solos. Evita que el
 *  recognizer se relance infinitamente ante silencio (drena batería). */
private const val MAX_EMPTY_SEGMENTS = 3

/** Delay entre fin de un segmento y arranque del siguiente. Le da al
 *  SpeechRecognizer un instante para liberar recursos antes de relanzarlo.
 *  Sin esto, el destroy()→create()→startListening() encadenado dentro del
 *  callback del recognizer suele fallar con ERROR_RECOGNIZER_BUSY o quedar
 *  en un estado inválido (el síntoma que se ve es "se corta a los 5 seg"). */
private const val RESTART_DELAY_MS = 150L

/**
 * Botón de grabación con SpeechRecognizer.
 *
 * @param compact si true, omite el texto descriptivo de abajo del botón y
 *   usa un FAB del mismo tamaño que el modifier recibido. Pensado para uso
 *   inline en barras de input (chat) donde no hay espacio vertical para el
 *   texto y el padre define el tamaño con `Modifier.size(48.dp)`.
 *
 *   IMPORTANTE: el bug previo del chat general era que se invocaba con
 *   `Modifier.size(48.dp)` desde el padre, pero internamente el FAB era de
 *   64.dp y había Spacer + Text después — todo eso se clipaba a 48.dp y el
 *   área clicable quedaba rota. En modo compact el FAB se ajusta al tamaño
 *   del Box contenedor y el countdown de segundos se muestra ADENTRO del
 *   propio FAB en lugar de texto debajo.
 */
@Composable
fun WorkingAudioButton(
    onAudioTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
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
    // Contador visible de segundos restantes. Se reinicia al arrancar.
    var secondsRemaining by remember { mutableStateOf(MAX_RECORDING_SECONDS) }
    // ✅ V1: sesión activa = desde que arranca hasta que se entrega/corta. El
    // countdown se ata a ESTO, no a cada segmento, así el tope es total real.
    var isSessionActive by remember { mutableStateOf(false) }
    // ✅ V2: segmentos consecutivos sin voz detectada.
    var emptySegments by remember { mutableStateOf(0) }

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.3f else 1.0f,
        animationSpec = tween(200)
    )

    // Handler para postponer el relance fuera del callback del recognizer.
    // Mantenerlo a nivel de composable así no se recrea en cada recomposition.
    val handler = remember { Handler(Looper.getMainLooper()) }

    // Acción única de "detener" — reusada por el botón cuando el usuario lo
    // toca Y por el timer cuando se cumple MAX_RECORDING_SECONDS.
    val stopRecording: () -> Unit = {
        Log.i("🎤", "Deteniendo grabación")
        userStopped = true
        isProcessing = true
        isRecording = false
        isSessionActive = false
        speechRecognizer?.stopListening()
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
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
                    emptySegments = 0
                } else {
                    emptySegments++
                }

                if (userStopped) {
                    // El usuario detuvo: entregar todo el texto acumulado
                    isRecording = false
                    isProcessing = false
                    isSessionActive = false
                    val finalText = accumulatedText.trim()
                    accumulatedText = ""
                    if (finalText.isNotBlank()) {
                        onAudioTranscribed(finalText)
                    } else {
                        errorMessage = "No se detectó texto claro"
                    }
                } else if (emptySegments >= MAX_EMPTY_SEGMENTS && accumulatedText.isBlank()) {
                    // ✅ V2: demasiados segmentos sin voz y nada acumulado → cortar.
                    Log.i("🎤", "Demasiados segmentos vacíos, corto la sesión")
                    userStopped = true
                    isRecording = false
                    isProcessing = false
                    isSessionActive = false
                    errorMessage = "No te escuché. Probá de nuevo."
                } else {
                    // ✅ Continuar grabando automáticamente, pero POSPONIENDO
                    // el relance fuera del callback del recognizer. Esto evita
                    // que destruyamos y recreemos el recognizer mientras él
                    // mismo está procesando su propio callback.
                    Log.i("🎤", "Reiniciando segmento (con delay)...")
                    handler.postDelayed({
                        if (!userStopped) launchRecognizer()
                    }, RESTART_DELAY_MS)
                }
            }

            override fun onError(error: Int) {
                val isRecoverableError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                Log.w("🎤", "Error código: $error, recuperable: $isRecoverableError")

                if (isRecoverableError && !userStopped) {
                    emptySegments++
                    if (emptySegments >= MAX_EMPTY_SEGMENTS && accumulatedText.isBlank()) {
                        // ✅ V2: silencio persistente → cortar en vez de loopear.
                        Log.i("🎤", "Silencio persistente, corto la sesión")
                        userStopped = true
                        isRecording = false
                        isProcessing = false
                        isSessionActive = false
                        errorMessage = "No te escuché. Probá de nuevo."
                    } else {
                        // ✅ Sin voz en este segmento → reiniciar con el mismo
                        // delay que en onResults, por la misma razón.
                        handler.postDelayed({
                            if (!userStopped) launchRecognizer()
                        }, RESTART_DELAY_MS)
                    }
                } else if (userStopped) {
                    // Usuario detuvo durante un error: entregar lo acumulado
                    isRecording = false
                    isProcessing = false
                    isSessionActive = false
                    val finalText = accumulatedText.trim()
                    accumulatedText = ""
                    if (finalText.isNotBlank()) onAudioTranscribed(finalText)
                    else errorMessage = "No se detectó texto claro"
                } else {
                    isRecording = false
                    isProcessing = false
                    isSessionActive = false
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
            emptySegments = 0
            isSessionActive = true
            launchRecognizer()
        } else {
            errorMessage = "Necesitás permisos de micrófono"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            speechRecognizer?.destroy()
        }
    }

    // ✅ V1: Countdown ATADO A LA SESIÓN, no a cada segmento. Antes se reiniciaba
    // cada vez que isRecording pasaba a true (en cada segmento), así que el tope
    // de 15s no era real y la grabación podía correr indefinidamente. Ahora corre
    // una sola vez por sesión.
    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            secondsRemaining = MAX_RECORDING_SECONDS
            while (secondsRemaining > 0 && isSessionActive && !userStopped) {
                delay(1000L)
                if (isSessionActive && !userStopped) secondsRemaining--
            }
            if (secondsRemaining <= 0 && !userStopped) {
                Log.i("🎤", "Timeout total de ${MAX_RECORDING_SECONDS}s, autodetener")
                stopRecording()
            }
        } else {
            // Sesión terminada → resetear el contador para la próxima
            secondsRemaining = MAX_RECORDING_SECONDS
        }
    }

    // Acción del FAB — extraída como lambda para reusarla en los dos modos
    // (compact e inline).
    val onFabClick: () -> Unit = {
        errorMessage = null
        when {
            isRecording || isProcessing -> {
                Log.i("🎤", "Usuario detuvo grabación manualmente")
                stopRecording()
            }
            else -> {
                userStopped = false
                accumulatedText = ""
                emptySegments = 0
                secondsRemaining = MAX_RECORDING_SECONDS
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    isSessionActive = true
                    launchRecognizer()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
    val fabContainerColor = when {
        isProcessing -> MaterialTheme.colorScheme.tertiary
        isRecording -> MaterialTheme.colorScheme.error
        errorMessage != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    if (compact) {
        // Modo compacto: solo el FAB, sin Spacer ni Text descriptivo. El FAB
        // ocupa todo el `modifier` que reciba (típicamente Modifier.size(48.dp)
        // desde la barra del chat). El countdown se renderiza ADENTRO del FAB
        // reemplazando el ícono Stop mientras se está grabando.
        FloatingActionButton(
            onClick = onFabClick,
            modifier = modifier,
            containerColor = fabContainerColor
        ) {
            when {
                isProcessing -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onTertiary,
                    strokeWidth = 2.5.dp
                )
                isRecording -> Text(
                    text = "${secondsRemaining}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError
                )
                errorMessage != null -> Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(22.dp)
                )
                else -> Icon(
                    Icons.Default.Mic,
                    contentDescription = "Grabar audio",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (errorMessage != null) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(4000)
                errorMessage = null
            }
        }
    } else {
        // Modo expandido — FAB de 64dp + texto descriptivo debajo. Para
        // pantallas donde el botón es la acción principal y hay espacio.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            FloatingActionButton(
                onClick = onFabClick,
                modifier = Modifier.size(64.dp).scale(scale),
                containerColor = fabContainerColor
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
                    isRecording -> "🎤 Grabando... ${secondsRemaining}s · Toca para detener"
                    isProcessing -> "⚡ Procesando audio..."
                    errorMessage != null -> "⚠️ $errorMessage"
                    else -> "Toca para grabar (máx ${MAX_RECORDING_SECONDS}s)"
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
}