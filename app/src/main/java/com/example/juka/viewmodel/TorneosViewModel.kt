package com.example.juka.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.firebase.TorneosFirebase
import com.example.juka.domain.model.EstadoParticipante
import com.example.juka.domain.model.EstadoTorneo
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.Torneo
import com.example.juka.domain.model.TipoPuntaje
import com.example.juka.domain.model.TorneoConParticipantes
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class TorneosUiState(
    val torneos: List<TorneoConParticipantes> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val torneoCreado: String? = null,
    val solicitudEnviada: Boolean = false,
    val torneoEncontrado: TorneoConParticipantes? = null
)

class TorneosViewModel : ViewModel() {

    private val firebase = TorneosFirebase()

    private val _uiState = MutableStateFlow(TorneosUiState())
    val uiState: StateFlow<TorneosUiState> = _uiState.asStateFlow()

    init { cargarTorneos() }

    fun cargarTorneos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            firebase.obtenerMisTorneos()
                .onSuccess { torneos -> _uiState.update { it.copy(torneos = torneos, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    fun crearTorneo(nombre: String, descripcion: String, fechaInicio: Date, fechaFin: Date, tipoPuntaje: TipoPuntaje, reglasPersonalizadas: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            firebase.crearTorneo(Torneo(nombre = nombre, descripcion = descripcion, fechaInicio = Timestamp(fechaInicio), fechaFin = Timestamp(fechaFin), tipoPuntaje = tipoPuntaje.name, reglasPersonalizadas = reglasPersonalizadas))
                .onSuccess { torneoId ->
                    firebase.obtenerMisTorneos().onSuccess { torneos ->
                        val nuevo = torneos.firstOrNull { it.torneo.id == torneoId }
                        _uiState.update { it.copy(torneos = torneos, torneoCreado = nuevo?.torneo?.codigoInvitacion, isLoading = false) }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    fun buscarTorneoPorCodigo(codigo: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            firebase.buscarTorneoPorCodigo(codigo)
                .onSuccess { torneo ->
                    if (torneo != null) _uiState.update { it.copy(torneoEncontrado = TorneoConParticipantes(torneo = torneo), isLoading = false) }
                    else _uiState.update { it.copy(error = "Código no encontrado. Verificá que esté bien escrito.", isLoading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    fun solicitarUnirse(torneoId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            firebase.solicitarUnirse(torneoId)
                .onSuccess { _uiState.update { it.copy(solicitudEnviada = true, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    fun responderSolicitud(torneoId: String, participanteId: String, aceptar: Boolean) {
        viewModelScope.launch {
            firebase.responderSolicitud(torneoId, participanteId, aceptar)
                .onSuccess { cargarTorneos() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Llamado desde HukaAppWithUser cuando se guarda un parte.
     * Guarda el parte en todos los torneos activos Y suma el puntaje automáticamente.
     */
    fun onParteSaved(parteId: String, parteData: ParteEnProgreso) {
        viewModelScope.launch {
            Log.d("TORNEO_DEBUG", "=== onParteSaved llamado ===")
            Log.d("TORNEO_DEBUG", "ParteId: $parteId")
            Log.d("TORNEO_DEBUG", "Especies: ${parteData.especiesCapturadas}")

            val torneosResult = firebase.obtenerMisTorneos().getOrNull() ?: run {
                Log.e("TORNEO_DEBUG", "❌ No se pudieron obtener torneos")
                return@launch
            }

            Log.d("TORNEO_DEBUG", "Torneos obtenidos: ${torneosResult.size}")
            torneosResult.forEach { t ->
                Log.d("TORNEO_DEBUG", "  → ${t.torneo.nombre} | estado: ${t.torneo.estado} | soyCreador: ${t.soyCreador} | miEstado: ${t.miEstado}")
            }

            val torneosActivos = torneosResult.filter { torneoConP ->
                torneoConP.torneo.estado == EstadoTorneo.ACTIVO &&
                        (torneoConP.soyCreador || torneoConP.miEstado == EstadoParticipante.ACEPTADO)
            }

            Log.d("TORNEO_DEBUG", "Torneos activos para sumar: ${torneosActivos.size}")

            if (torneosActivos.isEmpty()) {
                Log.w("TORNEO_DEBUG", "❌ Sin torneos activos — no se suma nada")
                return@launch
            }

            torneosActivos.forEach { torneoConP ->
                val puntaje = calcularPuntaje(torneoConP.torneo.tipoPuntajeEnum, parteData)
                Log.d("TORNEO_DEBUG", "Puntaje calculado para ${torneoConP.torneo.nombre}: $puntaje")
                Log.d("TORNEO_DEBUG", "TipoPuntaje: ${torneoConP.torneo.tipoPuntajeEnum}")

                firebase.guardarParteTorneo(torneoConP.torneo.id, parteId, parteData, puntaje)
                    .onSuccess { Log.d("TORNEO_DEBUG", "✅ Parte guardado en torneo") }
                    .onFailure { e -> Log.e("TORNEO_DEBUG", "❌ Error guardando parte: ${e.message}") }
            }

            cargarTorneos()
        }
    }

    /**
     * Admin rechaza un parte — el puntaje se resta automáticamente.
     */
    fun rechazarParte(torneoId: String, parteId: String, motivo: String = "") {
        viewModelScope.launch {
            firebase.rechazarParte(torneoId, parteId, motivo)
                .onSuccess { cargarTorneos() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    private fun calcularPuntaje(tipo: TipoPuntaje, parteData: ParteEnProgreso): Int {
        return when (tipo) {
            TipoPuntaje.CANTIDAD_PECES     -> parteData.especiesCapturadas.sumOf { it.numeroEjemplares }
            TipoPuntaje.ESPECIES_DISTINTAS -> parteData.especiesCapturadas.size
            TipoPuntaje.PERSONALIZADO      -> 0  // El admin gestiona los puntos manualmente.
        }
    }

    fun mostrarCodigoTorneo(codigo: String) = _uiState.update { it.copy(torneoCreado = codigo) }
    fun limpiarTorneoCreado()      = _uiState.update { it.copy(torneoCreado = null) }
    fun limpiarSolicitudEnviada()  = _uiState.update { it.copy(solicitudEnviada = false) }
    fun limpiarTorneoEncontrado()  = _uiState.update { it.copy(torneoEncontrado = null) }
    fun limpiarError()             = _uiState.update { it.copy(error = null) }
}