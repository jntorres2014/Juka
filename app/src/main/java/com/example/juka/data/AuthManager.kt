package com.example.juka.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.juka.R
import com.example.juka.data.encuesta.RespuestaPregunta

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    /**
     * `encuestaCompleta` indica si el usuario ya completó la encuesta inicial.
     * - false → la UI debe mandarlo a `EncuestaScreen` antes de entrar a la app.
     * - true  → ya la completó, va directo al home.
     *
     * Nombre canónico unificado (antes coexistían `surveyCompleted` en inglés
     * y `encuestaCompletada` con doble 'd'). Se mantiene `surveyCompleted`
     * en Firestore como fallback de LECTURA para docs viejos, pero las
     * escrituras nuevas usan siempre `encuestaCompleta`.
     */
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
            _authState.value = AuthState.Loading

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    // Timeout de 6s: si no hay red, Firestore puede quedar
                    // esperando indefinidamente y la app se congela en "Verificando sesión".
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
                    // Si auth.currentUser no es null, el usuario ya se registró antes
                    // → dejarlo entrar directamente en vez de mostrar la encuesta sin red.
                    _authState.value = AuthState.Authenticated(currentUser, terminosAceptados = true, encuestaCompleta = true)
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
            // Timeout: si la red está muy mala, queremos fallar limpio en
            // 15s en lugar de dejar el spinner colgado indefinidamente.
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
                Log.i(TAG, "🎉 Login exitoso: ${user.displayName}")

                // Pasos secundarios (Firestore + FCM) son "best effort" —
                // si fallan, el usuario igual queda autenticado.
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
                    // Si Firestore falla NO asumimos que la encuesta está
                    // completa (eso le ocultaría la encuesta a un usuario que
                    // no debía saltearla). Mejor mostrarla — si era una falla
                    // transitoria, al reintentar va a leer el flag real.
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
            Log.d(TAG, "✅ Términos aceptados guardados en Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando términos: ${e.message}")
        }
    }


    suspend fun markSurveyCompleted() {
        val user = auth.currentUser ?: return
        try {
            // Usamos set(..., merge) en vez de update() para que no truene
            // con NOT_FOUND si el doc padre todavía no existe (race entre
            // creación inicial post-login y el primer guardado de encuesta).
            db.collection("users").document(user.uid)
                .set(
                    mapOf(
                        "encuestaCompleta" to true,
                        "fechaEncuesta" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            _authState.value = AuthState.Authenticated(user, terminosAceptados = true, encuestaCompleta = true)
            Log.d(TAG, "✅ Usuario marcado como encuesta completada: ${user.displayName}")
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

    suspend fun guardarEncuestaCompleta(respuestas: Map<Int, RespuestaPregunta>): Boolean {
        val user = auth.currentUser ?: return false

        try {
            Log.d(TAG, "📋 Guardando encuesta para usuario: ${user.uid}")

            // Convertir respuestas a formato Firebase
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

            // Datos completos de la encuesta
            val encuestaData = mapOf(
                "userId" to user.uid,
                "completada" to true,
                "fechaCompletado" to FieldValue.serverTimestamp(),
                "respuestas" to respuestasFirebase,
                "totalPreguntas" to respuestas.size,
                "dispositivo" to android.os.Build.MODEL,
                "versionApp" to "1.0.0"
            )

            // Guardar encuesta + marcar usuario, con timeout para que el
            // botón "Enviar encuesta" no quede esperando para siempre si no
            // hay señal. Si falla, devolvemos false y el caller decide UX
            // (toast/retry/etc.).
            //
            // OJO: la segunda escritura usa set(..., merge) en vez de update()
            // para no tirar NOT_FOUND si por algún race el doc padre del user
            // todavía no existe.
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

            // Actualizar estado de autenticación
            _authState.value = AuthState.Authenticated(user, terminosAceptados = true, encuestaCompleta = true)

            Log.d(TAG, "✅ Encuesta guardada exitosamente para ${user.displayName}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando encuesta: ${e.message}", e)
            return false
        }
    }
    // Verifica si el usuario ya completó la encuesta (al iniciar sesión)
    suspend fun verificarEncuestaCompletada(): Boolean {
        val user = auth.currentUser ?: return false

        return try {
            val documento = db.collection("users")
                .document(user.uid)
                .get()
                .await()

            val encuestaCompleta = documento.getBoolean("encuestaCompleta") ?: false
            val terminosYaAceptados = documento.getBoolean("terminosAceptados") ?: false

            Log.d(TAG, "📋 Encuesta completada: $encuestaCompleta para ${user.displayName}")

            // Actualizar estado
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
