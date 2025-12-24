package com.example.juka.ui.theme.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de nivel superior (Flujo de Autenticación)
 */
sealed class AuthRoute(val route: String) {
    object Login : AuthRoute("login_screen")
    object Encuesta : AuthRoute("encuesta_screen")
    object MainApp : AuthRoute("main_app_root") // Cuando ya entró a la app
}

/**
 * Rutas de la navegación principal (Bottom Navigation)
 * Movemos esto aquí desde MainActivity para limpiar esa clase.
 */
sealed class AppScreen(val route: String, val title: String, val icon: ImageVector) {
    object Identificar : AppScreen("identificar", "Identificar", Icons.Default.PhotoCamera)
    object Chat : AppScreen("chat", "Chat", Icons.Default.Chat)
    object Reportes : AppScreen("reportes", "Reportes", Icons.Default.Book)
    object Profile : AppScreen("profile", "Perfil", Icons.Default.Person)

    // Función helper para iterar en el BottomBar
    companion object {
        val bottomNavItems = listOf(Identificar, Chat, Reportes, Profile)
    }
}