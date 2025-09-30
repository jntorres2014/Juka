package com.example.juka.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.encuesta.EncuestaData
import com.example.juka.data.encuesta.EstadoEncuesta
import com.example.juka.data.encuesta.Pregunta
import com.example.juka.data.encuesta.PreguntasEncuesta
import com.example.juka.data.encuesta.RespuestaPregunta
import com.example.juka.data.encuesta.TipoPregunta
import com.example.juka.data.encuesta.ValidadorEncuesta
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EncuestaViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "📋 EncuestaViewModel"
    }

    private val firebaseManager = FirebaseManager(getApplication())

    // Estados principales
    private val _estadoEncuesta = MutableStateFlow(EstadoEncuesta())
    val estadoEncuesta: StateFlow<EstadoEncuesta> = _estadoEncuesta.asStateFlow()

    private val _preguntaActual = MutableStateFlow(0)
    val preguntaActual: StateFlow<Int> = _preguntaActual.asStateFlow()

    private val _totalPreguntas = MutableStateFlow(PreguntasEncuesta.obtenerTotalPreguntas())
    val totalPreguntas: StateFlow<Int> = _totalPreguntas.asStateFlow()

    // Estados de UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _mensajeError = MutableStateFlow<String?>(null)
    val mensajeError: StateFlow<String?> = _mensajeError.asStateFlow()

    private val _encuestaCompletada = MutableStateFlow(false)
    val encuestaCompletada: StateFlow<Boolean> = _encuestaCompletada.asStateFlow()

    private val _guardandoProgreso = MutableStateFlow(false)
    val guardandoProgreso: StateFlow<Boolean> = _guardandoProgreso.asStateFlow()

    // Datos temporales
    private val respuestasTemporales = mutableMapOf<Int, RespuestaPregunta>()
    private var fechaInicio: Timestamp? = null

    init {
        Log.d(TAG, "Inicializando EncuestaViewModel")
        cargarProgresoGuardado()
        actualizarEstado()
    }

    /**
     * Carga progreso guardado si existe
     */
    private fun cargarProgresoGuardado() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                when (val resultado = firebaseManager.obtenerProgresoEncuesta()) {
                    is FirebaseResult.Success -> {
                        // Como tu FirebaseResult.Success no tiene data, necesitamos
                        // obtener los datos de otra manera o modificar la función
                        Log.d(TAG, "Progreso encontrado, pero necesita implementación específica")

                        // Por ahora, no hay progreso previo que cargar
                        // Esto se puede mejorar cuando ajustes el FirebaseResult para incluir datos
                    }
                    is FirebaseResult.Error -> {
                        Log.w(TAG, "No se pudo cargar progreso: ${resultado.message}")
                    }
                    is FirebaseResult.Loading -> {
                        // Mantener loading state
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando progreso: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Obtiene la pregunta actual
     */
    fun obtenerPreguntaActual(): Pregunta? {
        return PreguntasEncuesta.obtenerPreguntaPorId(_preguntaActual.value + 1)
    }

    /**
     * Obtiene la respuesta actual para la pregunta
     */
    fun obtenerRespuestaActual(): RespuestaPregunta? {
        val preguntaId = _preguntaActual.value + 1
        return respuestasTemporales[preguntaId]
    }

    /**
     * Guarda la respuesta de la pregunta actual
     */
    fun guardarRespuesta(respuesta: RespuestaPregunta) {
        val preguntaId = _preguntaActual.value + 1
        respuestasTemporales[preguntaId] = respuesta.copy(preguntaId = preguntaId)

        Log.d(TAG, "Respuesta guardada para pregunta $preguntaId")
        actualizarEstado()

        // Auto-guardar progreso cada 3 respuestas
        if (respuestasTemporales.size % 3 == 0) {
            guardarProgresoAutomatico()
        }
    }

    /**
     * Navega a la siguiente pregunta
     */
// En EncuestaViewModel.kt, en la función siguientePregunta()
    fun siguientePregunta() {
        Log.d(TAG, "=== SIGUIENTE PREGUNTA DEBUG ===")
        Log.d(TAG, "Pregunta actual antes: ${_preguntaActual.value}")

        val preguntaActual = obtenerPreguntaActual()
        val respuestaActual = obtenerRespuestaActual()

        Log.d(TAG, "Pregunta: ${preguntaActual?.texto}")
        Log.d(TAG, "Es obligatoria: ${preguntaActual?.esObligatoria}")
        Log.d(TAG, "Respuesta actual: $respuestaActual")

        if (preguntaActual != null && preguntaActual.esObligatoria) {
            if (respuestaActual == null) {
                Log.d(TAG, "ERROR: Respuesta nula para pregunta obligatoria")
                _mensajeError.value = "Esta pregunta es obligatoria"
                return
            }

            val validacion = ValidadorEncuesta.validarRespuesta(preguntaActual, respuestaActual)
            Log.d(TAG, "Validación: ${validacion.esValida}, Error: ${validacion.mensajeError}")

            if (!validacion.esValida) {
                _mensajeError.value = validacion.mensajeError
                return
            }
        }

        if (_preguntaActual.value < PreguntasEncuesta.obtenerTotalPreguntas() - 1) {
            _preguntaActual.value += 1
            Log.d(TAG, "Pregunta actual después: ${_preguntaActual.value}")
            _mensajeError.value = null
            actualizarEstado()
        } else {
            Log.d(TAG, "Ya estamos en la última pregunta")
        }
    }
    /**
     * Navega a la pregunta anterior
     */
    fun preguntaAnterior() {
        if (_preguntaActual.value > 0) {
            _preguntaActual.value -= 1
            _mensajeError.value = null
            actualizarEstado()
            Log.d(TAG, "Retrocediendo a pregunta ${_preguntaActual.value + 1}")
        }
    }

    /**
     * Salta directamente a una pregunta específica
     */
    fun irAPregunta(numeroPregunta: Int) {
        val indice = numeroPregunta - 1
        if (indice in 0 until PreguntasEncuesta.obtenerTotalPreguntas()) {
            _preguntaActual.value = indice
            _mensajeError.value = null
            actualizarEstado()
            Log.d(TAG, "Navegando a pregunta $numeroPregunta")
        }
    }

    /**
     * Actualiza el estado de la encuesta
     */
    private fun actualizarEstado() {
        val preguntaActualIndex = _preguntaActual.value
        val totalPreguntas = PreguntasEncuesta.obtenerTotalPreguntas()
        val progreso = ((respuestasTemporales.size.toFloat() / totalPreguntas) * 100).toInt()

        val nuevoEstado = EstadoEncuesta(
            preguntaActual = preguntaActualIndex,
            respuestasTemporales = respuestasTemporales,
            puedeAvanzar = preguntaActualIndex < totalPreguntas - 1,
            puedeRetroceder = preguntaActualIndex > 0,
            estaCompleta = respuestasTemporales.size >= totalPreguntas,
            progresoPorcentaje = progreso
        )

        _estadoEncuesta.value = nuevoEstado
        Log.d(TAG, "Estado actualizado: ${nuevoEstado.progresoPorcentaje}% completado")
    }

    /**
     * Guarda progreso automáticamente
     */
    private fun guardarProgresoAutomatico() {
        viewModelScope.launch {
            try {
                _guardandoProgreso.value = true

                val respuestasList = respuestasTemporales.values.toList()
                val progreso = _estadoEncuesta.value.progresoPorcentaje

                when (val resultado = firebaseManager.guardarProgresoEncuesta(respuestasList, progreso)) {
                    is FirebaseResult.Success -> {
                        Log.d(TAG, "Progreso auto-guardado: $progreso%")
                    }
                    is FirebaseResult.Error -> {
                        Log.e(TAG, "Error auto-guardando: ${resultado.message}")
                    }
                    is FirebaseResult.Loading -> {
                        // Estado de carga
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en auto-guardado: ${e.message}", e)
            } finally {
                _guardandoProgreso.value = false
            }
        }
    }

    /**
     * Guarda progreso manualmente
     */
    fun guardarProgreso() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val respuestasList = respuestasTemporales.values.toList()
                val progreso = _estadoEncuesta.value.progresoPorcentaje

                when (val resultado = firebaseManager.guardarProgresoEncuesta(respuestasList, progreso)) {
                    is FirebaseResult.Success -> {
                        Log.i(TAG, "Progreso guardado manualmente: $progreso%")
                        // Mostrar mensaje de éxito si es necesario
                    }
                    is FirebaseResult.Error -> {
                        _mensajeError.value = "Error guardando progreso: ${resultado.message}"
                    }
                    is FirebaseResult.Loading -> {
                        // Estado de carga
                    }
                }
            } catch (e: Exception) {
                _mensajeError.value = "Error guardando progreso"
                Log.e(TAG, "Error guardando progreso: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Completa y envía la encuesta
     */
    fun completarEncuesta() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Validar encuesta completa
                val respuestasList = respuestasTemporales.values.toList()
                val validacion = ValidadorEncuesta.validarEncuestaCompleta(respuestasList)

                if (!validacion.esValida) {
                    _mensajeError.value = validacion.mensajeError
                    _isLoading.value = false
                    return@launch
                }

                // Crear EncuestaData final
                val userId = firebaseManager.getCurrentUserId() ?: throw Exception("Usuario no autenticado")

                val encuestaFinal = EncuestaData(
                    userId = userId,
                    respuestas = respuestasList,
                    fechaInicio = fechaInicio ?: Timestamp.now(),
                    fechaCompletado = Timestamp.now(),
                    completada = true,
                    progreso = 100,
                    dispositivo = android.os.Build.MODEL,
                    versionApp = "1.0.0"
                )

                // Guardar en Firebase
                when (val resultado = firebaseManager.guardarRespuestasEncuesta(encuestaFinal)) {
                    is FirebaseResult.Success -> {
                        Log.i(TAG, "Encuesta completada exitosamente")

                        // Limpiar progreso temporal
                        firebaseManager.limpiarProgresoEncuesta()

                        _encuestaCompletada.value = true

                        // Delay para mostrar animación de éxito
                        delay(2000)

                    }
                    is FirebaseResult.Error -> {
                        _mensajeError.value = "Error completando encuesta: ${resultado.message}"
                        Log.e(TAG, "Error completando encuesta: ${resultado.message}")
                    }
                    is FirebaseResult.Loading -> {
                        // Estado de carga
                    }
                }

            } catch (e: Exception) {
                _mensajeError.value = "Error inesperado: ${e.message}"
                Log.e(TAG, "Error completando encuesta: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Cancela la encuesta y elimina progreso
     */
    fun cancelarEncuesta() {
        viewModelScope.launch {
            try {
                firebaseManager.limpiarProgresoEncuesta()
                Log.i(TAG, "Encuesta cancelada")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelando encuesta: ${e.message}")
            }
        }
    }

    /**
     * Limpia mensajes de error
     */
    fun limpiarError() {
        _mensajeError.value = null
    }

    /**
     * Verifica si puede completar la encuesta
     */
    fun puedeCompletarEncuesta(): Boolean {
        val preguntasObligatorias = PreguntasEncuesta.PREGUNTAS.filter { it.esObligatoria }
        val respuestasObligatorias = respuestasTemporales.values.filter { respuesta ->
            preguntasObligatorias.any { it.id == respuesta.preguntaId }
        }

        return respuestasObligatorias.size >= preguntasObligatorias.size
    }

    /**
     * Obtiene resumen de progreso
     */
    fun obtenerResumenProgreso(): String {
        val total = PreguntasEncuesta.obtenerTotalPreguntas()
        val respondidas = respuestasTemporales.size
        val porcentaje = _estadoEncuesta.value.progresoPorcentaje

        return "Pregunta ${_preguntaActual.value + 1} de $total ($porcentaje% completado)"
    }

    /**
     * Obtiene las preguntas faltantes obligatorias
     */
    fun obtenerPreguntasFaltantes(): List<Int> {
        val preguntasObligatorias = PreguntasEncuesta.PREGUNTAS.filter { it.esObligatoria }
        val respondidas = respuestasTemporales.keys

        return preguntasObligatorias.filter { pregunta ->
            pregunta.id !in respondidas
        }.map { it.id }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "EncuestaViewModel destruido")
    }
}