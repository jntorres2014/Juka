package com.example.juka.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.AuthManager
import kotlinx.coroutines.launch

class LoginViewModel(private val authManager: AuthManager) : ViewModel() {

    fun handleSignInResult(intent: Intent?) {
        // viewModelScope SOBREVIVE a la navegación.
        // Si LoginScreen muere, esta corrutina sigue viva hasta terminar.
        viewModelScope.launch {
            try {
                authManager.handleSignInResult(intent)
            } catch (e: Exception) {
                // Aquí podrías manejar errores globales si quisieras
            }
        }
    }
}