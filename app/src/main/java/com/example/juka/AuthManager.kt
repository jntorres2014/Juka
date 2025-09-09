// AuthManager.kt - VERSIÓN CORREGIDA PARA COMPOSE
package com.example.juka

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private var googleSignInClient: GoogleSignInClient? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private const val TAG = "🔐 AuthManager"
        // ⚠️ CAMBIAR POR TU WEB_CLIENT_ID
        private const val WEB_CLIENT_ID = "976766461144-s5kbfljvrii2nik3rvkdcjerat64cpr0.apps.googleusercontent.com"
    }

    init {
        initializeGoogleSignIn()
        checkAuthState()
    }

    private fun initializeGoogleSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(context, gso)
            Log.d(TAG, "✅ GoogleSignIn configurado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando GoogleSignIn: ${e.message}")
        }
    }

    private fun checkAuthState() {
        val currentUser = auth.currentUser
        _authState.value = if (currentUser != null) {
            Log.d(TAG, "✅ Usuario logueado: ${currentUser.displayName}")
            AuthState.Authenticated(currentUser)
        } else {
            Log.d(TAG, "❌ No hay usuario logueado")
            AuthState.Unauthenticated
        }
    }

    // ✅ OBTENER INTENT PARA LOGIN
    fun getSignInIntent(): Intent? {
        return googleSignInClient?.signInIntent
    }

    // ✅ PROCESAR RESULTADO DEL LOGIN (CORREGIDO)
    suspend fun handleSignInResult(data: Intent?): AuthState {
        return try {
            // ✅ ACTUALIZAR ESTADO SOLO SI AUN ES VÁLIDO
            if (_authState.value is AuthState.Unauthenticated || _authState.value is AuthState.Loading) {
                _authState.value = AuthState.Loading
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                val errorState = AuthState.Error("Error obteniendo cuenta")
                // ✅ VERIFICAR ANTES DE ACTUALIZAR
                if (_authState.value is AuthState.Loading) {
                    _authState.value = errorState
                }
                errorState
            }

        } catch (e: ApiException) {
            Log.e(TAG, "❌ Error Google Sign-In: ${e.statusCode}")
            val errorMessage = when (e.statusCode) {
                12501 -> "Login cancelado"
                12502 -> "Error de red"
                10 -> "Configuración incorrecta - Verificar google-services.json"
                else -> "Error: ${e.message}"
            }
            val errorState = AuthState.Error(errorMessage)
            // ✅ VERIFICAR ANTES DE ACTUALIZAR
            if (_authState.value is AuthState.Loading) {
                _authState.value = errorState
            }
            errorState
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inesperado: ${e.message}")
            val errorState = AuthState.Error("Error inesperado: ${e.message}")
            if (_authState.value is AuthState.Loading) {
                _authState.value = errorState
            }
            errorState
        }
    }

    // ✅ FIREBASE AUTH CORREGIDO
    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): AuthState {
        return try {
            Log.d(TAG, "🔥 Autenticando con Firebase...")

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = auth.signInWithCredential(credential).await()

            val user = authResult.user
            if (user != null) {
                Log.i(TAG, "🎉 Login exitoso: ${user.displayName}")

                val authState = AuthState.Authenticated(user)
                // ✅ ACTUALIZAR ESTADO DE FORMA SEGURA
                try {
                    _authState.value = authState
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Warning actualizando estado (pero login exitoso): ${e.message}")
                    // No es crítico - el login fue exitoso
                }

                authState
            } else {
                val errorState = AuthState.Error("Error en Firebase")
                if (_authState.value is AuthState.Loading) {
                    _authState.value = errorState
                }
                errorState
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error Firebase: ${e.message}")
            val errorState = AuthState.Error("Error Firebase: ${e.message}")
            if (_authState.value is AuthState.Loading) {
                _authState.value = errorState
            }
            errorState
        }
    }

    // ✅ CERRAR SESIÓN
    fun signOut() {
        try {
            auth.signOut()
            googleSignInClient?.signOut()
            _authState.value = AuthState.Unauthenticated
            Log.d(TAG, "🚪 Sesión cerrada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cerrando sesión: ${e.message}")
        }
    }

    // ✅ GETTERS ÚTILES
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isAuthenticated(): Boolean = auth.currentUser != null
    fun getUserId(): String? = auth.currentUser?.uid
    fun getUserEmail(): String? = auth.currentUser?.email
    fun getUserName(): String? = auth.currentUser?.displayName
    fun getUserPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()
}