// AudioManager.kt - Versión con logs detallados para debugging
package com.example.juka

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private var isInitialized = false

    companion object {
        private const val TAG = "🎤 AudioManager"
    }

    fun initialize(): Boolean {
        return try {
            android.util.Log.d(TAG, "🔧 Iniciando inicialización del AudioManager")

            // Verificar si el reconocimiento está disponible
            val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            android.util.Log.d(TAG, "📱 Reconocimiento disponible en dispositivo: $isAvailable")

            if (!isAvailable) {
                android.util.Log.e(TAG, "❌ Speech recognition NO disponible en este dispositivo")
                return false
            }

            // Verificar permisos
            val hasPermission = hasAudioPermission()
            android.util.Log.d(TAG, "🔐 Permisos de audio: $hasPermission")

            // Destruir instancia anterior si existe
            speechRecognizer?.destroy()
            android.util.Log.d(TAG, "🧹 Instancia anterior destruida")

            // Crear nueva instancia
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            android.util.Log.d(TAG, "🆕 Nueva instancia creada: ${speechRecognizer != null}")

            isInitialized = true
            android.util.Log.i(TAG, "✅ AudioManager inicializado correctamente")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error inicializando AudioManager: ${e.message}", e)
            false
        }
    }

    suspend fun startListening(): AudioResult = suspendCancellableCoroutine { continuation ->
        android.util.Log.d(TAG, "🎬 Iniciando proceso de escucha")

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

        try {
            val intent = createSpeechIntent()
            android.util.Log.d(TAG, "📋 Intent de speech creado con parámetros:")
            android.util.Log.d(TAG, "   - Idioma: ${intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)}")
            android.util.Log.d(TAG, "   - Modelo: ${intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)}")

            val listener = createRecognitionListener(continuation)

            speechRecognizer?.setRecognitionListener(listener)
            android.util.Log.d(TAG, "🎧 Listener configurado")

            speechRecognizer?.startListening(intent)
            android.util.Log.i(TAG, "🎤 INICIANDO ESCUCHA - Hablá ahora!")

            // Manejar cancelación
            continuation.invokeOnCancellation {
                try {
                    android.util.Log.d(TAG, "❌ Cancelando escucha")
                    speechRecognizer?.cancel()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error cancelando: ${e.message}")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error iniciando reconocimiento: ${e.message}", e)
            continuation.resume(AudioResult.Error("Error al iniciar reconocimiento: ${e.message}"))
        }
    }

    private fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) // Más resultados para debugging
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
    }

    private fun createRecognitionListener(
        continuation: kotlinx.coroutines.CancellableContinuation<AudioResult>
    ): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.i(TAG, "✅ Listo para escuchar - Micrófono activo")
                android.util.Log.d(TAG, "   Parámetros: $params")
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.i(TAG, "🗣️ DETECTÉ VOZ - Empezaste a hablar!")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Mostrar nivel de audio cada cierto tiempo
                if (rmsdB > 5.0f) {
                    android.util.Log.v(TAG, "🔊 Nivel de audio: $rmsdB dB")
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                android.util.Log.d(TAG, "📡 Buffer de audio recibido: ${buffer?.size} bytes")
            }

            override fun onEndOfSpeech() {
                android.util.Log.i(TAG, "🔚 Fin del discurso detectado - Procesando...")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                try {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    android.util.Log.i(TAG, "🔄 Resultado PARCIAL: ${partial?.firstOrNull()}")
                    android.util.Log.d(TAG, "   Todos los parciales: $partial")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error procesando parciales: ${e.message}")
                }
            }

            override fun onResults(results: Bundle?) {
                try {
                    android.util.Log.i(TAG, "🎉 RESULTADOS FINALES RECIBIDOS!")

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                    android.util.Log.i(TAG, "📝 Todas las opciones:")
                    matches?.forEachIndexed { index, match ->
                        val confidence = confidences?.getOrNull(index) ?: 0f
                        android.util.Log.i(TAG, "   $index: '$match' (confianza: $confidence)")
                    }

                    val bestMatch = matches?.firstOrNull()?.trim()
                    android.util.Log.i(TAG, "🏆 MEJOR RESULTADO: '$bestMatch'")

                    if (!bestMatch.isNullOrBlank()) {
                        android.util.Log.i(TAG, "✅ TRANSCRIPCIÓN EXITOSA: '$bestMatch'")
                        if (continuation.isActive) {
                            continuation.resume(AudioResult.Success(bestMatch))
                        } else {
                            android.util.Log.w(TAG, "⚠️ Continuation no activa, resultado descartado")
                        }
                    } else {
                        android.util.Log.w(TAG, "🤷 Resultado vacío o null")
                        if (continuation.isActive) {
                            continuation.resume(AudioResult.Error("No se entendió el audio. ¿Podrías repetir más claro?"))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "💥 Error procesando resultados: ${e.message}", e)
                    if (continuation.isActive) {
                        continuation.resume(AudioResult.Error("Error procesando audio: ${e.message}"))
                    }
                }
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                android.util.Log.e(TAG, "❌ ERROR DE RECONOCIMIENTO:")
                android.util.Log.e(TAG, "   Código: $error")
                android.util.Log.e(TAG, "   Mensaje: $errorMessage")

                // Información adicional según el error
                when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> {
                        android.util.Log.e(TAG, "🎤 Problema con micrófono o audio")
                    }
                    SpeechRecognizer.ERROR_NETWORK -> {
                        android.util.Log.e(TAG, "🌐 Problema de conexión a internet")
                    }
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        android.util.Log.e(TAG, "👂 No se pudo entender lo que dijiste")
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        android.util.Log.e(TAG, "⏰ No se detectó voz - hablaste?")
                    }
                }

                if (continuation.isActive) {
                    continuation.resume(AudioResult.Error(errorMessage, error))
                } else {
                    android.util.Log.w(TAG, "⚠️ Continuation no activa para error")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                android.util.Log.d(TAG, "🎭 Evento de reconocimiento: $eventType, params: $params")
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

    fun stopListening() {
        try {
            android.util.Log.d(TAG, "⏹️ Deteniendo escucha")
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error deteniendo: ${e.message}")
        }
    }

    fun cancelListening() {
        try {
            android.util.Log.d(TAG, "❌ Cancelando escucha")
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error cancelando: ${e.message}")
        }
    }

    private fun hasAudioPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        android.util.Log.d(TAG, "🔐 Permiso RECORD_AUDIO: $hasPermission")
        return hasPermission
    }

    fun destroy() {
        try {
            android.util.Log.d(TAG, "🧹 Destruyendo AudioManager")
            speechRecognizer?.destroy()
            speechRecognizer = null
            isInitialized = false
            android.util.Log.d(TAG, "✅ AudioManager destruido")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error destruyendo: ${e.message}")
        }
    }
}
