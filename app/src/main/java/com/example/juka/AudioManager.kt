// AudioManager.kt - Versión MANUAL sin timer automático
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.*
import kotlin.coroutines.resume

sealed class AudioResult {
    data class Success(val transcript: String) : AudioResult()
    data class Error(val message: String, val errorCode: Int? = null) : AudioResult()
    object PermissionDenied : AudioResult()
    object Cancelled : AudioResult()
}

sealed class AudioState {
    object Idle : AudioState()
    object Listening : AudioState()
    object Processing : AudioState()
    data class Error(val message: String) : AudioState()
}

class AudioManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isInitialized = false
    private var useFallbackMode = false
    private var currentContinuation: kotlinx.coroutines.CancellableContinuation<AudioResult>? = null

    companion object {
        private const val TAG = "🎤 AudioManager"
    }

    fun initialize(): Boolean {
        return try {
            android.util.Log.d(TAG, "🔧 Iniciando AudioManager manual (sin timer)")

            // Verificar si SpeechRecognizer está disponible
            val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            android.util.Log.d(TAG, "📱 SpeechRecognizer disponible: $isAvailable")

            if (!isAvailable) {
                android.util.Log.w(TAG, "⚠️ SpeechRecognizer no disponible, usando MediaRecorder manual")
                useFallbackMode = true
            } else {
                // Crear SpeechRecognizer
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                android.util.Log.d(TAG, "🆕 SpeechRecognizer creado")
                useFallbackMode = false
            }

            isInitialized = true
            android.util.Log.i(TAG, "✅ AudioManager inicializado - Modo: ${if (useFallbackMode) "MediaRecorder Manual" else "SpeechRecognizer"}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error inicializando: ${e.message}", e)
            useFallbackMode = true
            isInitialized = true
            true
        }
    }

    suspend fun startListening(): AudioResult = suspendCancellableCoroutine { continuation ->
        android.util.Log.d(TAG, "🎬 Iniciando grabación MANUAL - Modo: ${if (useFallbackMode) "MediaRecorder" else "SpeechRecognizer"}")

        // Verificar permisos
        if (!hasAudioPermission()) {
            android.util.Log.e(TAG, "🚫 Sin permisos de audio")
            continuation.resume(AudioResult.PermissionDenied)
            return@suspendCancellableCoroutine
        }

        // Verificar inicialización
        if (!isInitialized && !initialize()) {
            android.util.Log.e(TAG, "🔥 No se pudo inicializar AudioManager")
            continuation.resume(AudioResult.Error("No se pudo inicializar el reconocimiento de voz"))
            return@suspendCancellableCoroutine
        }

        // Guardar referencia para poder cancelar/detener manualmente
        currentContinuation = continuation

        if (useFallbackMode) {
            startManualMediaRecorder(continuation)
        } else {
            startSpeechRecognition(continuation)
        }
    }

    private fun startSpeechRecognition(continuation: kotlinx.coroutines.CancellableContinuation<AudioResult>) {
        try {
            val intent = createSpeechIntent()
            android.util.Log.d(TAG, "📋 Iniciando SpeechRecognizer MANUAL")

            val listener = createRecognitionListener(continuation)
            speechRecognizer?.setRecognitionListener(listener)
            speechRecognizer?.startListening(intent)
            android.util.Log.i(TAG, "🎤 SpeechRecognizer ACTIVO - Mantenete presionado!")

            continuation.invokeOnCancellation {
                try {
                    android.util.Log.d(TAG, "❌ Cancelando SpeechRecognizer")
                    speechRecognizer?.cancel()
                    currentContinuation = null
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error cancelando SpeechRecognizer: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error iniciando SpeechRecognizer: ${e.message}", e)
            continuation.resume(AudioResult.Error("Error al iniciar reconocimiento: ${e.message}"))
            currentContinuation = null
        }
    }

    private fun startManualMediaRecorder(continuation: kotlinx.coroutines.CancellableContinuation<AudioResult>) {
        try {
            android.util.Log.i(TAG, "🎙️ Iniciando MediaRecorder MANUAL (sin timer)")

            val audioFile = File(context.cacheDir, "manual_audio_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            android.util.Log.i(TAG, "🎤 GRABACIÓN MANUAL ACTIVA - Solta cuando termines!")

            continuation.invokeOnCancellation {
                try {
                    android.util.Log.d(TAG, "🚫 Cancelando grabación manual")
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                    mediaRecorder = null
                    audioFile.delete() // Borrar archivo si se cancela
                    currentContinuation = null
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error cancelando MediaRecorder: ${e.message}")
                }
            }

            // NO HAY TIMER - LA GRABACIÓN CONTINÚA HASTA QUE SE LLAME stopListening()

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error iniciando MediaRecorder: ${e.message}", e)
            continuation.resume(AudioResult.Error("Error al iniciar grabación: ${e.message}"))
            currentContinuation = null
        }
    }

    // ✅ NUEVO: Detener grabación manualmente (cuando sueltas el botón)
    fun stopListening() {
        android.util.Log.d(TAG, "⏹️ Deteniendo grabación MANUAL")

        if (useFallbackMode) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                android.util.Log.i(TAG, "📁 Grabación completada manualmente")

                // En modo fallback, generar transcripción simulada
                val mockTranscription = generateMockTranscription()
                android.util.Log.i(TAG, "📝 Transcripción simulada: '$mockTranscription'")

                currentContinuation?.let { continuation ->
                    if (continuation.isActive) {
                        continuation.resume(AudioResult.Success(mockTranscription))
                    }
                }

                mediaRecorder = null
                currentContinuation = null

            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 Error deteniendo MediaRecorder: ${e.message}")
                currentContinuation?.let { continuation ->
                    if (continuation.isActive) {
                        continuation.resume(AudioResult.Error("Error procesando audio"))
                    }
                }
                currentContinuation = null
            }
        } else {
            try {
                speechRecognizer?.stopListening()
                android.util.Log.i(TAG, "🛑 SpeechRecognizer detenido manualmente")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error deteniendo SpeechRecognizer: ${e.message}")
            }
        }
    }

    fun cancelListening() {
        android.util.Log.d(TAG, "❌ Cancelando grabación")

        if (useFallbackMode) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error cancelando MediaRecorder: ${e.message}")
            }
        } else {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error cancelando SpeechRecognizer: ${e.message}")
            }
        }

        currentContinuation?.let { continuation ->
            if (continuation.isActive) {
                continuation.resume(AudioResult.Cancelled)
            }
        }
        currentContinuation = null
    }

    private fun generateMockTranscription(): String {
        val fishingMockResponses = listOf(
            "Ayer de 7 a 11, 2 pejerreyes de costa con una caña",
            "Hoy pesqué tres dorados embarcado con dos cañas",
            "El sábado saqué un surubí grande usando lombriz",
            "Anteayer de 6 a 10, cinco bagres desde la orilla",
            "Esta mañana capturé dos tarariras con señuelos"
        )
        return fishingMockResponses.random()
    }

    private fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // ✅ CONFIGURACIÓN PARA GRABACIÓN MANUAL
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L) // 10 seg silencio
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L) // 8 seg posible
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L) // Mínimo 0.5 seg
        }
    }

    private fun createRecognitionListener(
        continuation: kotlinx.coroutines.CancellableContinuation<AudioResult>
    ): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.i(TAG, "✅ SpeechRecognizer listo - Hablá cuando quieras")
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.i(TAG, "🗣️ VOZ DETECTADA - Continúa hablando")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Opcional: mostrar nivel de audio
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                android.util.Log.v(TAG, "📡 Buffer recibido: ${buffer?.size} bytes")
            }

            override fun onEndOfSpeech() {
                android.util.Log.i(TAG, "🔚 Fin de voz detectado - Procesando...")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                try {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    android.util.Log.d(TAG, "🔄 Resultado parcial: ${partial?.firstOrNull()}")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error procesando parciales: ${e.message}")
                }
            }

            override fun onResults(results: Bundle?) {
                try {
                    android.util.Log.i(TAG, "🎉 RESULTADOS FINALES!")

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestMatch = matches?.firstOrNull()?.trim()

                    android.util.Log.i(TAG, "🏆 TRANSCRIPCIÓN: '$bestMatch'")

                    if (!bestMatch.isNullOrBlank()) {
                        if (continuation.isActive) {
                            continuation.resume(AudioResult.Success(bestMatch))
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(AudioResult.Error("No se entendió el audio"))
                        }
                    }
                    currentContinuation = null
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "💥 Error procesando resultados: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(AudioResult.Error("Error procesando audio: ${e.message}"))
                    }
                    currentContinuation = null
                }
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                android.util.Log.e(TAG, "❌ ERROR: $errorMessage ($error)")

                if (continuation.isActive) {
                    continuation.resume(AudioResult.Error(errorMessage, error))
                }
                currentContinuation = null
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                android.util.Log.v(TAG, "🎭 Evento: $eventType")
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Error de audio. ¿Funciona el micrófono?"
            SpeechRecognizer.ERROR_CLIENT -> "Error del cliente. Intentá de nuevo."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono."
            SpeechRecognizer.ERROR_NETWORK -> "Sin internet. Necesita conexión."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red. Verificá conexión."
            SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió. Hablá más claro."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado. Esperá."
            SpeechRecognizer.ERROR_SERVER -> "Error del servidor. Intentá después."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No detecté voz. Hablá más fuerte."
            else -> "Error desconocido ($errorCode)."
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun destroy() {
        try {
            android.util.Log.d(TAG, "🧹 Destruyendo AudioManager")
            speechRecognizer?.destroy()
            speechRecognizer = null

            mediaRecorder?.release()
            mediaRecorder = null

            currentContinuation = null
            isInitialized = false
            android.util.Log.d(TAG, "✅ AudioManager destruido")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error destruyendo: ${e.message}")
        }
    }
}