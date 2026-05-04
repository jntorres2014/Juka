package com.example.juka.data

import android.util.Log
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.ParteEnProgreso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FishCounterManager(
    private val localStorageHelper: LocalStorageHelper
) {
    private val _contadorPeces = MutableStateFlow<List<EspecieCapturada>>(emptyList())
    val contadorPeces: StateFlow<List<EspecieCapturada>> = _contadorPeces.asStateFlow()

    fun agregarPezAlContador(nombreEspecie: String, cantidad: Int) {
        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst { it.nombre.equals(nombreEspecie, ignoreCase = true) }

        if (indiceExistente != -1) {
            val itemExistente = listaActual[indiceExistente]
            listaActual[indiceExistente] = itemExistente.copy(numeroEjemplares = itemExistente.numeroEjemplares + cantidad)
        } else {
            listaActual.add(EspecieCapturada(nombre = nombreEspecie, numeroEjemplares = cantidad))
        }
        _contadorPeces.value = listaActual
    }

    fun eliminarPezDelContador(nombreEspecie: String) {
        val listaActual = _contadorPeces.value.toMutableList()
        listaActual.removeAll { it.nombre.equals(nombreEspecie, ignoreCase = true) }
        _contadorPeces.value = listaActual
    }

    fun actualizarCantidadPez(nombreEspecie: String, nuevaCantidad: Int) {
        if (nuevaCantidad <= 0) {
            eliminarPezDelContador(nombreEspecie)
            return
        }
        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst { it.nombre.equals(nombreEspecie, ignoreCase = true) }

        if (indiceExistente != -1) {
            listaActual[indiceExistente] = listaActual[indiceExistente].copy(numeroEjemplares = nuevaCantidad)
            _contadorPeces.value = listaActual
        }
    }

    fun incrementarEspecie(nombreEspecie: String) {
        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst { it.nombre.equals(nombreEspecie, ignoreCase = true) }

        if (indiceExistente != -1) {
            val itemExistente = listaActual[indiceExistente]
            listaActual[indiceExistente] = itemExistente.copy(numeroEjemplares = itemExistente.numeroEjemplares + 1)
            _contadorPeces.value = listaActual
        } else {
            agregarPezAlContador(nombreEspecie, 1)
        }
    }

    fun decrementarEspecie(nombreEspecie: String) {
        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst { it.nombre.equals(nombreEspecie, ignoreCase = true) }

        if (indiceExistente != -1) {
            val itemExistente = listaActual[indiceExistente]
            val nuevaCantidad = itemExistente.numeroEjemplares - 1

            if (nuevaCantidad > 0) {
                listaActual[indiceExistente] = itemExistente.copy(numeroEjemplares = nuevaCantidad)
            } else {
                listaActual.removeAt(indiceExistente)
            }
            _contadorPeces.value = listaActual
        }
    }

    fun limpiarContador() {
        _contadorPeces.value = emptyList()
    }

    fun especieYaEnContador(nombreEspecie: String): Boolean {
        return _contadorPeces.value.any { it.nombre.equals(nombreEspecie, ignoreCase = true) }
    }

    fun getTotalPecesContador(): Int {
        return _contadorPeces.value.sumOf { it.numeroEjemplares }
    }

    fun cargarContadorDesdeParteExistente(parte: ParteEnProgreso) {
        _contadorPeces.value = parte.especiesCapturadas
    }

    fun guardarEstadoContador(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = Json.encodeToString<List<EspecieCapturada>>(_contadorPeces.value)
                localStorageHelper.savePreference("contador_peces_backup", json)
            } catch (e: Exception) {
                Log.e("FishCounter", "Error guardando contador: ${e.message}")
            }
        }
    }

    fun restaurarEstadoContador(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = localStorageHelper.getPreference("contador_peces_backup")
                if (!json.isNullOrEmpty()) {
                    _contadorPeces.value = Json.decodeFromString(json)
                }
            } catch (e: Exception) {
                Log.e("FishCounter", "Error restaurando contador: ${e.message}")
            }
        }
    }
}