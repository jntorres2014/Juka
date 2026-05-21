package com.example.juka.ui.theme.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Identificar : Screen("identificar", "Identificar", Icons.Default.PhotoCamera)
    object Chat : Screen("chat", "Chat Huka", Icons.Default.Chat)
    object Pescadex : Screen("pescadex", "Pescadex", Icons.Default.Book)

    object Contador : Screen("fish_counter", "Contador", Icons.Default.Add)
    object Reportes : Screen("reportes", "Reportes", Icons.Default.Assignment)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object Torneos : Screen("torneos", "Torneos", Icons.Default.EmojiEvents)
    object Logros : Screen("achievements_screen", "Logros", Icons.Default.EmojiEvents)
    object Notificaciones : Screen("notificaciones", "Notificaciones", Icons.Default.EmojiEvents)

    object Wizard : Screen("parte_wizard", "Crear parte", Icons.Default.EditNote)
}




