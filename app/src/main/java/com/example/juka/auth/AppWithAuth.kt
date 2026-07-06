package com.example.juka.auth

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.juka.HukaApplication
import com.example.juka.data.AuthManager
import com.example.juka.data.AuthState
import com.example.juka.navigation.HukaAppWithUser
import com.example.juka.ui.theme.navigation.AuthRoute

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppWithAuth() {
    val context = LocalContext.current
    val authManager = (context.applicationContext as HukaApplication).authManager
    val navController = rememberNavController()
    val authState by authManager.authState.collectAsState()

    // Flujo de auth: Login → Términos (si no aceptó) → Encuesta (si no completó) → App
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> {
                val destino = when {
                    !state.terminosAceptados -> AuthRoute.Terminos.route
                    !state.encuestaCompleta  -> AuthRoute.Encuesta.route
                    else                     -> AuthRoute.MainApp.route
                }
                navController.navigate(destino) {
                    popUpTo(AuthRoute.Login.route) { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                navController.navigate(AuthRoute.Login.route) {
                    popUpTo(AuthRoute.MainApp.route) { inclusive = true }
                }
            }
            else -> {} // Loading: no hacemos nada todavía
        }
    }

    // ✅ Evitamos el "fogonazo" del Login mostrando Loading primero
    if (authState is AuthState.Loading) {
        LoadingScreen("Verificando sesión...")
    } else {
        NavHost(
            navController = navController,
            startDestination = AuthRoute.Login.route
        ) {
            composable(AuthRoute.Login.route) {
                LoginScreen(authManager, navController)
            }

            composable(AuthRoute.Terminos.route) {
                AceptarTerminosScreen(
                    authManager = authManager,
                    onTerminosAceptados = {
                        val state = authManager.authState.value
                        val destino = if (state is AuthState.Authenticated && !state.encuestaCompleta) {
                            AuthRoute.Encuesta.route
                        } else {
                            AuthRoute.MainApp.route
                        }
                        navController.navigate(destino) {
                            popUpTo(AuthRoute.Terminos.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(AuthRoute.Encuesta.route) {
                EncuestaScreen(authManager = authManager, navController = navController)
            }

            composable(AuthRoute.MainApp.route) {
                val user = (authState as? AuthState.Authenticated)?.user
                if (user != null) {
                    HukaAppWithUser(user = user, authManager = authManager)
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text)
        }
    }
}