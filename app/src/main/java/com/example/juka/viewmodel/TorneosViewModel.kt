package com.example.juka.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.firebase.TorneosFirebase
import com.example.juka.domain.model.EstadoParticipante
import com.example.juka.domain.model.EstadoTorneo
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.ReglasPuntaje
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

    fun crearTorneo(
        nombre: String,
        descripcion: String,
        fechaInicio: Date,
        fechaFin: Date,
        tipoPuntaje: TipoPuntaje,
        reglasPersonalizadas: String = "",
        reglas: ReglasPuntaje? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            firebase.crearTorneo(
                Torneo(
                    nombre = nombre,
                    descripcion = descripcion,
                    fechaInicio = Timestamp(fechaInicio),
                    fechaFin = Timestamp(fechaFin),
                    tipoPuntaje = tipoPuntaje.name,
                    reglasPersonalizadas = reglasPersonalizadas,
                    // Solo persistimos reglas estructuradas si el torneo es
                    // PERSONALIZADO. Para los otros tipos, queda null y el
                    // cálculo cae al camino legacy.
                    reglas = if (tipoPuntaje == TipoPuntaje.PERSONALIZADO) reglas else null
                )
            )
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
                // Para PERSONALIZADO con reglas estructuradas: chequeamos si
                // este es el primer parte del torneo (para el bonus). Best
                // effort — en la ventana de race podrían dos participantes
                // simultáneos cobrar el bonus; aceptable para la primera
                // versión, se puede endurecer después con transaction.
                val esPrimerParte = if (torneoConP.torneo.reglas?.bonusPrimerParte != null) {
                    firebase.esPrimerParteTorneo(torneoConP.torneo.id).getOrDefault(false)
                } else false

                val puntaje = calcularPuntaje(torneoConP.torneo, parteData, esPrimerParte)
                Log.d("TORNEO_DEBUG", "Puntaje calculado para ${torneoConP.torneo.nombre}: $puntaje (primer parte: $esPrimerParte)")

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

    /**
     * Calcula el puntaje de un parte para un torneo dado.
     *
     * - CANTIDAD_PECES: 1 punto por cada ejemplar (cualquier especie).
     * - ESPECIES_DISTINTAS: 1 punto por cada especie distinta.
     * - PERSONALIZADO con `reglas` definidas: aplica las 3 reglas aditivamente
     *   (bonus primer parte + cantidad + por especie/otros).
     * - PERSONALIZADO sin reglas (torneos viejos creados antes del cambio):
     *   suma 0, el admin gestiona puntos manualmente (legacy).
     */
    private fun calcularPuntaje(
        torneo: Torneo,
        parteData: ParteEnProgreso,
        esPrimerParteTorneo: Boolean
    ): Int {
        return when (torneo.tipoPuntajeEnum) {
            TipoPuntaje.CANTIDAD_PECES     -> parteData.especiesCapturadas.sumOf { it.numeroEjemplares }
            TipoPuntaje.ESPECIES_DISTINTAS -> parteData.especiesCapturadas.size
            TipoPuntaje.PERSONALIZADO -> {
                val reglas = torneo.reglas
                if (reglas == null || !reglas.tieneAlgunaRegla) {
                    // Torneo viejo o sin reglas configuradas → comportamiento
                    // legacy (admin gestiona puntos manualmente, suma 0).
                    0
                } else {
                    aplicarReglasPersonalizadas(reglas, parteData, esPrimerParteTorneo)
                }
            }
        }
    }

    /**
     * Aplica las reglas componibles de un torneo PERSONALIZADO sobre un parte.
     * Cada regla activa suma puntos al total — son aditivas.
     */
    private fun aplicarReglasPersonalizadas(
        reglas: com.example.juka.domain.model.ReglasPuntaje,
        parteData: ParteEnProgreso,
        esPrimerParteTorneo: Boolean
    ): Int {
        var total = 0

        // Regla 1: bonus al primer parte del torneo
        if (esPrimerParteTorneo) {
            reglas.bonusPrimerParte?.let { total += it }
        }

        // Regla 2: cantidad genérica (X puntos por cada pez)
        reglas.puntosPorPez?.let { porPez ->
            total += parteData.especiesCapturadas.sumOf { it.numeroEjemplares } * porPez
        }

        // Regla 3: tabla por especie + otros (catch-all)
        reglas.puntosPorEspecie?.takeIf { it.isNotEmpty() }?.let { tabla ->
            parteData.especiesCapturadas.forEach { esp ->
                val id = normalizarParaIdTorneo(esp.nombre)
                val ptsPorEjemplar = tabla[id] ?: reglas.puntosOtrosPeces
                total += ptsPorEjemplar * esp.numeroEjemplares
            }
        }

        return total
    }

    /**
     * Normaliza un nombre de especie a un id estable para usarlo como key
     * en el map `puntosPorEspecie` del torneo. Misma lógica que PescadexManager
     * para que ambos sistemas matcheen exactamente las mismas especies.
     */
    private fun normalizarParaIdTorneo(nombre: String): String =
        nombre.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    fun mostrarCodigoTorneo(codigo: String) = _uiState.update { it.copy(torneoCreado = codigo) }
    fun limpiarTorneoCreado()      = _uiState.update { it.copy(torneoCreado = null) }
    fun limpiarSolicitudEnviada()  = _uiState.update { it.copy(solicitudEnviada = false) }
    fun limpiarTorneoEncontrado()  = _uiState.update { it.copy(torneoEncontrado = null) }
    fun limpiarError()             = _uiState.update { it.copy(error = null) }
}