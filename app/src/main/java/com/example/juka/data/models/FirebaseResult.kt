package com.example.juka.data.models

sealed class FirebaseResult {
    object Success : FirebaseResult()
    data class Error(val message: String, val exception: Exception? = null) : FirebaseResult()
    object Loading : FirebaseResult()
}