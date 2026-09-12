package com.example.juka.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.example.juka.R
import com.example.juka.data.encuesta.RespuestaPregunta
import com.example.juka.worker.SyncBorradoresWorker

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.juka.HukaApplication
import java.io.File

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(
        val user: FirebaseUser,
        val terminosAceptados: Boolean,
        val encuestaCompleta: Boolean
    ) : AuthState()
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
            Log.d(TAG, "✅ GoogleSignIn configurado")
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
            clientId
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo client ID: ${e.message}")
            null
        }
    }

    private fun checkAuthState() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            _authState.value = AuthState.Loading

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    val userDoc = withTimeoutOrNull(6_000) {
                        db.collection("users").document(currentUser.uid).get().await()
                    }

                    val terminosAceptados: Boolean
                    val encuestaCompleta: Boolean
                    when {
                        userDoc == null -> {
                            Log.w(TAG, "⚠️ Firestore timeout, entrando en modo offline")
                            terminosAceptados = true
                            encuestaCompleta = true
                        }
                        userDoc.exists() -> {
                            try {
                                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()?.let { token ->
                                    db.collection("users").document(currentUser.uid)
                                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                                }
                            } catch (_: Exception) {}
                            terminosAceptados = userDoc.getBoolean("terminosAceptados") ?: false
                            encuestaCompleta = userDoc.getBoolean("encuestaCompleta")
                                ?: userDoc.getBoolean("surveyCompleted")
                                ?: false
                        }
                        else -> {
                            try {
                                db.collection("users").document(currentUser.uid)
                                    .set(mapOf("encuestaCompleta" to false, "terminosAceptados" to false)).await()
                            } catch (_: Exception) {}
                            terminosAceptados = false
                            encuestaCompleta = false
                        }
                    }
                    _authState.value = AuthState.Authenticated(currentUser, terminosAceptados, encuestaCompleta)

                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error consultando Firestore: ${e.message}")
                    _authState.value = AuthState.Authenticated(currentUser, terminosAceptados = true, encuestaCompleta = true)
                }
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun getSignInIntent(): Intent? {
        return try {
            val client = googleSignInClient
            if (client == null) {
                initializeGoogleSignIn()
                return googleSignInClient?.signInIntent
            }
            client.signInIntent
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en getSignInIntent", e)
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
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = withTimeoutOrNull(15_000) {
                auth.signInWithCredential(credential).await()
            } ?: run {
                val msg = "Sin conexión o red muy lenta. Probá de nuevo cuando tengas mejor señal."
                val errorState = AuthState.Error(msg)
                _authState.value = errorState
                return errorState
            }

            val user = authResult.user
            if (user != null) {
                try {
                    val userDoc = withTimeoutOrNull(8_000) {
                        db.collection("users").document(user.uid).get().await()
                    }

                    var terminosAceptados = userDoc?.getBoolean("terminosAceptados") ?: false
                    var encuestaCompleta = userDoc?.getBoolean("encuestaCompleta")
                        ?: userDoc?.getBoolean("surveyCompleted")
                        ?: false

                    if (userDoc != null && !userDoc.exists()) {
                        withTimeoutOrNull(5_000) {
                            db.collection("users").document(user.uid)
                                .set(mapOf("encuestaCompleta" to false, "terminosAceptados" to false)).await()
                        }
                        terminosAceptados = false
                        encuestaCompleta = false
                    }
                    withTimeoutOrNull(5_000) {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()?.let { token ->
                            db.collection("users").document(user.uid)
                                .update("fcmToken", token)
                                .await()
                            Log.d(TAG, "✅ Token FCM guardado")
                        }
                    }
                    val authState = AuthState.Authenticated(user, terminosAceptados, encuestaCompleta)
                    _authState.value = authState
                    authState
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error Firestore, pero login exitoso: ${e.message}")
                    val authState = AuthState.Authenticated(user, terminosAceptados = false, encuestaCompleta = false)
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
            val errorState = AuthState.Error("Error de conexión: ${e.message}")
            _authState.value = errorState
            errorState
        }
    }

    suspend fun aceptarTerminos() {
        val user = auth.currentUser ?: return
        try {
            db.collection("users").document(user.uid)
                .set(mapOf("terminosAceptados" to true), SetOptions.merge()).await()
            val current = _authState.value
            if (current is AuthState.Authenticated) {
                _authState.value = current.copy(terminosAceptados = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando términos: ${e.message}")
        }
    }

    suspend fun markSurveyCompleted() {
        val user = auth.currentUser ?: return
        try {
            db.collection("users").document(user.uid)
                .set(
                    mapOf(
                        "encuestaCompleta" to true,
                        "fechaEncuesta" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            _authState.value = AuthState.Authenticated(user, terminosAceptados = true, encuestaCompleta = true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marcando survey: ${e.message}")
        }
    }

    /**
     * El cierre de sesión se serializa: primero se cancela el worker y se
     * limpian los datos del UID saliente; solo después se cierra Firebase.
     * Esto evita que un segundo usuario llegue a ver datos locales del primero.
     */
    fun signOut() {
        val departingUid = auth.currentUser?.uid
        _authState.value = AuthState.Loading

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val app = context.applicationContext as? HukaApplication
                if (app != null && !departingUid.isNullOrBlank()) {
                    WorkManager.getInstance(context)
                        .cancelUniqueWork(SyncBorradoresWorker.WORK_NAME)

                    withContext(Dispatchers.IO) {
                        app.roomDatabase.chatDao().clearHistoryForOwner(departingUid)
                        app.roomDatabase.borradorDao().deleteAllForOwner(departingUid)
                        app.roomDatabase.notificacionDao().deleteAllForOwner(departingUid)
                        app.roomDatabase.pescadexDao().deleteAllForOwner(departingUid)

                        app.localStorageHelper.clearContadorPeces()
                        app.localStorageHelper.clearAllPreferences()

                        File(context.filesDir, "captured_images").deleteRecursively()
                        File(context.cacheDir, "images").deleteRecursively()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error limpiando datos locales: ${e.message}")
            } finally {
                try {
                    auth.signOut()
                    googleSignInClient?.signOut()?.await()
                } catch (e: Exception) {
                    Log.w(TAG, "Cierre de Google Sign-In incompleto: ${e.message}")
                }
                _authState.value = AuthState.Unauthenticated
                Log.d(TAG, "🚪 Sesión cerrada y datos locales aislados")
            }
        }
    }

    suspend fun guardarEncuestaCompleta(respuestas: Map<Int, RespuestaPregunta>): Boolean {
        val user = auth.currentUser ?: return false

        try {
            val respuestasFirebase = respuestas.map { (preguntaId, respuesta) ->
                mapOf(
                    "preguntaId" to preguntaId,
                    "respuestaTexto" to respuesta.respuestaTexto,
                    "respuestaNumero" to respuesta.respuestaNumero,
                    "respuestaFecha" to respuesta.respuestaFecha,
                    "opcionSeleccionada" to respuesta.opcionSeleccionada,
                    "opcionesSeleccionadas" to respuesta.opcionesSeleccionadas,
                    "valorEscala" to respuesta.valorEscala,
                    "respuestaSiNo" to respuesta.respuestaSiNo,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
            }

            val encuestaData = mapOf(
                "userId" to user.uid,
                "completada" to true,
                "fechaCompletado" to FieldValue.serverTimestamp(),
                "respuestas" to respuestasFirebase,
                "totalPreguntas" to respuestas.size,
                "dispositivo" to android.os.Build.MODEL,
                "versionApp" to "1.0.0"
            )

            val ok = withTimeoutOrNull(15_000) {
                db.collection("users")
                    .document(user.uid)
                    .collection("encuestas")
                    .document("respuestas")
                    .set(encuestaData)
                    .await()

                db.collection("users")
                    .document(user.uid)
                    .set(
                        mapOf(
                            "encuestaCompleta" to true,
                            "fechaEncuesta" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                true
            }

            if (ok != true) {
                Log.w(TAG, "⚠️ Encuesta no se pudo guardar (timeout/sin red)")
                return false
            }

            _authState.value = AuthState.Authenticated(user, terminosAceptados = true, encuestaCompleta = true)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando encuesta: ${e.message}", e)
            return false
        }
    }

    suspend fun verificarEncuestaCompletada(): Boolean {
        val user = auth.currentUser ?: return false

        return try {
            val documento = db.collection("users")
                .document(user.uid)
                .get()
                .await()

            val encuestaCompleta = documento.getBoolean("encuestaCompleta") ?: false
            val terminosYaAceptados = documento.getBoolean("terminosAceptados") ?: false
            _authState.value = AuthState.Authenticated(user, terminosYaAceptados, encuestaCompleta)
            encuestaCompleta

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando encuesta: ${e.message}")
            false
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isAuthenticated(): Boolean = auth.currentUser != null
    fun getUserId(): String? = auth.currentUser?.uid
    fun getUserEmail(): String? = auth.currentUser?.email
    fun getUserName(): String? = auth.currentUser?.displayName
    fun getUserPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()
}
