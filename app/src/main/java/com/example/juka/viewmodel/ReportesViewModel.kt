package com.example.juka.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.firebase.PartePesca
import com.example.juka.data.repository.FishingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reportes = lista.sortedByDescending { r -> r.fecha } // Ordenamos aquí, no en la UI
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}