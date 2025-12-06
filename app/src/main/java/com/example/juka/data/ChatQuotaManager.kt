// domain/chat/ChatQuotaManager.kt
package com.example.juka.domain.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

class ChatQuotaManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chat_quota", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _quotaState = MutableStateFlow(QuotaState())
    val quotaState: StateFlow<QuotaState> = _quotaState.asStateFlow()

    companion object {
        const val DAILY_LIMIT = 5
        const val PREMIUM_LIMIT = 999
    }

    init {
        checkAndResetDaily()
    }

    fun checkAndResetDaily() {
        val today = dateFormat.format(Date())
        val lastDay = prefs.getString("last_day", "") ?: ""  // ← FIX: Agregar ?: ""

        if (today != lastDay) {
            // Nuevo día - resetear
            prefs.edit().apply {
                putString("last_day", today)
                putInt("queries_remaining", DAILY_LIMIT)
                apply()
            }
            _quotaState.value = QuotaState(
                remaining = DAILY_LIMIT,
                total = DAILY_LIMIT,
                lastReset = today
            )
        } else {
            // Mismo día - cargar estado
            val remaining = prefs.getInt("queries_remaining", DAILY_LIMIT)
            _quotaState.value = QuotaState(
                remaining = remaining,
                total = DAILY_LIMIT,
                lastReset = lastDay  // ← Ahora lastDay es String no nullable
            )
        }
    }

    fun canMakeQuery(): Boolean {
        checkAndResetDaily()
        return _quotaState.value.remaining > 0
    }

    fun consumeQuery() {
        if (canMakeQuery()) {
            val newRemaining = _quotaState.value.remaining - 1
            prefs.edit().putInt("queries_remaining", newRemaining).apply()
            _quotaState.value = _quotaState.value.copy(remaining = newRemaining)
        }
    }

    fun getQuotaMessage(): String {
        return when (_quotaState.value.remaining) {
            0 -> """
                ⚠️ **Límite diario alcanzado**
                
                Has usado tus 5 consultas diarias.
                Se reinician a medianoche 🕐
                
                Mientras tanto podés:
                • 📝 Crear un parte de pesca
                • 📊 Ver tus estadísticas
            """.trimIndent()

            1 -> "⚠️ Te queda 1 consulta para hoy. Usala sabiamente!"
            else -> "Consultas restantes: ${_quotaState.value.remaining}/5"
        }
    }
}

data class QuotaState(
    val remaining: Int = 5,
    val total: Int = 5,
    val lastReset: String = "",
    val isPremium: Boolean = false
)