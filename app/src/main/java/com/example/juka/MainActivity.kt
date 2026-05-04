package com.example.juka

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.juka.auth.AppWithAuth
import com.example.juka.ui.theme.HukaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private val RECORD_AUDIO_PERMISSION = 1001

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase (Verificar si es estrictamente necesario según tu build.gradle)
        FirebaseApp.initializeApp(this)

        // ✅ Movido fuera de setContent para evitar que se ejecute en cada recomposición de la UI
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d("FCM_TOKEN", "🔑 Token: $token")
        }

        // TODO: A futuro, mover esta petición a la pantalla específica que usa el micrófono
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
}