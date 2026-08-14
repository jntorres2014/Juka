package com.example.juka.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitor de conectividad reactivo basado en `ConnectivityManager`.
 *
 * Expone un `StateFlow<Boolean>` que la UI puede colectar para mostrar
 * banners "Sin conexión" / "Conexión restablecida", y un getter síncrono
 * `isOnlineNow()` que los ViewModels usan para decidir en el momento si
 * intentar una operación de red o ir directo al fallback offline.
 *
 * El callback se registra una vez al construir la instancia y nunca se
 * desregistra — la instancia vive todo el ciclo del proceso (singleton
 * en `HukaApplication`).
 *
 * Considera "online" cuando hay al menos una red con capability
 * `NET_CAPABILITY_INTERNET`. NO valida si efectivamente llega a
 * Internet (eso requeriría un ping); pero detecta correctamente los
 * casos comunes: modo avión, sin WiFi y sin datos, etc.
 */
class NetworkMonitor(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(currentOnlineState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
                Log.d(TAG, "✅ Network available")
            }

            override fun onLost(network: Network) {
                // Puede haber otra red activa, así que recomputamos en vez de
                // asumir offline.
                _isOnline.value = currentOnlineState()
                Log.d(TAG, "📡 Network lost; online=${_isOnline.value}")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet) _isOnline.value = true
            }
        })
    }

    /**
     * Devuelve el estado actual sin esperar a un nuevo evento del callback.
     * Útil para el chequeo upfront antes de intentar una operación.
     */
    fun isOnlineNow(): Boolean = currentOnlineState()

    private fun currentOnlineState(): Boolean {
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "📡 NetworkMonitor"
    }
}
