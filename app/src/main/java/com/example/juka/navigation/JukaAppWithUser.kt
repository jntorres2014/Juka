package com.example.juka.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel // 👈 Importar esto
import com.example.juka.data.AuthManager
import com.example.juka.ui.theme.chat.EnhancedChatScreen
import com.example.juka.identificar.IdentificarPezScreen
import com.example.juka.reportes.MisReportesScreenMejorado
import com.example.juka.auth.SimpleProfileScreen
import com.example.juka.ui.theme.FishCounterScreen
import com.example.juka.ui.theme.logros.AchievementsScreen
import com.example.juka.ui.theme.navigation.Screen
import com.example.juka.viewmodel.AppViewModelProvider
import com.example.juka.viewmodel.EnhancedChatViewModel
import com.google.firebase.auth.FirebaseUser

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JukaAppWithUser(user: FirebaseUser, authManager: AuthManager) {
    val navController = rememberNavController()
    // 1. EL CEREBRO COMPARTIDO (Importante)
    val sharedViewModel: EnhancedChatViewModel = viewModel(factory = AppViewModelProvider.Factory)

    // 2. LA LISTA DE BOTONES
    val screens = listOf(
        Screen.Chat,
        Screen.Contador, // 👈 ¡AQUÍ LO AGREGAS!
        Screen.Identificar,
        Screen.Reportes,
        Screen.Profile
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
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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

            // PANTALLA DE CHAT
            composable(Screen.Chat.route) {
                EnhancedChatScreen(
                    user = user,
                    viewModel = sharedViewModel, // 👈 Le pasamos el compartido

                )
            }
            // ✅ AGREGA ESTE BLOQUE AQUÍ:
            composable(Screen.Contador.route) {
                FishCounterScreen(
                    viewModel = sharedViewModel,
                    onNavigateToChat = {
                        // 1. Preparamos el parte con los peces contados
                        sharedViewModel.iniciarParteDesdeContador()
                        // 2. Volvemos al chat
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Identificar.route) {
                IdentificarPezScreen()
            }

            composable(Screen.Reportes.route) {
                MisReportesScreenMejorado()
            }

            composable(Screen.Profile.route) {
                SimpleProfileScreen(
                    user = user,
                    authManager = authManager,
                    navController = navController // 👈 Le pasamos el navController para que pueda navegar
                )
            }
            composable("achievements_screen") {
                AchievementsScreen(
                    onBack = { navController.popBackStack() } // 👈 Para que el botón volver funcione
                )
            }
        }
    }

}