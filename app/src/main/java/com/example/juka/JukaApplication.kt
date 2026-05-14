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
import GeminiChatService
import com.example.juka.data.local.room.JukaRoomDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class JukaApplication : Application() {

    // Base de datos Room
    val roomDatabase by lazy { JukaRoomDatabase.getDatabase(this) }
    val localStorageHelper by lazy {
        LocalStorageHelper(
            context = applicationContext,
            chatDao = roomDatabase.chatDao(),
            borradorDao = roomDatabase.borradorDao()
        )
    }

    // ✅ Instancia única de FishDatabase — se pasa a quienes la necesiten
    val fishDatabase by lazy { FishDatabase(this) }

    // Firebase y Auth
    val firebaseManager by lazy { FirebaseManager(this) }
    val authManager by lazy { AuthManager(this) }

    // Repositorios
    val chatRepository by lazy { ChatRepository(firebaseManager, localStorageHelper) }
    val fishingRepository by lazy { FishingRepository(firebaseManager) }

    // Servicios
    val geminiService by lazy { GeminiChatService() }

    // ✅ mlKitManager recibe la fishDatabase ya creada — no crea una nueva
    val mlKitManager by lazy { MLKitManager(this, fishDatabase) }

    val chatQuotaManager by lazy {
        ChatQuotaManager(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
    }
    val chatBotManager by lazy { ChatBotManager(this) }
    val chatBotActionHandler by lazy { ChatBotActionHandler(this) }

    override fun onCreate() {
        super.onCreate()
    }
}