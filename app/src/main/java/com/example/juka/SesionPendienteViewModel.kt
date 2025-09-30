/*
// SesionesPendientesViewModel.kt
package com.example.juka.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.EstadoParte
import com.example.juka.ParteSessionChat
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SesionesPendientesUiState(
    val isLoading: Boolean = false,
    val sesiones: List<ParteSessionChat> = emptyList(),
    val estadisticas: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val isEmpty: Boolean = false,
    val isRefreshing: Boolean = false
)

class SesionesPendientesViewModel(
    private val firebaseManager: FirebaseManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SesionesPendientesUiState())
    val uiState: StateFlow<SesionesPendientesUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SesionesPendientesVM"
    }

    init {
//        cargarSesionesPendientes()
    }

    */
/**
     * Carga todas las sesiones pendientes
     *//*

*/
/*
    fun cargarSesionesPendientes() {
        viewModelScope.launch {
            Log.d(TAG, "Cargando sesiones pendientes")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val sesiones = firebaseManager.obtenerSesionesPendientes()
                //val estadisticas = firebaseManager.obtenerEstadisticasSesiones()

                *//*

*/
/*_uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sesiones = sesiones,
                    estadisticas = //,
                    isEmpty = sesiones.isEmpty(),
                    error = null
                )*//*
*/
/*


                Log.i(TAG, "Sesiones pendientes cargadas: ${sesiones.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando sesiones pendientes: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando sesiones: ${e.localizedMessage}"
                )
            }
        }
    }
*//*


    */
/**
     * Refresca la lista de sesiones
     *//*

    fun refrescarSesiones() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            try {
//                val sesiones = firebaseManager.obtenerSesionesPendientes()
//                val estadisticas = firebaseManager.obtenerEstadisticasSesiones()

               */
/* _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    sesiones = sesiones,
                    estadisticas = estadisticas,
                    isEmpty = sesiones.isEmpty(),
                    error = null
                )
*//*

//                Log.i(TAG, "Sesiones refrescadas: ${sesiones.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error refrescando: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "Error refrescando: ${e.localizedMessage}"
                )
            }
        }
    }

    */
/**
     * Actualiza el estado de una sesión específica
     *//*

    fun actualizarEstadoSesion(sessionId: String, nuevoEstado: EstadoParte) {
        viewModelScope.launch {
            Log.d(TAG, "Actualizando estado de sesión $sessionId a $nuevoEstado")

            try {
                val result = firebaseManager.actualizarEstadoSesion(sessionId, nuevoEstado)

                when (result) {
                    is FirebaseResult.Success -> {
                        Log.i(TAG, "Estado actualizado exitosamente")
                        // Refresar la lista para mostrar cambios
                        refrescarSesiones()
                    }
                    is FirebaseResult.Error -> {
                        Log.e(TAG, "Error actualizando estado: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            error = "Error actualizando estado: ${result.message}"
                        )
                    }
                    is FirebaseResult.Loading -> {
                        // No necesario manejar aquí
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en actualización: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error actualizando: ${e.localizedMessage}"
                )
            }
        }
    }

    */
/**
     * Elimina una sesión específica
     *//*

    fun eliminarSesion(sessionId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Eliminando sesión: $sessionId")

            try {
                val result = firebaseManager.eliminarSesion(sessionId)

                when (result) {
                    is FirebaseResult.Success -> {
                        Log.i(TAG, "Sesión eliminada exitosamente")
                        // Refresar la lista
                        refrescarSesiones()
                    }
                    is FirebaseResult.Error -> {
                        Log.e(TAG, "Error eliminando sesión: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            error = "Error eliminando sesión: ${result.message}"
                        )
                    }
                    is FirebaseResult.Loading -> {
                        // No necesario
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error eliminando: ${e.localizedMessage}"
                )
            }
        }
    }

    */
/**
     * Completa una sesión convirtiéndola a parte
     *//*

    fun completarSesion(session: ParteSessionChat) {
        viewModelScope.launch {
            //Log.d(TAG, "Completando sesión: ${session.sessionId}")

            try {
                val result = firebaseManager.convertirSessionAParte(session)

                when (result) {
                    is FirebaseResult.Success -> {
                        Log.i(TAG, "Sesión completada exitosamente")
                        // Refresar para remover de pendientes
                        refrescarSesiones()
                    }
                    is FirebaseResult.Error -> {
                        Log.e(TAG, "Error completando sesión: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            error = "Error completando sesión: ${result.message}"
                        )
                    }
                    is FirebaseResult.Loading -> {
                        // Mostrar loading si es necesario
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error completando: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error completando: ${e.localizedMessage}"
                )
            }
        }
    }

    */
/**
     * Filtra sesiones por estado
     *//*

    fun filtrarPorEstado(estado: EstadoParte?) {
        viewModelScope.launch {
            try {
                val sesiones = if (estado != null) {
//                    firebaseManager.obtenerSesionesUsuario(estado)
                } else {
//                    firebaseManager.obtenerSesionesPendientes()
                }

                _uiState.value = _uiState.value.copy(
                    sesiones = sesiones,
                    isEmpty = sesiones.isEmpty()
                )

                Log.d(TAG, "Sesiones filtradas por $estado: ${sesiones.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error filtrando: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error filtrando: ${e.localizedMessage}"
                )
            }
        }
    }

    */
/**
     * Limpia errores
     *//*

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
*/
