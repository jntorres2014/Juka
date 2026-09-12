package com.example.juka.data.local.room

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth

private fun currentOwnerUid(): String =
    FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isFromUser: Boolean,
    val type: String,
    val timestamp: String,
    val ownerUid: String = currentOwnerUid()
)

@Dao
abstract class ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE ownerUid = :ownerUid ORDER BY id ASC")
    abstract suspend fun getAllMessagesForOwner(ownerUid: String): List<ChatMessageEntity>

    suspend fun getAllMessages(): List<ChatMessageEntity> {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) emptyList() else getAllMessagesForOwner(uid)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    abstract suspend fun clearHistory()
}

@Entity(tableName = "notificaciones")
data class NotificacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val cuerpo: String,
    val timestamp: Long,
    val leida: Boolean = false,
    val origen: String = "SISTEMA",
    val ownerUid: String = currentOwnerUid()
)

@Dao
abstract class NotificacionDao {
    @Query("SELECT * FROM notificaciones WHERE ownerUid = :ownerUid ORDER BY timestamp DESC")
    abstract suspend fun getAllForOwner(ownerUid: String): List<NotificacionEntity>

    suspend fun getAll(): List<NotificacionEntity> {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) emptyList() else getAllForOwner(uid)
    }

    @Query("SELECT COUNT(*) FROM notificaciones WHERE ownerUid = :ownerUid AND leida = 0")
    abstract suspend fun countUnreadForOwner(ownerUid: String): Int

    suspend fun countUnread(): Int {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) 0 else countUnreadForOwner(uid)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(notificacion: NotificacionEntity): Long

    @Query("UPDATE notificaciones SET leida = 1 WHERE ownerUid = :ownerUid")
    abstract suspend fun markAllReadForOwner(ownerUid: String)

    suspend fun markAllRead() {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) markAllReadForOwner(uid)
    }

    @Query("DELETE FROM notificaciones WHERE id = :id AND ownerUid = :ownerUid")
    abstract suspend fun deleteByIdForOwner(id: Long, ownerUid: String)

    suspend fun deleteById(id: Long) {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) deleteByIdForOwner(id, uid)
    }

    @Query("DELETE FROM notificaciones")
    abstract suspend fun deleteAll()
}

@Entity(tableName = "borradores_parte")
data class BorradorParteEntity(
    @PrimaryKey val id: String,
    val parteJson: String,
    val fechaActualizacion: Long,
    val porcentajeCompletado: Int,
    val resumenLugar: String? = null,
    val resumenFecha: String? = null,
    val ownerUid: String = currentOwnerUid()
)

@Dao
abstract class BorradorParteDao {
    @Query("SELECT * FROM borradores_parte WHERE ownerUid = :ownerUid ORDER BY fechaActualizacion DESC")
    abstract suspend fun getAllForOwner(ownerUid: String): List<BorradorParteEntity>

    suspend fun getAll(): List<BorradorParteEntity> {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) emptyList() else getAllForOwner(uid)
    }

    @Query("SELECT * FROM borradores_parte WHERE id = :id AND ownerUid = :ownerUid LIMIT 1")
    abstract suspend fun getByIdForOwner(id: String, ownerUid: String): BorradorParteEntity?

    suspend fun getById(id: String): BorradorParteEntity? {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) null else getByIdForOwner(id, uid)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(borrador: BorradorParteEntity)

    @Query("DELETE FROM borradores_parte WHERE id = :id AND ownerUid = :ownerUid")
    abstract suspend fun deleteByIdForOwner(id: String, ownerUid: String)

    suspend fun deleteById(id: String) {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) deleteByIdForOwner(id, uid)
    }

    @Query("DELETE FROM borradores_parte")
    abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM borradores_parte WHERE ownerUid = :ownerUid")
    abstract suspend fun countForOwner(ownerUid: String): Int

    suspend fun count(): Int {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) 0 else countForOwner(uid)
    }
}

@Entity(tableName = "pescadex_records")
data class PescadexRecordEntity(
    @PrimaryKey val especieId: String,
    val nombreComun: String,
    val nombreCientifico: String = "",
    val totalCapturas: Int,
    val pesoRecord: Double?,
    val primeraFoto: String?,
    val fechaDescubrimiento: Long?,
    val mejorDiaCantidad: Int,
    val mejorDiaFecha: String?,
    val rareza: String = "comun",
    val locacionesRaw: String = "",
    val ownerUid: String = currentOwnerUid()
)

@Dao
abstract class PescadexRecordDao {
    @Query("SELECT * FROM pescadex_records WHERE ownerUid = :ownerUid")
    abstract suspend fun getAllForOwner(ownerUid: String): List<PescadexRecordEntity>

    suspend fun getAll(): List<PescadexRecordEntity> {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) emptyList() else getAllForOwner(uid)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(records: List<PescadexRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(record: PescadexRecordEntity)

    @Query("DELETE FROM pescadex_records")
    abstract suspend fun deleteAll()
}

@Database(
    entities = [
        ChatMessageEntity::class,
        BorradorParteEntity::class,
        NotificacionEntity::class,
        PescadexRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class HukaRoomDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatMessageDao
    abstract fun borradorDao(): BorradorParteDao
    abstract fun notificacionDao(): NotificacionDao
    abstract fun pescadexDao(): PescadexRecordDao

    companion object {
        @Volatile
        private var INSTANCE: HukaRoomDatabase? = null

        /**
         * v4 -> v5: agrega ownerUid a todos los datos locales asociados a una
         * sesión. Los registros viejos no tienen un propietario verificable;
         * se eliminan una única vez para evitar que puedan aparecer bajo una
         * cuenta diferente en un dispositivo compartido.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE borradores_parte ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notificaciones ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pescadex_records ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")

                db.execSQL("DELETE FROM chat_messages WHERE ownerUid = ''")
                db.execSQL("DELETE FROM borradores_parte WHERE ownerUid = ''")
                db.execSQL("DELETE FROM notificaciones WHERE ownerUid = ''")
                db.execSQL("DELETE FROM pescadex_records WHERE ownerUid = ''")
            }
        }

        fun getDatabase(context: Context): HukaRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HukaRoomDatabase::class.java,
                    "juka_chat_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    // Versiones muy antiguas no tenían migraciones versionadas.
                    // El camino normal v4->v5 ya es no destructivo.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
