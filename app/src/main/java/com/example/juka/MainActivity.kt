package com.example.juka

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.juka.auth.AppWithAuth
import com.example.juka.service.HukaNotifications
import com.example.juka.ui.theme.HukaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    /**
     * Launcher para pedir POST_NOTIFICATIONS (Android 13+). En versiones
     * anteriores el permiso se concede automáticamente por declaración
     * en el manifest, no hace falta runtime request.
     */
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            Log.d("PERMS", "✅ POST_NOTIFICATIONS concedido")
        } else {
            Log.w("PERMS", "⚠️ POST_NOTIFICATIONS denegado — las notificaciones no se mostrarán")
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        // In-App Messaging sigue habilitado, pero no exponemos el Installation ID en Logcat.
        FirebaseInAppMessaging.getInstance().isAutomaticDataCollectionEnabled = true

        // 1. Crear el canal de notificaciones al arrancar (idempotente).
        HukaNotifications.crearCanal(this)

        // 2. Pedir POST_NOTIFICATIONS en runtime (Android 13+).
        pedirPermisoNotificacionesSiHaceFalta()

        // 3. Obtener el token FCM y persistirlo sin registrar su contenido.
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM_TOKEN", "Token FCM obtenido correctamente")
                persistirTokenSiHayUsuario(token)
            }
            .addOnFailureListener { e ->
                Log.w("FCM_TOKEN", "⚠️ No se pudo obtener el token: ${e.message}")
            }

        // RECORD_AUDIO se solicita únicamente cuando el usuario usa la función de voz.
        setContent {
            HukaTheme {
                AppWithAuth()
            }
        }
    }

    private fun pedirPermisoNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val concedido = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedido) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun persistirTokenSiHayUsuario(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w("FCM_TOKEN", "⚠️ No se persistió el token en arranque: ${e.message}")
            }
    }
}
