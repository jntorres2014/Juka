/*
// ViewModelFactory.kt - Para inyectar FirebaseManager en ViewModels
package com.example.juka.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.viewmodels.ChatHistoryViewModel
import com.example.juka.viewmodels.PartesConChatViewModel
//import com.example.juka.viewmodels.SesionesPendientesViewModel

class ViewModelFactory(
    private val firebaseManager: FirebaseManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatHistoryViewModel::class.java) -> {
                ChatHistoryViewModel(firebaseManager) as T
            }
            modelClass.isAssignableFrom(PartesConChatViewModel::class.java) -> {
                PartesConChatViewModel(firebaseManager) as T
            }
            */
/*modelClass.isAssignableFrom(SesionesPendientesViewModel::class.java) -> {
                SesionesPendientesViewModel(firebaseManager) as T
            }*//*

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}*/
