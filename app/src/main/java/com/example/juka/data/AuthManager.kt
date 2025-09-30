
package com.example.juka.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AuthManager {
    private val auth = Firebase.auth

    fun getCurrentUser() = auth.currentUser
}
