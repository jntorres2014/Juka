package com.example.juka.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.juka.JukaApplication
import com.example.juka.data.AchievementsViewModel
import com.example.juka.data.firebase.StorageService
import com.example.juka.data.local.ImageHelper
import com.example.juka.domain.usecase.FishingDataExtractor
import com.example.juka.domain.usecase.IntelligentResponses
import com.example.juka.domain.usecase.ParteLogicUseCase
import com.example.juka.usecase.SendMessageUseCase
import com.example.juka.data.FishCounterManager

object AppViewModelProvider {
    val Factory = viewModelFactory {

        // 1. ChatViewModel
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as JukaApplication)
            val repository = application.chatRepository
            val fishDatabase = application.fishDatabase

            val intelligentResponses = IntelligentResponses(fishDatabase)
            val dataExtractor = FishingDataExtractor(application)
            val sendMessageUseCase = SendMessageUseCase(repository, intelligentResponses, dataExtractor)

            ChatViewModel(
                chatRepository = repository,
                sendMessageUseCase = sendMessageUseCase
            )
        }

        // 2. LoginViewModel
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as JukaApplication)
            LoginViewModel(application.authManager)
        }

        // 3. ReportesViewModel
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as JukaApplication)
            ReportesViewModel(app.fishingRepository)
        }

        // 4. AchievementsViewModel — instancia propia gestionada por Jetpack
        initializer {
            AchievementsViewModel()
        }

        // 5. EnhancedChatViewModel — recibe AchievementsViewModel inyectado
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as JukaApplication)

            val parteLogicUseCase = ParteLogicUseCase(app.mlKitManager)
            val imageHelper = ImageHelper(app.applicationContext)
            val storageService = StorageService()
            val fishCounterManager = FishCounterManager(app.localStorageHelper)

            // ✅ Se crea via factory — lifecycle-aware y compartido correctamente
            val achievementsViewModel = AchievementsViewModel()

            EnhancedChatViewModel(
                quotaManager = app.chatQuotaManager,
                geminiService = app.geminiService,
                mlKitManager = app.mlKitManager,
                firebaseManager = app.firebaseManager,
                chatBotManager = app.chatBotManager,
                chatBotActionHandler = app.chatBotActionHandler,
                localStorageHelper = app.localStorageHelper,
                fishDatabase = app.fishDatabase,
                parteLogicUseCase = parteLogicUseCase,
                imageHelper = imageHelper,
                storageService = storageService,
                fishCounterManager = fishCounterManager,
                achievementsViewModel = achievementsViewModel  // ✅ inyectado
            )
        }
    }
}