package com.example.juka

import android.app.Application
import android.util.Log
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
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import GeminiChatService

class HukaApplication : Application() {

    companion object {
        const val AI_FIREBASE_APP_NAME = "HUKA_AI_FREE"
        private const val TAG = "HukaApplication"
    }

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

        // Firebase principal: Auth / Firestore / Storage / FCM.
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

        // Segundo Firebase: exclusivamente para la prueba de Firebase AI Logic.
        // Si falta configuración, Huka sigue funcionando y el Chat conserva su
        // fallback al SDK directo anterior.
        initializeSecondaryAiFirebase()

        org.osmdroid.config.Configuration.getInstance().userAgentValue =
            "Huka/1.0.2 (com.jonytorres.huka)"

        programarSyncBorradores()
    }

    private fun initializeSecondaryAiFirebase() {
        if (
            BuildConfig.HUKA_AI_PROJECT_ID.isBlank() ||
            BuildConfig.HUKA_AI_APP_ID.isBlank() ||
            BuildConfig.HUKA_AI_API_KEY.isBlank()
        ) {
            Log.w(TAG, "Firebase secundario de IA no configurado; se usará fallback")
            return
        }

        try {
            val existente = FirebaseApp.getApps(this)
                .firstOrNull { it.name == AI_FIREBASE_APP_NAME }

            if (existente == null) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.HUKA_AI_PROJECT_ID)
                    .setApplicationId(BuildConfig.HUKA_AI_APP_ID)
                    .setApiKey(BuildConfig.HUKA_AI_API_KEY)
                    .build()

                FirebaseApp.initializeApp(
                    this,
                    options,
                    AI_FIREBASE_APP_NAME
                )
            }

            Log.d(TAG, "Firebase secundario de IA inicializado")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo inicializar Firebase secundario de IA [${e.javaClass.simpleName}]")
        }
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
