// PartesConChatViewModel.kt - Extensión del PartesViewModel existente
package com.example.juka.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.PartePesca
import com.example.juka.viewmodel.ChatMessage
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
    private val firebaseManager: FirebaseManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartesConChatUiState())
    val uiState: StateFlow<PartesConChatUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "PartesConChatVM"
    }

    /**
     * Carga partes con información de chat asociado
     */
    fun cargarPartesConChat(limite: Int = 50) {
        viewModelScope.launch {
            Log.d(TAG, "Cargando partes con información de chat")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                // Cargar partes básicos
                val partes = firebaseManager.obtenerMisPartes(limite)

                // Para cada parte, verificar si tiene chat asociado
                val partesConChat = partes.map { parte ->
                    try {
                        // obtenerChatPorParteId devuelve List<ChatMessage>
                        val chatMessages = firebaseManager.obtenerChatPorParteId(parte.id)

                        // Asegurar que realmente son ChatMessage
                        val validChatMessages = chatMessages.filterIsInstance<ChatMessage>()

                        ParteConChat(
                            parte = parte,
                            tieneChat = validChatMessages.isNotEmpty(),
                            chatMessages = validChatMessages,
                            numeroMensajes = validChatMessages.size
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error verificando chat para parte ${parte.id}: ${e.message}")
                        ParteConChat(
                            parte = parte,
                            tieneChat = false,
                            chatMessages = emptyList(),
                            numeroMensajes = 0
                        )
                    }
                }

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

    /**
     * Refresca la lista de partes
     */
    fun refrescarPartes() {
        cargarPartesConChat()
    }

    /**
     * Filtra partes que tienen chat
     */
    fun filtrarSoloConChat() {
        val currentPartes = _uiState.value.partes
        val partesConChat = currentPartes.filter { it.tieneChat }

        _uiState.value = _uiState.value.copy(
            partes = partesConChat,
            isEmpty = partesConChat.isEmpty()
        )

        Log.d(TAG, "Filtrados partes con chat: ${partesConChat.size}")
    }

    /**
     * Muestra todos los partes nuevamente
     */
    fun mostrarTodos() {
        cargarPartesConChat()
    }
}