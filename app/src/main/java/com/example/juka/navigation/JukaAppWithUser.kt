package com.example.juka.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juka.data.AuthManager
import com.example.juka.EnhancedChatScreen
import com.example.juka.identificar.IdentificarPezScreen
import com.example.juka.reportes.MisReportesScreenMejorado
import com.example.juka.Screen
import com.example.juka.auth.SimpleProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JukaAppWithUser(
    user: com.google.firebase.auth.FirebaseUser,
    authManager: AuthManager
) {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Chat,        // Tu pantalla principal
        Screen.Identificar, // Tu pantalla de identificar
        Screen.Reportes,    // Tu pantalla de reportes
        Screen.Profile      // Nueva pantalla de perfil
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            /*composable(Screen.Chat.route) {
                // ✅ TU CHATSCREEN ACTUAL (agregamos usuario)
                ChatScreenWithUser(user = user)
            }
            composable(Screen.Chat.route) {
                ChatScreenSimplificado(user = user) // ← Versión fácil
            }*/
            composable(Screen.Chat.route) {
                EnhancedChatScreen(user = user) // ← Versión completa
            }
            composable(Screen.Identificar.route) {
                // ✅ TU PANTALLA ACTUAL SIN CAMBIOS
                IdentificarPezScreen()
            }

            composable(Screen.Reportes.route) {
                // ✅ TU PANTALLA ACTUAL SIN CAMBIOS
                // MisReportesScreen()
                MisReportesScreenMejorado()
            }

            composable(Screen.Profile.route) {
                // ✅ NUEVA PANTALLA DE PERFIL SIMPLE
                SimpleProfileScreen(user = user, authManager = authManager)
            }
        }
    }
}