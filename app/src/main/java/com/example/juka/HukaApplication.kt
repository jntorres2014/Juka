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
        val defaultApp = FirebaseApp.initializeApp(this)
        if (defaultApp != null) {
            configureAppCheck(defaultApp, "principal")
        }

        // Segundo Firebase: exclusivamente para Firebase AI Logic.
        initializeSecondaryAiFirebase()

        org.osmdroid.config.Configuration.getInstance().userAgentValue =
            "Huka/1.0.2 (com.jonytorres.huka)"

        programarSyncBorradores()
    }

    private fun configureAppCheck(firebaseApp: FirebaseApp, label: String) {
        try {
            val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
            AppCheckProviderInstaller.install(appCheck)
            Log.d(TAG, "App Check configurado para Firebase $label")
        } catch (e: Exception) {
            Log.w(
                TAG,
                "No se pudo configurar App Check para Firebase $label [${e.javaClass.simpleName}]"
            )
        }
    }

    private fun initializeSecondaryAiFirebase() {
        if (
            BuildConfig.HUKA_AI_PROJECT_ID.isBlank() ||
            BuildConfig.HUKA_AI_APP_ID.isBlank() ||
            BuildConfig.HUKA_AI_API_KEY.isBlank()
        ) {
            Log.w(TAG, "Firebase secundario de IA no configurado")
            return
        }

        try {
            val existente = FirebaseApp.getApps(this)
                .firstOrNull { it.name == AI_FIREBASE_APP_NAME }

            val aiApp = existente ?: run {
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

            if (aiApp != null) {
                configureAppCheck(aiApp, "secundario de IA")
                Log.d(TAG, "Firebase secundario de IA inicializado")
            } else {
                Log.w(TAG, "Firebase secundario de IA devolvió instancia nula")
            }
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
