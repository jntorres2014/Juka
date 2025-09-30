package com.example.juka.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.ParteSessionChat
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.ChatMessageWithMode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatHistoryUiState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessageWithMode> = emptyList(),
    val session: ParteSessionChat? = null,
    val error: String? = null,
    val isEmpty: Boolean = false
)

class ChatHistoryViewModel(
    var firebaseManager: FirebaseManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatHistoryUiState())
    val uiState: StateFlow<ChatHistoryUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ChatHistoryVM"
    }

    /**
     * Carga el historial de chat para un parte específico
     */
/*
    fun cargarChatPorParteId(parteId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Cargando chat para parte: $parteId")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                // Buscar los mensajes del chat para este parte
                val messages = firebaseManager.obtenerChatPorParteId(parteId)

                if (messages.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messages = messages,
                        isEmpty = false,
                        error = null
                    )

                    Log.i(TAG, "Chat cargado: ${messages.size} mensajes")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se encontró el chat para este parte",
                        isEmpty = true
                    )

                    Log.w(TAG, "No se encontró chat para parte: $parteId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando chat: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando chat: ${e.localizedMessage}"
                )
            }
        }
    }
*/

    /**
     * Carga el historial directamente por session ID
     */
/*    fun cargarChatPorSessionId(sessionId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Cargando chat por session ID: $sessionId")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                // Obtener la sesión
//                val session = firebaseManager.obtenerSesionPorId(sessionId)

                if (session != null) {
                    // Obtener el historial usando el sessionId que ya tenemos
                    //val messages = firebaseManager.obtenerHistorialChat(sessionId)
                    *//*
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        session = session,
                        messages = messages as List<ChatMessageWithMode>,
                        isEmpty = messages.isEmpty(),
                        error = null
                    )*//*

                    //Log.i(TAG, "Chat cargado por session: ${messages.size} mensajes")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se encontró la sesión",
                        isEmpty = true
                    )

                    Log.w(TAG, "No se encontró sesión: $sessionId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando chat por session: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando chat: ${e.localizedMessage}"
                )
            }
        }
    }*/

    /**
     * Refresca el chat actual
     */
    fun refrescarChat() {
        val currentSession = _uiState.value.session
        if (currentSession != null) {
            //cargarChatPorSessionId(currentSession.sessionId)
        }
    }

    /**
     * Limpia el estado
     */
    fun limpiarEstado() {
        _uiState.value = ChatHistoryUiState()
    }
}