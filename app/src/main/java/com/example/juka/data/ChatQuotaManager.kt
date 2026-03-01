// domain/chat/ChatQuotaManager.kt
package com.example.juka.domain.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import java.util.*

class ChatQuotaManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val _quotaState = MutableStateFlow(QuotaState())
    val quotaState: StateFlow<QuotaState> = _quotaState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val DAILY_LIMIT = 5
        const val PREMIUM_LIMIT = 999
        private const val COLLECTION_NAME = "user_quotas"
        private const val TAG = "ChatQuotaManager"
    }

    init {
        // Cargar estado inicial
        scope.launch {
            checkAndResetDaily()
        }
    }

    suspend fun checkAndResetDaily() {
        val userId = auth.currentUser?.uid ?: return

        try {
            val quotaDoc = firestore.collection(COLLECTION_NAME)
                .document(userId)
                .get()
                .await()

            if (!quotaDoc.exists()) {
                // Primera vez - crear documento
                initializeUserQuota()
                return
            }

            val data = quotaDoc.data ?: return
            val lastReset = (data["lastResetDate"] as? Timestamp) ?: Timestamp.now()
            val queriesUsed = (data["dailyChatsUsed"] as? Long)?.toInt() ?: 0
            val isPremium = (data["isPremium"] as? Boolean) ?: false

            // Verificar si es un nuevo día
            if (isNewDay(lastReset)) {
                resetDailyQuota()
            } else {
                // Actualizar estado con datos del servidor
                val limit = if (isPremium) PREMIUM_LIMIT else DAILY_LIMIT
                _quotaState.value = QuotaState(
                    remaining = limit - queriesUsed,
                    total = limit,
                    lastReset = formatDate(lastReset.toDate()),
                    isPremium = isPremium
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking quota", e)
            // Fallback a valores por defecto
            _quotaState.value = QuotaState()
        }
    }

    private suspend fun initializeUserQuota() {
        val userId = auth.currentUser?.uid ?: return

        try {
            val initialData = hashMapOf(
                "dailyChatsUsed" to 0,
                "dailyPhotosUsed" to 0,
                "lastResetDate" to FieldValue.serverTimestamp(),
                "isPremium" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(COLLECTION_NAME)
                .document(userId)
                .set(initialData)
                .await()

            _quotaState.value = QuotaState(
                remaining = DAILY_LIMIT,
                total = DAILY_LIMIT,
                lastReset = formatDate(Date()),
                isPremium = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing quota", e)
        }
    }

    private suspend fun resetDailyQuota() {
        val userId = auth.currentUser?.uid ?: return

        try {
            val updates = hashMapOf(
                "dailyChatsUsed" to 0,
                "dailyPhotosUsed" to 0,
                "lastResetDate" to FieldValue.serverTimestamp()
            )

            firestore.collection(COLLECTION_NAME)
                .document(userId)
                .update(updates as Map<String, Any>)
                .await()

            val isPremium = _quotaState.value.isPremium
            val limit = if (isPremium) PREMIUM_LIMIT else DAILY_LIMIT

            _quotaState.value = QuotaState(
                remaining = limit,
                total = limit,
                lastReset = formatDate(Date()),
                isPremium = isPremium
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting quota", e)
        }
    }

    suspend fun canMakeQuery(): Boolean {
        checkAndResetDaily()
        return _quotaState.value.remaining > 0
    }

    suspend fun consumeQuery(): Boolean {
        if (!canMakeQuery()) {
            return false
        }

        val userId = auth.currentUser?.uid ?: return false

        return try {
            firestore.runTransaction { transaction ->
                val quotaRef = firestore.collection(COLLECTION_NAME).document(userId)
                val snapshot = transaction.get(quotaRef)

                if (!snapshot.exists()) {
                    throw Exception("Quota document doesn't exist")
                }

                val currentUsed = (snapshot.getLong("dailyChatsUsed") ?: 0).toInt()
                val isPremium = snapshot.getBoolean("isPremium") ?: false
                val limit = if (isPremium) PREMIUM_LIMIT else DAILY_LIMIT

                if (currentUsed >= limit) {
                    return@runTransaction false
                }

                // Actualizar en Firestore
                transaction.update(quotaRef,
                    "dailyChatsUsed", FieldValue.increment(1),
                    "lastUsedAt", FieldValue.serverTimestamp()
                )

                true
            }.await().also { success ->
                if (success) {
                    // Actualizar estado local
                    _quotaState.value = _quotaState.value.copy(
                        remaining = _quotaState.value.remaining - 1
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consuming query", e)
            false
        }
    }

    fun getQuotaMessage(): String {
        return when (_quotaState.value.remaining) {
            0 -> """
                ⚠️ **Límite diario alcanzado**
                
                Has usado tus consultas diarias.
                Se reinician a medianoche 🕐
                
                Mientras tanto podés:
                • 📝 Crear un parte de pesca
                • 📊 Ver tus estadísticas
                • 🐟 Identificar peces con fotos
            """.trimIndent()

            1 -> "⚠️ Te queda 1 consulta para hoy. ¡Usala sabiamente!"
            in 2..3 -> "💡 Te quedan ${_quotaState.value.remaining} consultas para hoy"
            else -> "Consultas restantes: ${_quotaState.value.remaining} de ${_quotaState.value.total} diarias"
        }
    }

    private fun isNewDay(lastReset: Timestamp): Boolean {
        val lastResetCal = Calendar.getInstance().apply {
            time = lastReset.toDate()
        }
        val nowCal = Calendar.getInstance()

        return lastResetCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR) ||
                lastResetCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)
    }

    private fun formatDate(date: Date): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date)
    }

    // Método para cuando agregues foto detection
    suspend fun consumePhotoQuery(): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        return try {
            firestore.runTransaction { transaction ->
                val quotaRef = firestore.collection(COLLECTION_NAME).document(userId)
                val snapshot = transaction.get(quotaRef)

                if (!snapshot.exists()) {
                    throw Exception("Quota document doesn't exist")
                }

                val currentUsed = (snapshot.getLong("dailyPhotosUsed") ?: 0).toInt()

                if (currentUsed >= DAILY_LIMIT) {
                    return@runTransaction false
                }

                transaction.update(quotaRef,
                    "dailyPhotosUsed", FieldValue.increment(1)
                )

                true
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error consuming photo query", e)
            false
        }
    }
}

// Tu data class existente sigue igual
data class QuotaState(
    val remaining: Int = 5,
    val total: Int = 5,
    val lastReset: String = "",
    val isPremium: Boolean = false
)