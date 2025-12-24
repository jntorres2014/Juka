package com.example.juka.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.juka.data.local.room.ChatMessageDao
import com.example.juka.data.local.room.ChatMessageEntity
import com.example.juka.domain.model.ChatMessageWithMode
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.viewmodel.MessageType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper unificado: Maneja Room (Chat), Archivos (Borradores) y Preferencias
 */
class LocalStorageHelper(
    private val context: Context,
    private val chatDao: ChatMessageDao
) {

    // Herramientas
    private val gson = Gson()
    private val fileName = "borrador_parte.json"

    // SharedPreferences para datos pequeños y rápidos
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("JukaPreferences", Context.MODE_PRIVATE)
    }

    // ==========================================
    // 🏛️ SECCIÓN 1: CHAT HISTORY (ROOM DB)
    // ==========================================

    suspend fun loadChatHistory(): List<ChatMessageWithMode> = withContext(Dispatchers.IO) {
        try {
            val entities = chatDao.getAllMessages()
            entities.map { entity ->
                ChatMessageWithMode(
                    content = entity.content,
                    isFromUser = entity.isFromUser,
                    type = try {
                        MessageType.valueOf(entity.type)
                    } catch (e: Exception) {
                        MessageType.TEXT
                    },
                    timestamp = entity.timestamp,
                    mode = ChatMode.GENERAL
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveChatMessage(message: ChatMessageWithMode) = withContext(Dispatchers.IO) {
        try {
            val entity = ChatMessageEntity(
                content = message.content,
                isFromUser = message.isFromUser,
                type = message.type.name,
                timestamp = message.timestamp
            )
            chatDao.insertMessage(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        chatDao.clearHistory()
    }

    // ==========================================
    // 📁 SECCIÓN 2: BORRADOR OFFLINE (FILES)
    // ==========================================

    fun saveParteBorrador(parte: ParteEnProgreso) {
        try {
            val jsonString = gson.toJson(parte)
            val file = File(context.filesDir, fileName)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getParteBorrador(): ParteEnProgreso? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null

        return try {
            val jsonString = file.readText()
            gson.fromJson(jsonString, ParteEnProgreso::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearBorrador() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasBorrador(): Boolean {
        return File(context.filesDir, fileName).exists()
    }

    // ==========================================
    // ⚙️ SECCIÓN 3: PREFERENCIAS (SharedPreferences)
    // ==========================================

    /**
     * Guarda una preferencia string
     */
    fun savePreference(key: String, value: String) {
        sharedPreferences.edit().apply {
            putString(key, value)
            apply()
        }
    }

    /**
     * Obtiene una preferencia string
     */
    fun getPreference(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    /**
     * Elimina una preferencia
     */
    fun removePreference(key: String) {
        sharedPreferences.edit().apply {
            remove(key)
            apply()
        }
    }

    /**
     * Guarda una preferencia boolean
     */
    fun saveBooleanPreference(key: String, value: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(key, value)
            apply()
        }
    }

    /**
     * Obtiene una preferencia boolean
     */
    fun getBooleanPreference(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    /**
     * Guarda una preferencia int
     */
    fun saveIntPreference(key: String, value: Int) {
        sharedPreferences.edit().apply {
            putInt(key, value)
            apply()
        }
    }

    /**
     * Obtiene una preferencia int
     */
    fun getIntPreference(key: String, defaultValue: Int = 0): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    // ==========================================
    // 🐟 SECCIÓN 4: CONTADOR DE PECES ESPECÍFICO
    // ==========================================

    /**
     * Guarda el contador de peces usando Gson
     */
    fun saveContadorPeces(especies: List<EspecieCapturada>) {
        try {
            val jsonString = gson.toJson(especies)
            savePreference("contador_peces_backup", jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Restaura el contador de peces usando Gson
     */
    fun getContadorPeces(): List<EspecieCapturada>? {
        return try {
            val jsonString = getPreference("contador_peces_backup")
            if (!jsonString.isNullOrEmpty()) {
                val type = object : TypeToken<List<EspecieCapturada>>() {}.type
                gson.fromJson(jsonString, type)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Limpia el contador de peces guardado
     */
    fun clearContadorPeces() {
        removePreference("contador_peces_backup")
    }

    /**
     * Verifica si hay un contador guardado
     */
    fun hasContadorPecesGuardado(): Boolean {
        return getPreference("contador_peces_backup") != null
    }

    // ==========================================
    // 🔧 UTILIDADES ADICIONALES
    // ==========================================

    /**
     * Limpia todas las preferencias (útil para logout)
     */
    fun clearAllPreferences() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Obtiene todas las claves guardadas (para debug)
     */
    fun getAllPreferenceKeys(): Set<String> {
        return sharedPreferences.all.keys
    }
}