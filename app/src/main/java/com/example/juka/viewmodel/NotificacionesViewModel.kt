package com.example.juka.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.data.local.room.NotificacionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel que maneja la lista de notificaciones del historial local.
 *
 * Expone dos StateFlow:
 *  - `notificaciones`: lista cronológica (más reciente primero) para la pantalla.
 *  - `unreadCount`: cantidad de no-leídas para el badge de la campana.
 *
 * El refresh es manual via `recargar()` — el caller lo llama desde la
 * pantalla al entrar y la campana lo llama en su `LaunchedEffect` para
 * mantener el badge al día. Es suficiente para nuestro volumen y evita
 * el costo de un listener permanente sobre Room.
 */
class NotificacionesViewModel(
    private val storage: LocalStorageHelper
) : ViewModel() {

    private val _notificaciones = MutableStateFlow<List<NotificacionEntity>>(emptyList())
    val notificaciones: StateFlow<List<NotificacionEntity>> = _notificaciones.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init {
        recargar()
    }

    /** Refresca lista + contador. */
    fun recargar() {
        viewModelScope.launch {
            _notificaciones.value = storage.getNotificaciones()
            _unreadCount.value = storage.countUnreadNotificaciones()
        }
    }

    /**
     * Marca todas como leídas. Se llama desde la pantalla cuando el
     * usuario entra: ver la lista cuenta como "ya las viste".
     */
    fun marcarTodasLeidas() {
        viewModelScope.launch {
            storage.markAllNotificacionesAsRead()
            recargar()
        }
    }

    /** Borra una notificación específica (swipe to dismiss). */
    fun borrar(id: Long) {
        viewModelScope.launch {
            storage.deleteNotificacion(id)
            recargar()
        }
    }

    /** Limpia todo el historial (opción "borrar todo"). */
    fun borrarTodas() {
        viewModelScope.launch {
            storage.deleteAllNotificaciones()
            recargar()
        }
    }
}
