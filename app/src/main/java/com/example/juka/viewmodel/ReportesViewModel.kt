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

                // 🐛 DEBUG: cuántos partes llegaron y cuáles tienen coordenada.
                Log.d("DEBUG_PARTES", "📥 obtenerMisPartes() devolvió ${ordenados.size} partes")
                ordenados.forEach { r ->
                    Log.d(
                        "DEBUG_PARTES",
                        "  • id=${r.id} fecha=${r.fecha} lat=${r.ubicacion?.latitud} lng=${r.ubicacion?.longitud} lugar=${r.ubicacion?.nombre}"
                    )
                }
                val conCoord = ordenados.count { it.ubicacion?.latitud != null }
                Log.d("DEBUG_PARTES", "🗺️ Con coordenada (van al mapa): $conCoord de ${ordenados.size}")

                _uiState.update {
                    it.copy(isLoading = false, reportes = ordenados)
                }
            } catch (e: Exception) {
                Log.e("DEBUG_PARTES", "💥 Error cargando reportes: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}