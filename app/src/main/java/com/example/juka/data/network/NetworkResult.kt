package com.example.juka.data.network

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Resultado tipado de una operación de red. Permite que los callers
 * distingan **por qué** falló: si no había conexión, si pasó el timeout,
 * o si la operación tiró una excepción genuina (auth, permisos, etc.).
 * Cada caso amerita un mensaje distinto en la UI.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()

    /** No hay red al momento de iniciar la operación. */
    object NoConnection : NetworkResult<Nothing>()

    /** Hay red pero el server no respondió a tiempo. */
    object Timeout : NetworkResult<Nothing>()

    /** Excepción genuina (4xx/5xx/permisos/parseo). */
    data class Error(val exception: Throwable) : NetworkResult<Nothing>()
}

/**
 * Envoltura para operaciones suspendidas que tocan red. Aplica:
 *   1. Chequeo upfront de conectividad (si pasás un `NetworkMonitor`).
 *   2. Timeout de `timeoutMillis` (default 15s).
 *   3. Mapeo a `NetworkResult` para el caller.
 *
 * Uso típico desde un ViewModel:
 *
 * ```kotlin
 * when (val r = withNetworkTimeout(monitor) { firestore.set(...).await() }) {
 *     is NetworkResult.Success     -> { ... }
 *     is NetworkResult.NoConnection -> mostrarSinSenal()
 *     is NetworkResult.Timeout      -> mostrarTimeout()
 *     is NetworkResult.Error        -> mostrarError(r.exception)
 * }
 * ```
 *
 * Si no se pasa `monitor`, no se hace chequeo upfront — sólo timeout
 * y manejo de excepciones. Útil para llamadas donde no queremos
 * dependencia del monitor.
 */
suspend fun <T> withNetworkTimeout(
    monitor: NetworkMonitor? = null,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    block: suspend () -> T
): NetworkResult<T> {
    if (monitor != null && !monitor.isOnlineNow()) {
        return NetworkResult.NoConnection
    }
    return try {
        val result = withTimeout(timeoutMillis) { block() }
        NetworkResult.Success(result)
    } catch (e: TimeoutCancellationException) {
        NetworkResult.Timeout
    } catch (e: Exception) {
        NetworkResult.Error(e)
    }
}

/** Timeout por defecto: 15s. Razonable para Firestore en buena red. */
const val DEFAULT_TIMEOUT_MS = 15_000L

/** Timeout más permisivo para Gemini (puede tardar más). */
const val GEMINI_TIMEOUT_MS = 30_000L

/** Timeout corto para uploads de imagen (las grandes pueden necesitar más). */
const val IMAGE_UPLOAD_TIMEOUT_MS = 25_000L
