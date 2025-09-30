package com.example.juka.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.juka.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser, val surveyCompleted: Boolean) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var googleSignInClient: GoogleSignInClient? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private const val TAG = "🔐 AuthManager"
    }

    init {
        initializeGoogleSignIn()
        // ✅ DESCOMENTAR esto para que verifique el usuario actual
        checkAuthState()
    }

    private fun initializeGoogleSignIn() {
        try {
            val clientId = getClientId()
            if (clientId == null) {
                Log.e(TAG, "❌ No se pudo obtener el Client ID")
                return
            }

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(context, gso)
            Log.d(TAG, "✅ GoogleSignIn configurado con client ID: ${clientId.take(20)}...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando GoogleSignIn: ${e.message}")
        }
    }

    private fun getClientId(): String? {
        return try {
            val clientId = context.getString(R.string.default_web_client_id)

            if (clientId.isEmpty() || clientId == "your_web_client_id") {
                Log.e(TAG, "❌ Client ID no configurado en strings.xml")
                return null
            }

            Log.d(TAG, "✅ Client ID obtenido: ${clientId.take(20)}...")
            clientId
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo client ID: ${e.message}")
            null
        }
    }

    // ✅ HACER esta función NO suspendida para llamarla desde init
    private fun checkAuthState() {
        val currentUser = auth.currentUser
        Log.d(TAG, "🔍 Verificando estado de auth. Usuario actual: ${currentUser?.displayName}")

        if (currentUser != null) {
            Log.d(TAG, "✅ Usuario encontrado: ${currentUser.displayName}")
            // Marcar como autenticado inmediatamente, Firestore después
            _authState.value = AuthState.Authenticated(currentUser, true)

            // Verificar Firestore en background
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    val userDoc = db.collection("users").document(currentUser.uid).get().await()
                    val surveyCompleted = userDoc.getBoolean("surveyCompleted") ?: false
                    if (!userDoc.exists()) {
                        db.collection("users").document(currentUser.uid)
                            .set(mapOf("surveyCompleted" to true)).await()
                    }
                    _authState.value = AuthState.Authenticated(currentUser, true)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error consultando Firestore, pero usuario válido: ${e.message}")
                    // Mantener el usuario autenticado aunque Firestore falle
                }
            }
        } else {
            Log.d(TAG, "❌ No hay usuario logueado")
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun getSignInIntent(): Intent? {
        Log.d("AUTH_DEBUG", "=== INICIANDO getSignInIntent ===")

        return try {
            val client = googleSignInClient
            if (client == null) {
                Log.e("AUTH_DEBUG", "❌ GoogleSignInClient no inicializado")
                initializeGoogleSignIn()
                return googleSignInClient?.signInIntent
            }

            val intent = client.signInIntent
            Log.d("AUTH_DEBUG", "✅ Intent obtenido: ${intent != null}")
            intent

        } catch (e: Exception) {
            Log.e("AUTH_DEBUG", "❌ Error en getSignInIntent", e)
            null
        }
    }

    suspend fun handleSignInResult(data: Intent?): AuthState {
        return try {
            _authState.value = AuthState.Loading

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                val errorState = AuthState.Error("Error obteniendo cuenta")
                _authState.value = errorState
                errorState
            }

        } catch (e: ApiException) {
            Log.e(TAG, "❌ Error Google Sign-In: ${e.statusCode}")
            val errorMessage = when (e.statusCode) {
                12501 -> "Login cancelado"
                12502 -> "Error de red"
                10 -> "Configuración incorrecta - Verificar SHA-1 y Client ID"
                else -> "Error: ${e.message}"
            }
            val errorState = AuthState.Error(errorMessage)
            _authState.value = errorState
            errorState
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inesperado: ${e.message}")
            val errorState = AuthState.Error("Error inesperado: ${e.message}")
            _authState.value = errorState
            errorState
        }
    }

    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): AuthState {
        return try {
            Log.d(TAG, "🔥 Autenticando con Firebase...")

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = auth.signInWithCredential(credential).await()

            val user = authResult.user
            if (user != null) {
                Log.i(TAG, "🎉 Login exitoso: ${user.displayName}")

                try {
                    val userDoc = db.collection("users").document(user.uid).get().await()
                    var surveyCompleted = userDoc.getBoolean("surveyCompleted") ?: false

                    if (!userDoc.exists()) {
                        db.collection("users").document(user.uid)
                            .set(mapOf("surveyCompleted" to true)).await()
                        surveyCompleted = true
                    }

                    val authState = AuthState.Authenticated(user, surveyCompleted)
                    _authState.value = authState
                    authState
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error Firestore, pero login exitoso: ${e.message}")
                    val authState = AuthState.Authenticated(user, true)
                    _authState.value = authState
                    authState
                }
            } else {
                val errorState = AuthState.Error("Error en Firebase")
                _authState.value = errorState
                errorState
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error Firebase: ${e.message}")
            val errorState = AuthState.Error("Error Firebase: ${e.message}")
            _authState.value = errorState
            errorState
        }
    }

    suspend fun markSurveyCompleted() {
        val user = auth.currentUser ?: return
        try {
            db.collection("users").document(user.uid)
                .update("surveyCompleted", true).await()
            _authState.value = AuthState.Authenticated(user, true)
            Log.d(TAG, "✅ Usuario marcado como survey completado: ${user.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marcando survey: ${e.message}")
        }
    }

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

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isAuthenticated(): Boolean = auth.currentUser != null
    fun getUserId(): String? = auth.currentUser?.uid
    fun getUserEmail(): String? = auth.currentUser?.email
    fun getUserName(): String? = auth.currentUser?.displayName
    fun getUserPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()
}
