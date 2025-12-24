package com.example.juka

import android.app.Application
import com.example.juka.data.AuthManager
import com.example.juka.data.ChatBotActionHandler
import com.example.juka.data.ChatBotManager
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.data.repository.ChatRepository
import com.example.juka.data.repository.FishingRepository
import com.example.juka.domain.chat.ChatQuotaManager
import GeminiChatService // Asegurate de importar esto
import com.example.juka.data.local.room.JukaRoomDatabase

class JukaApplication : Application() {

    // Base de datos (Room)
    val roomDatabase by lazy { JukaRoomDatabase.getDatabase(this) }
    val localStorageHelper by lazy {
        LocalStorageHelper(context = applicationContext, // 👈 Ahora pide esto
            chatDao = roomDatabase.chatDao())
    }
    val fishDatabase by lazy { FishDatabase(this) }

    // Firebase y Auth
    val firebaseManager by lazy { FirebaseManager(this) }
    val authManager by lazy { AuthManager(this) }

    // Helpers Locales


    // Repositorios
    val chatRepository by lazy {
        ChatRepository(firebaseManager, localStorageHelper)
    }
    val fishingRepository by lazy {
        FishingRepository(firebaseManager)
    }

    // ✅ NUEVOS INGREDIENTES PARA EL CHAT MEJORADO
    val geminiService by lazy { GeminiChatService() }
    val mlKitManager by lazy { MLKitManager(this) }
    val chatQuotaManager by lazy { ChatQuotaManager(this) }
    val chatBotManager by lazy { ChatBotManager(this) }

    // El ActionHandler necesita contexto
    val chatBotActionHandler by lazy { ChatBotActionHandler(this) }

    override fun onCreate() {
        super.onCreate()
    }
}