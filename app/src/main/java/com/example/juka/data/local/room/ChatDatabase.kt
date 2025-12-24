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

// 3. LA BASE DE DATOS (El cerebro)
@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = false)
abstract class JukaRoomDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: JukaRoomDatabase? = null

        fun getDatabase(context: Context): JukaRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JukaRoomDatabase::class.java,
                    "juka_chat_database"
                )
                    .fallbackToDestructiveMigration() // Si cambias la estructura, borra y crea de nuevo
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}