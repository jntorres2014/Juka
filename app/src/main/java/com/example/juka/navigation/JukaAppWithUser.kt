package com.example.juka.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juka.data.Achievement
import com.example.juka.data.AuthManager
import com.example.juka.ui.theme.chat.EnhancedChatScreen
import com.example.juka.identificar.IdentificarPezScreen
import com.example.juka.reportes.MisReportesScreenMejorado
import com.example.juka.auth.SimpleProfileScreen
import com.example.juka.ui.CrearTorneoScreen
import com.example.juka.ui.PartesTorneoScreen
import com.example.juka.ui.UnirseATorneoScreen
import com.example.juka.ui.theme.FishCounterScreen
import com.example.juka.ui.theme.logros.AchievementUnlockedPopup
import com.example.juka.ui.theme.logros.AchievementsScreen
import com.example.juka.ui.theme.navigation.Screen
import com.example.juka.ui.torneos.TorneosScreen
import com.example.juka.ui.wizard.ParteWizardScreen
import com.example.juka.viewmodel.AppViewModelProvider
import com.example.juka.viewmodel.EnhancedChatViewModel
import com.example.juka.viewmodel.TorneosViewModel
import com.google.firebase.auth.FirebaseUser

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JukaAppWithUser(user: FirebaseUser, authManager: AuthManager) {
    val navController = rememberNavController()
    val sharedViewModel: EnhancedChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val torneosViewModel: TorneosViewModel = viewModel()
    // ✅ NUEVO: estado del popup de logros
    var pendingAchievement by remember { mutableStateOf<Achievement?>(null) }

    // ✅ NUEVO: colectar el flow de logros desbloqueados
    LaunchedEffect("achievements") {
        sharedViewModel.newAchievementUnlocked.collect { achievement ->
            pendingAchievement = achievement
        }
    }
    LaunchedEffect("partes") {
        sharedViewModel.parteSavedEvent.collect { evento ->
            evento?.let { (parteId, parteData) ->
                torneosViewModel.onParteSaved(parteId, parteData)
            }
        }
    }

    val screens = listOf(
        Screen.Chat,
        Screen.Contador,
        Screen.Identificar,
        Screen.Reportes,
        Screen.Profile,
        Screen.Torneos,
    )

    // ✅ Box exterior para que el popup pueda superponerse sobre todo
    Box(modifier = Modifier.fillMaxSize()) {

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
                composable(Screen.Chat.route) {
                    EnhancedChatScreen(
                        user = user,
                        viewModel = sharedViewModel,
                        onNavigateToWizard = { navController.navigate(Screen.Wizard.route) }
                    )
                }

                composable(Screen.Contador.route) {
                    FishCounterScreen(
                        viewModel = sharedViewModel,
                        onNavigateToChat = {
                            sharedViewModel.iniciarParteDesdeContador()
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
                        navController = navController
                    )
                }

                composable("achievements_screen") {
                    AchievementsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Wizard.route) {
                    ParteWizardScreen(
                        viewModel = sharedViewModel,
                        onFinished = {
                            navController.navigate(Screen.Chat.route) {
                                popUpTo(Screen.Chat.route) { inclusive = true }
                            }
                        }
                    )
                }
                // ── Torneos ───────────────────────────────────────────────────

                composable(Screen.Torneos.route) {
                    TorneosScreen(
                        viewModel = torneosViewModel,
                        onCrearTorneo = { navController.navigate("crear_torneo") },
                        onUnirse = { navController.navigate("unirse_torneo") },
                        onVerPartes = { torneoId -> navController.navigate("partes_torneo/$torneoId") }  // ✅ AGREGAR
                    )
                }

                composable("crear_torneo") {
                    CrearTorneoScreen(
                        viewModel = torneosViewModel,       // ✅ misma instancia
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("unirse_torneo") {
                    UnirseATorneoScreen(
                        viewModel = torneosViewModel,       // ✅ misma instancia
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("partes_torneo/{torneoId}") { backStackEntry ->
                    val torneoId = backStackEntry.arguments?.getString("torneoId") ?: return@composable
                    // Pasamos solo el id: la screen collecta el state del VM
                    // y se reactiva sola cuando cambian los datos (rechazo,
                    // refresh, etc.) sin necesidad de salir y volver a entrar.
                    PartesTorneoScreen(
                        torneoId = torneoId,
                        viewModel = torneosViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }


        // ✅ NUEVO: mostrar popup cuando hay un logro pendiente
        // Se superpone sobre todo el contenido, incluida la bottom bar
        pendingAchievement?.let { achievement ->
            AchievementUnlockedPopup(
                achievement = achievement,
                onDismiss = { pendingAchievement = null }
            )
        }


    }

}