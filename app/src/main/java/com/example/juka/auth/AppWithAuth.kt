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

    // Control de navegación basado en el estado de autenticación
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                // ✅ Al navegar a la App principal, eliminamos el Login y la Encuesta del historial
                navController.navigate(AuthRoute.MainApp.route) {
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

            composable(AuthRoute.Encuesta.route) {
                // Si necesitás que desde aquí se navegue al finalizar,
                // asegurate que el botón de 'Finalizar' llame a una función que
                // actualice el estado en el AuthManager.
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