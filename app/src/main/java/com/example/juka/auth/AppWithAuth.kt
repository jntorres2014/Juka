package com.example.juka.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.juka.data.AuthManager
import com.example.juka.data.AuthState
import com.example.juka.navigation.ChatScreenWithUser
import com.example.juka.navigation.JukaAppWithUser


@Composable
fun AppWithAuth(authManager: AuthManager = AuthManager(LocalContext.current)) {
    val navController = rememberNavController()
    val authState by authManager.authState.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("login") {
            LoginScreen(authManager, navController)
        }
        composable("chat") { EncuestaScreen(authManager, navController) } // Asegúrate de que esto exista
        composable("encuesta") {
            EncuestaScreen(authManager, navController)
        }

        composable("home") {
            when (authState) {
                is AuthState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Verificando usuario...")
                        }
                    }
                }

                is AuthState.Authenticated -> {
                    // ✅ USAR JukaAppWithUser en lugar de EnhancedChatScreen directo
                    JukaAppWithUser(
                        user = (authState as AuthState.Authenticated).user,
                        authManager = authManager
                    )
                }

                is AuthState.Unauthenticated -> {
                    LaunchedEffect(Unit) {
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Redirigiendo al login...")
                    }
                }

                is AuthState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error: ${(authState as AuthState.Error).message}",
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            ) {
                                Text("Ir al Login")
                            }
                        }
                    }
                }
            }
        }

        // ✅ ELIMINAR estas rutas ya que ahora están dentro de JukaAppWithUser
        // composable(Screen.Chat.route) { ... }
        // composable(Screen.Identificar.route) { ... }
        // composable(Screen.Reportes.route) { ... }
        // composable(Screen.Profile.route) { ... }
    }
}