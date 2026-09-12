package com.example.juka

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.juka.data.AuthManager
import com.example.juka.data.ChatBotActionHandler
import com.example.juka.data.ChatBotManager
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.data.network.NetworkMonitor
import com.example.juka.data.repository.ChatRepository
import com.example.juka.data.repository.FishingRepository
import com.example.juka.domain.chat.ChatQuotaManager
import com.example.juka.worker.SyncBorradoresWorker
import GeminiChatService
import com.example.juka.data.local.room.HukaRoomDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HukaApplication : Application() {

    // Monitor de conectividad: singleton del proceso. Se inicializa
    // al primer acceso y vive todo el ciclo de la app.
    val networkMonitor by lazy { NetworkMonitor(this) }

    // Base de datos Room
    val roomDatabase by lazy { HukaRoomDatabase.getDatabase(this) }
    val localStorageHelper by lazy {
        LocalStorageHelper(
            context = applicationContext,
            chatDao = roomDatabase.chatDao(),
            borradorDao = roomDatabase.borradorDao(),
            notificacionDao = roomDatabase.notificacionDao(),
            pescadexDao = roomDatabase.pescadexDao()
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
        // User-Agent propio para los mapas (osmdroid). OSM devuelve 403
        // ("Access blocked") con el User-Agent por defecto ("osmdroid") o con
        // paquetes "com.example.*". Con uno único que identifique la app, no bloquea.
        org.osmdroid.config.Configuration.getInstance().userAgentValue = "Huka/1.0.2 (com.jonytorres.huka)"
        programarSyncBorradores()
    }

    /**
     * Programa un job de sincronización de borradores pendientes.
     * WorkManager lo ejecuta en cuanto el dispositivo tiene internet.
     * Si no hay borradores, el worker termina en milisegundos sin impacto.
     * ExistingWorkPolicy.KEEP evita encolar el mismo job varias veces.
     */
    fun programarSyncBorradores() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncBorradoresWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            SyncBorradoresWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}