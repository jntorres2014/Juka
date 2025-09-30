package com.example.juka.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.R
import com.example.juka.data.AuthManager
import com.example.juka.data.AuthState
import androidx.navigation.NavController
import com.example.juka.Screen
import kotlinx.coroutines.launch

// 2. CORRIGE LoginScreen.kt
@Composable
fun LoginScreen(
    authManager: AuthManager,
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val authState by authManager.authState.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                authManager.handleSignInResult(result.data)
            } catch (e: Exception) {
                Log.e("LOGIN", "Error handling sign in result", e)
                isLoading = false
                errorMessage = "Error procesando login: ${e.message}"
            }
        }
    }

    LaunchedEffect(authState) {
        Log.d("LOGIN_DEBUG", "AuthState: ${authState::class.simpleName}")
        when (authState) {
            is AuthState.Authenticated -> {
                isLoading = false
                errorMessage = null

                // ✅ SIEMPRE ir a "home" ya que no hay survey
                Log.d("LOGIN_DEBUG", "Navegando a home")
                navController.navigate(route = "chat") {
                    popUpTo("login") { inclusive = true }
                }
            }
            is AuthState.Error -> {
                isLoading = false
                errorMessage = (authState as AuthState.Error).message
                Log.e("LOGIN_DEBUG", "Auth error: ${(authState as AuthState.Error).message}")
            }
            is AuthState.Loading -> {
                isLoading = true
                errorMessage = null
            }
            is AuthState.Unauthenticated -> {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E3A8A)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logohuka1),
                    contentDescription = "Logo Huka",
                    modifier = Modifier.size(120.dp) // Reducido para mejor rendimiento
                )
                Text(
                    text = "Huka",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
                Text(
                    text = "HukaApp",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Error de login",
                                    color = Color.Red,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = error,
                                    color = Color.Red,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        Log.d("LOGIN_DEBUG", "Botón presionado")
                        scope.launch {
                            try {
                                isLoading = true
                                errorMessage = null

                                val intent = authManager.getSignInIntent()
                                if (intent != null) {
                                    signInLauncher.launch(intent)
                                } else {
                                    isLoading = false
                                    errorMessage = "Error configurando login"
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Continuar con Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Inicia sesión para guardar tus datos",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}