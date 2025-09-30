// PartesConChatViewModel.kt - ViewModel para la pantalla de "Mis Reportes"
package com.example.juka.features.reportes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.models.PartePesca
import com.example.juka.data.repositories.ReportesRepository
import com.example.juka.features.chat.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParteConChat(
    val parte: PartePesca,
    val tieneChat: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val numeroMensajes: Int = 0
)

data class PartesConChatUiState(
    val isLoading: Boolean = false,
    val partes: List<ParteConChat> = emptyList(),
    val error: String? = null,
    val isEmpty: Boolean = false
)

class PartesConChatViewModel(
    private val reportesRepository: ReportesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartesConChatUiState())
    val uiState: StateFlow<PartesConChatUiState> = _uiState.asStateFlow()
    private var allPartes: List<ParteConChat> = emptyList()


    companion object {
        private const val TAG = "PartesConChatVM"
    }

    fun cargarPartesConChat(limite: Int = 50) {
        viewModelScope.launch {
            Log.d(TAG, "Cargando partes con información de chat")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val partesConChat = reportesRepository.getPartesConChat(limite)
                allPartes = partesConChat

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    partes = partesConChat,
                    isEmpty = partesConChat.isEmpty(),
                    error = null
                )

                Log.i(TAG, "Partes cargados: ${partesConChat.size}, con chat: ${partesConChat.count { it.tieneChat }}")

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando partes: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando partes: ${e.localizedMessage}"
                )
            }
        }
    }

    fun refrescarPartes() {
        cargarPartesConChat()
    }

    fun filtrarSoloConChat() {
        val partesConChat = allPartes.filter { it.tieneChat }

        _uiState.value = _uiState.value.copy(
            partes = partesConChat,
            isEmpty = partesConChat.isEmpty()
        )

        Log.d(TAG, "Filtrados partes con chat: ${partesConChat.size}")
    }

    fun mostrarTodos() {
        _uiState.value = _uiState.value.copy(
            partes = allPartes,
            isEmpty = allPartes.isEmpty()
        )
        Log.d(TAG, "Mostrando todos los partes: ${allPartes.size}")
    }
}
