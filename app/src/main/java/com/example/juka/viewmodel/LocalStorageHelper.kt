package com.example.juka.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.juka.data.local.room.BorradorParteDao
import com.example.juka.data.local.room.BorradorParteEntity
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
import java.util.UUID

/**
 * Resumen ligero de un borrador para listarlo sin tener que parsear el JSON
 * del parte completo cada vez.
 */
data class BorradorMeta(
    val id: String,
    val porcentajeCompletado: Int,
    val fechaActualizacion: Long,
    val resumenLugar: String?,
    val resumenFecha: String?
)

/**
 * Helper unificado: Maneja Room (Chat + Borradores) y Preferencias.
 */
class LocalStorageHelper(
    private val context: Context,
    private val chatDao: ChatMessageDao,
    private val borradorDao: BorradorParteDao
) {

    // Herramientas
    private val gson = Gson()

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
    // 📁 SECCIÓN 2: BORRADORES OFFLINE (Room, multi-borrador)
    // ==========================================
    //
    // Cada parte que se está cargando tiene su propio borrador identificado
    // por UUID. El usuario puede tener N borradores simultáneos (ej. salió a
    // pescar 3 días sin señal y necesita cargar los 3 antes de poder enviar).
    //
    // Convenciones:
    //   - id: el UUID lo genera el ViewModel y se mantiene mientras dure la
    //     edición. Cada llamada a saveBorrador con el mismo id sobrescribe.
    //   - parteJson: ParteEnProgreso serializado con Gson. Si en el futuro
    //     cambia ParteEnProgreso de forma incompatible, hay que migrar.
    //   - resumenLugar/resumenFecha: snapshot para listar sin parsear el json.

    /**
     * Genera un id nuevo para arrancar un borrador. El llamador lo usa para
     * referenciar este borrador hasta que se complete o se descarte.
     */
    fun generarBorradorId(): String = UUID.randomUUID().toString()

    /**
     * Persiste (insert o update) un borrador. Llamar cada vez que el parte
     * cambia — el costo es bajo y garantiza que no se pierde nada.
     */
    suspend fun saveBorrador(id: String, parte: ParteEnProgreso) = withContext(Dispatchers.IO) {
        try {
            val entity = BorradorParteEntity(
                id = id,
                parteJson = gson.toJson(parte),
                fechaActualizacion = System.currentTimeMillis(),
                porcentajeCompletado = parte.porcentajeCompletado,
                resumenLugar = parte.nombreLugar,
                resumenFecha = parte.fecha
            )
            borradorDao.upsert(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Recupera un borrador específico, o null si no existe (fue borrado
     * porque se completó o porque el usuario lo descartó).
     */
    suspend fun getBorrador(id: String): ParteEnProgreso? = withContext(Dispatchers.IO) {
        try {
            val entity = borradorDao.getById(id) ?: return@withContext null
            gson.fromJson(entity.parteJson, ParteEnProgreso::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lista todos los borradores ordenados por fecha de actualización
     * descendente (más reciente primero). Devuelve metadata liviana — para
     * cargar el parte completo usar getBorrador(id).
     */
    suspend fun getAllBorradores(): List<BorradorMeta> = withContext(Dispatchers.IO) {
        try {
            borradorDao.getAll().map { entity ->
                BorradorMeta(
                    id = entity.id,
                    porcentajeCompletado = entity.porcentajeCompletado,
                    fechaActualizacion = entity.fechaActualizacion,
                    resumenLugar = entity.resumenLugar,
                    resumenFecha = entity.resumenFecha
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Borra un borrador puntual. Llamar cuando el parte se envía OK o el
     * usuario elige descartarlo.
     */
    suspend fun deleteBorrador(id: String) = withContext(Dispatchers.IO) {
        try {
            borradorDao.deleteById(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Borra todos los borradores. Útil para logout o limpieza explícita.
     */
    suspend fun deleteAllBorradores() = withContext(Dispatchers.IO) {
        try {
            borradorDao.deleteAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Cantidad de borradores pendientes (no requiere parsear los JSON). */
    suspend fun countBorradores(): Int = withContext(Dispatchers.IO) {
        try { borradorDao.count() } catch (e: Exception) { 0 }
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