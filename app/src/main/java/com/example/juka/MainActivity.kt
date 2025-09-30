// MainActivity.kt - MODIFICACIÓN MÍNIMA CON AUTH
package com.example.juka

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.auth.AppWithAuth
import com.example.juka.chat.AnalyzingIndicator
import com.example.juka.chat.MessageBubble
import com.example.juka.chat.TypingIndicator
import com.example.juka.ui.theme.HukaTheme
import com.example.juka.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    private val RECORD_AUDIO_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        setContent {
            HukaTheme {
                // ✅ NUEVA ESTRUCTURA CON AUTH
                AppWithAuth()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Identificar : Screen("identificar", "Identificar", Icons.Default.PhotoCamera)
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    object Encuesta : Screen("encuesta", "Encuesta", Icons.Default.Assignment)
    object Reportes : Screen("reportes", "Reportes", Icons.Default.Book)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person) // ✅ NUEVA
}




