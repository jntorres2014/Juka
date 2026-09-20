package com.example.juka.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.juka.HukaApplication
import com.example.juka.PescadexManager
import com.example.juka.RegistroResult
import com.example.juka.data.Achievement
import com.example.juka.data.AuthManager
import com.example.juka.identificar.IdentificarPezScreen
import com.example.juka.reportes.MisReportesScreenMejorado
import com.example.juka.auth.SimpleProfileScreen
import com.example.juka.ui.CrearTorneoScreen
import com.example.juka.ui.PartesTorneoScreen
import com.example.juka.ui.UnirseATorneoScreen
import com.example.juka.ui.navigation.HukaNavigationDrawer
import com.example.juka.ui.network.NetworkBanner
import com.example.juka.ui.notificaciones.CampanaIcon
import com.example.juka.ui.notificaciones.NotificacionesScreen
import com.example.juka.ui.theme.FishCounterScreen
import com.example.juka.ui.theme.CelebracionNuevaEspecieModal
import com.example.juka.ui.theme.PescadexScreen
import com.example.juka.ui.theme.chat.ChatMenuScreen
import com.example.juka.ui.theme.chat.EnhancedChatScreen
import com.example.juka.ui.theme.logros.AchievementUnlockedPopup
import com.example.juka.ui.theme.logros.AchievementsScreen
import com.example.juka.ui.theme.navigation.Screen
import com.example.juka.ui.torneos.TorneosScreen
import com.example.juka.ui.tutorial.TutorialPreferences
import com.example.juka.ui.wizard.ParteWizardScreen
import com.example.juka.viewmodel.AppViewModelProvider
import com.example.juka.viewmodel.EnhancedChatViewModel
import com.example.juka.viewmodel.TorneosViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HukaAppWithUser(user: FirebaseUser, authManager: AuthManager) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sharedViewModel: EnhancedChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val torneosViewModel: TorneosViewModel = viewModel()
    val networkMonitor = (context.applicationContext as HukaApplication).networkMonitor
    val storage = (context.applicationContext as HukaApplication).localStorageHelper
    val tutorialPreferences = remember(context) {
        TutorialPreferences(context.applicationContext)
    }
    var tutorialStep by rememberSaveable {
        mutableIntStateOf(if (tutorialPreferences.hasSeenMainTutorial()) -1 else 0)
    }
    var pendingAchievement by remember { mutableStateOf<Achievement?>(null) }
    // Cola de modales de "Nueva especie en tu Pescadex". Si un parte aporta
    // más de una especie nueva, se muestran en secuencia: cuando el usuario
    // cierra un modal, se abre el siguiente.
    var pilaCelebraciones by remember { mutableStateOf<List<RegistroResult.Success>>(emptyList()) }

    // Estado del drawer + scope para abrirlo/cerrarlo con animación
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect("achievements") {
        sharedViewModel.newAchievementUnlocked.collect { achievement ->
            pendingAchievement = achievement
            // Persistir el logro en el historial local para que aparezca en
            // la campanita. Best-effort: si falla no bloquea el popup.
            storage.guardarNotificacion(
                titulo = "🏆 Nuevo logro desbloqueado",
                cuerpo = achievement.title,
                origen = "LOGRO"
            )
        }
    }
    LaunchedEffect("partes") {
        sharedViewModel.parteSavedEvent.collect { evento ->
            evento?.let { (parteId, parteData) ->
                torneosViewModel.onParteSaved(parteId, parteData)
            }
        }
    }
    // Cuando un parte revela especies nuevas para el Pescadex, las metemos
    // en la pila de celebraciones. El modal de abajo las consume de a una.
    LaunchedEffect("nuevas_especies_pescadex") {
        sharedViewModel.nuevasEspeciesEvent.collect { nuevas ->
            if (nuevas.isNotEmpty()) {
                pilaCelebraciones = pilaCelebraciones + nuevas
            }
        }
    }

    // Ruta actual para destacar el item activo del drawer y decidir
    // si renderizamos el TopAppBar global
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun cerrarDrawer() = scope.launch { drawerState.close() }
    fun abrirDrawer() = scope.launch { drawerState.open() }

    fun finalizarTutorial() {
        tutorialPreferences.markMainTutorialSeen()
        tutorialStep = -1
        cerrarDrawer()
    }

    fun siguienteTutorial() {
        when (tutorialStep) {
            0 -> tutorialStep = 1
            1 -> {
                tutorialStep = 2
                abrirDrawer()
            }
            in 2..4 -> tutorialStep += 1
            5 -> finalizarTutorial()
        }
    }

    fun omitirTutorial() {
        finalizarTutorial()
    }

    fun repetirTutorial() {
        tutorialPreferences.resetMainTutorial()
        cerrarDrawer()
        navController.navigate(Screen.ChatMenu.route) {
            launchSingleTop = true
        }
        tutorialStep = 0
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Solo permitimos el gesto cuando el drawer YA está abierto (para
        // cerrarlo deslizando). Con el drawer cerrado, el deslizar-desde-el-borde
        // queda deshabilitado, así no choca con el paneo del mapa. Para abrirlo
        // se usa el botón ☰ del TopAppBar.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HukaNavigationDrawer(
                user = user,
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    cerrarDrawer()
                    // Navegamos al destino del drawer. saveState/restoreState
                    // preserva el estado de cada pestaña al volver.
                    navController.navigate(screen.route) {
                        popUpTo(Screen.ChatMenu.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCloseDrawer = { cerrarDrawer() },
                onSignOut = {
                    cerrarDrawer()
                    authManager.signOut()
                },
                tutorialStep = tutorialStep,
                onTutorialNext = { siguienteTutorial() },
                onTutorialSkip = { omitirTutorial() },
                onReplayTutorial = { repetirTutorial() }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Scaffold(
                topBar = {
                    // Banner global de conectividad + TopAppBar (condicional)
                    NetworkBanner(monitor = networkMonitor)
                    if (mostrarTopBarGlobal(currentRoute)) {
                        TopAppBar(
                            title = { Text(tituloPorRuta(currentRoute)) },
                            navigationIcon = {
                                IconButton(onClick = { abrirDrawer() }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Abrir menú")
                                }
                            },
                            actions = {
                                // Campana 🔔 con badge de no-leídas. Tap → pantalla
                                // de notificaciones.
                                CampanaIcon(
                                    onClick = {
                                        navController.navigate(Screen.Notificaciones.route)
                                    }
                                )
                            }
                        )
                    }
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.ChatMenu.route,
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable(Screen.ChatMenu.route) {
                        ChatMenuScreen(
                            user = user,
                            onConsultar = { navController.navigate(Screen.Chat.route) },
                            onOpenDrawer = { abrirDrawer() },
                            onOpenNotificaciones = {
                                navController.navigate(Screen.Notificaciones.route)
                            },
                            tutorialStep = tutorialStep,
                            onTutorialNext = { siguienteTutorial() },
                            onTutorialSkip = { omitirTutorial() }
                        )
                    }

                    composable(Screen.Chat.route) {
                        EnhancedChatScreen(
                            user = user,
                            viewModel = sharedViewModel,
                            onNavigateToWizard = { navController.navigate(Screen.Wizard.route) },
                            onOpenDrawer = { abrirDrawer() },
                            onOpenNotificaciones = {
                                navController.navigate(Screen.Notificaciones.route)
                            },
                            onBackToMenu = {
                                navController.navigate(Screen.ChatMenu.route) {
                                    popUpTo(Screen.Chat.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Notificaciones.route) {
                        NotificacionesScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Pescadex.route) {
                        PescadexScreen()
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
                        IdentificarPezScreen(navController = navController)
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

                    composable(Screen.Logros.route) {
                        AchievementsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        Screen.Wizard.routeWithArgs,
                        arguments = listOf(
                            navArgument(Screen.Wizard.ARG_FOTO_URI) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument(Screen.Wizard.ARG_ESPECIES) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val fotoUriArg = backStackEntry.arguments?.getString(Screen.Wizard.ARG_FOTO_URI)
                        val especiesArg = backStackEntry.arguments?.getString(Screen.Wizard.ARG_ESPECIES)
                        // "nombre:cantidad|nombre:cantidad" → List<EspecieCapturada>
                        val especiesIniciales = especiesArg
                            ?.split("|")
                            ?.mapNotNull { item ->
                                val partes = item.split(":")
                                val nombre = partes.getOrNull(0)?.trim().orEmpty()
                                val cant = partes.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                if (nombre.isBlank()) null
                                else com.example.juka.domain.model.EspecieCapturada(
                                    nombre = nombre,
                                    numeroEjemplares = cant,
                                    numeroRetenidos = cant
                                )
                            }
                            ?: emptyList()
                        ParteWizardScreen(
                            viewModel = sharedViewModel,
                            fotoInicialUri = fotoUriArg?.let { Uri.parse(it) },
                            especiesIniciales = especiesIniciales,
                            onFinished = {
                                navController.navigate(Screen.Chat.route) {
                                    popUpTo(Screen.Chat.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    // ── Torneos ───────────────────────────────────────────

                    composable(Screen.Torneos.route) {
                        TorneosScreen(
                            viewModel = torneosViewModel,
                            onCrearTorneo = { navController.navigate("crear_torneo") },
                            onUnirse = { navController.navigate("unirse_torneo") },
                            onVerPartes = { torneoId -> navController.navigate("partes_torneo/$torneoId") }
                        )
                    }

                    composable("crear_torneo") {
                        CrearTorneoScreen(
                            viewModel = torneosViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("unirse_torneo") {
                        UnirseATorneoScreen(
                            viewModel = torneosViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("partes_torneo/{torneoId}") { backStackEntry ->
                        val torneoId = backStackEntry.arguments?.getString("torneoId") ?: return@composable
                        PartesTorneoScreen(
                            torneoId = torneoId,
                            viewModel = torneosViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Popup de logro desbloqueado: superpuesto sobre todo
            pendingAchievement?.let { achievement ->
                AchievementUnlockedPopup(
                    achievement = achievement,
                    onDismiss = { pendingAchievement = null }
                )
            }

            // Modal de "Nueva especie en tu Pescadex". Se dispara automáticamente
            // al guardar un parte que aporta especies nuevas. Si hubo varias, se
            // muestran de a una: al cerrar la actual, aparece la siguiente.
            pilaCelebraciones.firstOrNull()?.let { resultado ->
                CelebracionNuevaEspecieModal(
                    resultado = resultado,
                    onDismiss = { pilaCelebraciones = pilaCelebraciones.drop(1) }
                )
            }
        }
    }
}

/**
 * Decide si la ruta actual debe mostrar el TopAppBar global con el botón
 * de menú. Las pantallas que tienen su propio TopAppBar (wizard, sub-pantallas
 * de torneos) no lo necesitan. El chat tampoco — tiene su `EnhancedChatHeader`
 * propio y recibe el callback de abrir drawer.
 */
private fun mostrarTopBarGlobal(route: String?): Boolean = when (route) {
    Screen.Pescadex.route,
    Screen.Contador.route,
    Screen.Identificar.route,
    Screen.Reportes.route,
    Screen.Profile.route,
    Screen.Torneos.route,
    Screen.Logros.route -> true
    else -> false
}

/** Título a mostrar en el TopAppBar global por ruta. */
private fun tituloPorRuta(route: String?): String = when (route) {
    Screen.Pescadex.route -> "Pescadex"
    Screen.Contador.route -> "Contador de peces"
    Screen.Identificar.route -> "Identificar pez"
    Screen.Reportes.route -> "Mis reportes"
    Screen.Profile.route -> "Mi perfil"
    Screen.Torneos.route -> "Torneos"
    Screen.Logros.route -> "Mis logros"
    else -> "Huka"
}
