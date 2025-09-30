package com.example.juka.common.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.juka.core.logic.IntelligentResponseGenerator
import com.example.juka.core.nlp.MLKitManager
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.repositories.ChatRepository
import com.example.juka.data.repositories.FishRepository
import com.example.juka.data.repositories.ReportesRepository
import com.example.juka.database.FishDatabase
import com.example.juka.features.chat.ChatViewModel
import com.example.juka.features.reportes.PartesConChatViewModel

class ViewModelFactory(
    private val context: Context,
    private val firebaseManager: FirebaseManager,
    private val mlKitManager: MLKitManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                val fishDatabase = FishDatabase.getDatabase(context).fishDao()
                val fishRepository = FishRepository(fishDatabase)
                val chatRepository = ChatRepository(firebaseManager)
                val intelligentResponseGenerator = IntelligentResponseGenerator(fishRepository)
                ChatViewModel(chatRepository, fishRepository, intelligentResponseGenerator) as T
            }
            modelClass.isAssignableFrom(PartesConChatViewModel::class.java) -> {
                val reportesRepository = ReportesRepository(firebaseManager)
                PartesConChatViewModel(reportesRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
