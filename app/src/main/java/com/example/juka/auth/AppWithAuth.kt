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
import com.example.juka.JukaApplication
import com.example.juka.data.AuthManager
import com.example.juka.data.AuthState

import com.example.juka.navigation.JukaAppWithUser
import com.example.juka.ui.theme.logros.AchievementsScreen
import com.example.juka.ui.theme.navigation.AuthRoute

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppWithAuth() {
    // Nota: Usamos 'remember' para no recrear el Manager en recomposiciones accidentales si no usas DI.
    val context = LocalContext.current
    //val authManager = remember { AuthManager(context) }
    val authManager = (context.applicationContext as JukaApplication).authManager
    val navController = rememberNavController()
    val authState by authManager.authState.collectAsState()

    // Observar el estado de Auth para navegar automáticamente
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> {
                if (state.surveyCompleted) {
                    navController.navigate(AuthRoute.MainApp.route) {
                        popUpTo(AuthRoute.Login.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(AuthRoute.Encuesta.route) {
                        popUpTo(AuthRoute.Login.route) { inclusive = true }
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                navController.navigate(AuthRoute.Login.route) {
                    popUpTo(0) { inclusive = true } // Limpia todo el stack
                }
            }
            else -> { /* Loading o Error se manejan en la UI */ }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthRoute.Login.route // Empezamos asumiendo Login, el LaunchedEffect redirige si hace falta
    ) {
        composable(AuthRoute.Login.route) {
            // Si el estado es Loading, mostramos loading encima del login o una pantalla splash
            if (authState is AuthState.Loading) {
                LoadingScreen("Iniciando sesión...")
            } else {
                LoginScreen(authManager, navController)
            }
        }

        composable(AuthRoute.Encuesta.route) {
            EncuestaScreen(authManager = authManager, navController = navController)
        }

        composable(AuthRoute.MainApp.route) {
            // Aquí cargamos la UI principal que tiene su PROPIO NavHost interno (BottomBar)
            val user = (authState as? AuthState.Authenticated)?.user
            if (user != null) {
                JukaAppWithUser(user = user, authManager = authManager)
            } else {
                // Fallback por seguridad
                LoadingScreen("Cargando perfil...")
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