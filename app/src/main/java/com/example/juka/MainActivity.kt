package com.example.juka

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.juka.auth.AppWithAuth
import com.example.juka.service.HukaNotifications
import com.example.juka.ui.theme.HukaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private val RECORD_AUDIO_PERMISSION = 1001

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
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        // 1. Crear el canal de notificaciones al arrancar (idempotente).
        //    Así existe siempre, sin depender de que el servicio FCM
        //    haya recibido un push antes.
        HukaNotifications.crearCanal(this)

        // 2. Pedir POST_NOTIFICATIONS en runtime (Android 13+). Sin este
        //    permiso las notificaciones NO se muestran al usuario aunque
        //    estén declaradas en el manifest.
        pedirPermisoNotificacionesSiHaceFalta()

        // 3. Obtener el token FCM y PERSISTIRLO en Firestore (no solo
        //    loguearlo). Si el usuario ya está logueado, esto garantiza
        //    que su fcmToken esté actualizado en cada arranque, incluso si
        //    Firebase lo rotó silenciosamente.
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM_TOKEN", "🔑 Token: ${token.take(20)}...")
                persistirTokenSiHayUsuario(token)
            }
            .addOnFailureListener { e ->
                Log.w("FCM_TOKEN", "⚠️ No se pudo obtener el token: ${e.message}")
            }

        // 4. Permiso de audio (igual que antes).
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        setContent {
            HukaTheme {
                AppWithAuth()
            }
        }
    }

    private fun pedirPermisoNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val concedido = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedido) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
