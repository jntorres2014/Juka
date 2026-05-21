package com.example.juka.data.local.room

import android.content.Context
import androidx.room.*

// 1. LA ENTIDAD (La tabla de la base de datos)
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isFromUser: Boolean,
    val type: String, // Guardamos el Enum como String (TEXT, AUDIO, IMAGE)
    val timestamp: String
)

// 2. EL DAO (El intermediario para guardar/leer)
@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC") // Orden cronológico
    suspend fun getAllMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICACIONES — historial local de notificaciones que vio el usuario.
// Incluye pushes FCM recibidos en foreground y logros desbloqueados.
// La "campanita" del header lee de acá.
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "notificaciones")
data class NotificacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val cuerpo: String,
    /** ms epoch del momento en que llegó/se generó. Para ordenar y mostrar "hace X". */
    val timestamp: Long,
    /** false cuando aparece nueva; true cuando el usuario abre la pantalla. */
    val leida: Boolean = false,
    /** "FCM" | "LOGRO" | "SISTEMA" — para mostrar ícono distinto en la lista. */
    val origen: String = "SISTEMA"
)

@Dao
interface NotificacionDao {
    @Query("SELECT * FROM notificaciones ORDER BY timestamp DESC")
    suspend fun getAll(): List<NotificacionEntity>

    @Query("SELECT COUNT(*) FROM notificaciones WHERE leida = 0")
    suspend fun countUnread(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notificacion: NotificacionEntity): Long

    @Query("UPDATE notificaciones SET leida = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notificaciones WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notificaciones")
    suspend fun deleteAll()
}

// ─────────────────────────────────────────────────────────────────────────────
// BORRADORES DE PARTE — múltiples partes a medio cargar persistidos local
// para que el usuario pueda volver a cualquiera, retomarlos y enviarlos
// cuando vuelva a tener señal.
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "borradores_parte")
data class BorradorParteEntity(
    /** UUID generado al iniciar el borrador. */
    @PrimaryKey val id: String,
    /** ParteEnProgreso serializado a JSON. Es la fuente de verdad. */
    val parteJson: String,
    /** Timestamp de la última actualización (ms epoch). Para ordenar. */
    val fechaActualizacion: Long,
    /** Snapshot del progreso (0-100) para mostrar en la lista sin parsear el JSON. */
    val porcentajeCompletado: Int,
    /** Resumen del lugar para mostrar en la card (puede ser null). */
    val resumenLugar: String? = null,
    /** Resumen de la fecha del parte (texto, no timestamp) para la card. */
    val resumenFecha: String? = null
)

@Dao
interface BorradorParteDao {
    @Query("SELECT * FROM borradores_parte ORDER BY fechaActualizacion DESC")
    suspend fun getAll(): List<BorradorParteEntity>

    @Query("SELECT * FROM borradores_parte WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BorradorParteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(borrador: BorradorParteEntity)

    @Query("DELETE FROM borradores_parte WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM borradores_parte")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM borradores_parte")
    suspend fun count(): Int
}

// 3. LA BASE DE DATOS (El cerebro)
@Database(
    entities = [
        ChatMessageEntity::class,
        BorradorParteEntity::class,
        NotificacionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HukaRoomDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatMessageDao
    abstract fun borradorDao(): BorradorParteDao
    abstract fun notificacionDao(): NotificacionDao

    companion object {
        @Volatile
        private var INSTANCE: HukaRoomDatabase? = null

        fun getDatabase(context: Context): HukaRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HukaRoomDatabase::class.java,
                    "juka_chat_database"
                )
                    // En dev usamos destructive: si bumpeamos versión, se borran
                    // los datos locales (chat + borradores). Aceptable mientras
                    // el applicationId siga siendo "com.example.juka". Antes de
                    // ir a prod hay que escribir Migrations explícitas.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}