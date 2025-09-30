package com.example.juka.features.main.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "Login", Icons.Default.Person)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object IdentificarPez : Screen("identificar_pez", "Identificar Pez", Icons.Default.Search)
    object MisReportes : Screen("mis_reportes", "Mis Reportes", Icons.Default.Summarize)
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    object Identificar: Screen("identificar", "Identificar", Icons.Default.Search)
    object Reportes: Screen("reportes", "Reportes", Icons.Default.Summarize)

}
