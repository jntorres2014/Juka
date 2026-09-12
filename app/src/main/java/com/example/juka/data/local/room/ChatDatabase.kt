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
    @ColumnInfo(defaultValue = "''") val ownerUid: String = currentOwnerUid()
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

    @Query("DELETE FROM chat_messages WHERE ownerUid = :ownerUid")
    abstract suspend fun clearHistoryForOwner(ownerUid: String)

    suspend fun clearHistory() {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) clearHistoryForOwner(uid)
    }
}

@Entity(tableName = "notificaciones")
data class NotificacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val cuerpo: String,
    val timestamp: Long,
    val leida: Boolean = false,
    val origen: String = "SISTEMA",
    @ColumnInfo(defaultValue = "''") val ownerUid: String = currentOwnerUid()
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

    @Query("DELETE FROM notificaciones WHERE ownerUid = :ownerUid")
    abstract suspend fun deleteAllForOwner(ownerUid: String)

    suspend fun deleteAll() {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) deleteAllForOwner(uid)
    }
}

@Entity(tableName = "borradores_parte")
data class BorradorParteEntity(
    @PrimaryKey val id: String,
    val parteJson: String,
    val fechaActualizacion: Long,
    val porcentajeCompletado: Int,
    val resumenLugar: String? = null,
    val resumenFecha: String? = null,
    @ColumnInfo(defaultValue = "''") val ownerUid: String = currentOwnerUid()
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

    @Query("DELETE FROM borradores_parte WHERE ownerUid = :ownerUid")
    abstract suspend fun deleteAllForOwner(ownerUid: String)

    suspend fun deleteAll() {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) deleteAllForOwner(uid)
    }

    @Query("SELECT COUNT(*) FROM borradores_parte WHERE ownerUid = :ownerUid")
    abstract suspend fun countForOwner(ownerUid: String): Int

    suspend fun count(): Int {
        val uid = currentOwnerUid()
        return if (uid.isBlank()) 0 else countForOwner(uid)
    }
}

@Entity(
    tableName = "pescadex_records",
    primaryKeys = ["especieId", "ownerUid"]
)
data class PescadexRecordEntity(
    val especieId: String,
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
    @ColumnInfo(defaultValue = "''") val ownerUid: String = currentOwnerUid()
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

    @Query("DELETE FROM pescadex_records WHERE ownerUid = :ownerUid")
    abstract suspend fun deleteAllForOwner(ownerUid: String)

    suspend fun deleteAll() {
        val uid = currentOwnerUid()
        if (uid.isNotBlank()) deleteAllForOwner(uid)
    }
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE borradores_parte ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notificaciones ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")

                db.execSQL("DELETE FROM chat_messages WHERE ownerUid = ''")
                db.execSQL("DELETE FROM borradores_parte WHERE ownerUid = ''")
                db.execSQL("DELETE FROM notificaciones WHERE ownerUid = ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pescadex_records_new (
                        especieId TEXT NOT NULL,
                        nombreComun TEXT NOT NULL,
                        nombreCientifico TEXT NOT NULL,
                        totalCapturas INTEGER NOT NULL,
                        pesoRecord REAL,
                        primeraFoto TEXT,
                        fechaDescubrimiento INTEGER,
                        mejorDiaCantidad INTEGER NOT NULL,
                        mejorDiaFecha TEXT,
                        rareza TEXT NOT NULL,
                        locacionesRaw TEXT NOT NULL,
                        ownerUid TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(especieId, ownerUid)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE pescadex_records")
                db.execSQL("ALTER TABLE pescadex_records_new RENAME TO pescadex_records")
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
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
