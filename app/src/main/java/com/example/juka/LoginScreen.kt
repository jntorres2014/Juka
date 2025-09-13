// LoginScreen.kt - VERSIÓN CORREGIDA SIN CONFLICTOS
package com.example.juka

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
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val authState by authManager.authState.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ✅ LAUNCHER PARA GOOGLE SIGN-IN (SIN MANEJAR ESTADOS AQUÍ)
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            authManager.handleSignInResult(result.data)
            // No manejar estados aquí - dejar que LaunchedEffect lo haga
        }
    }

    // ✅ MANEJAR ESTADOS SOLO AQUÍ
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                isLoading = false
                errorMessage = null
                // Delay corto para evitar el error de corrutinas
                kotlinx.coroutines.delay(500)
                onLoginSuccess()
            }
            is AuthState.Error -> {
                isLoading = false
                errorMessage = (authState as AuthState.Error).message
                // Limpiar el error después de mostrarlo
                kotlinx.coroutines.delay(5000)
                errorMessage = null
            }
            is AuthState.Loading -> {
                isLoading = true
                errorMessage = null
            }
            is AuthState.Unauthenticated -> {
                isLoading = false
                // No limpiar errorMessage aquí para que se mantenga visible
            }
        }
    }

    // ✅ UI SÚPER SIMPLE
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E3A8A)), // Azul
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
                    modifier = Modifier.size(200.dp)
                )

                // Título
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

                // ✅ ERROR MESSAGE CON AUTO-DISMISS
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

                // ✅ BOTÓN DE LOGIN
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        val intent = authManager.getSignInIntent()
                        if (intent != null) {
                            signInLauncher.launch(intent)
                        } else {
                            isLoading = false
                            errorMessage = "Error configurando login"
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

                // ✅ BOTÓN DE REINTENTAR SI HAY ERROR
                if (errorMessage != null && !isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { errorMessage = null }
                    ) {
                        Text(
                            "Reintentar",
                            color = Color(0xFF1E3A8A)
                        )
                    }
                }
            }
        }
    }
}