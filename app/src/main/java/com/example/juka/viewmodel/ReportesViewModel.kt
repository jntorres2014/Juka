package com.example.juka.viewmodel
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.firebase.PartePesca
import com.example.juka.data.repository.FishingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Convierte "yyyy-MM-dd" o "dd/MM/yyyy" a epoch millis para poder ordenar. Devuelve 0 si no puede. */
private fun fechaAEpoch(fecha: String?): Long {
    if (fecha.isNullOrBlank()) return 0L
    val formatos = listOf("yyyy-MM-dd", "dd/MM/yyyy")
    for (fmt in formatos) {
        try {
            return java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                .parse(fecha)?.time ?: continue
        } catch (_: Exception) { }
    }
    return 0L
}

class ReportesViewModel(
    private val repository: FishingRepository
) : ViewModel() {

    // Estado encapsulado (Loading, Success, Error)
    data class ReportesUiState(
        val reportes: List<PartePesca> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ReportesUiState())
    val uiState: StateFlow<ReportesUiState> = _uiState.asStateFlow()

    init {
        cargarReportes()
    }

    fun cargarReportes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val lista = repository.obtenerMisPartes()
                val ordenados = lista.sortedByDescending { r -> fechaAEpoch(r.fecha) }
                _uiState.update {
                    it.copy(isLoading = false, reportes = ordenados)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}