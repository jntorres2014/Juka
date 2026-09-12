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
import com.example.juka.data.local.room.HukaRoomDatabase
import com.example.juka.data.network.NetworkMonitor
import com.example.juka.data.repository.ChatRepository
import com.example.juka.data.repository.FishingRepository
import com.example.juka.domain.chat.ChatQuotaManager
import com.example.juka.worker.SyncBorradoresWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import GeminiChatService

class HukaApplication : Application() {

    val networkMonitor by lazy { NetworkMonitor(this) }

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

    val fishDatabase by lazy { FishDatabase(this) }

    val firebaseManager by lazy { FirebaseManager(this) }
    val authManager by lazy { AuthManager(this) }

    val chatRepository by lazy { ChatRepository(firebaseManager, localStorageHelper) }
    val fishingRepository by lazy { FishingRepository(firebaseManager) }

    val geminiService by lazy { GeminiChatService() }

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

        // App Check debe instalarse antes de que la aplicación empiece a usar
        // servicios Firebase. En desarrollo se usa el proveedor debug; el APK
        // release usa Play Integrity.
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        org.osmdroid.config.Configuration.getInstance().userAgentValue =
            "Huka/1.0.2 (com.jonytorres.huka)"

        programarSyncBorradores()
    }

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