package com.example.juka.ui.theme.navigation

import android.net.Uri
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
    // Pantalla de entrada: tarjetas para elegir "hacer una consulta" (→ Chat)
    // o accesos directos (mareas, info adicional). Antes esto era el primer
    // mensaje del bot adentro del chat, mezclado con el input de texto.
    object ChatMenu : Screen("chat_menu", "Chat Huka", Icons.Default.Chat)
    object Chat : Screen("chat", "Chat Huka", Icons.Default.Chat)
    object Pescadex : Screen("pescadex", "Pescadex", Icons.Default.Book)

    object Contador : Screen("fish_counter", "Contador", Icons.Default.Add)
    object Reportes : Screen("reportes", "Reportes", Icons.Default.Assignment)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object Torneos : Screen("torneos", "Torneos", Icons.Default.EmojiEvents)
    object Logros : Screen("achievements_screen", "Logros", Icons.Default.EmojiEvents)
    object Notificaciones : Screen("notificaciones", "Notificaciones", Icons.Default.EmojiEvents)

    object Wizard : Screen("parte_wizard", "Crear parte", Icons.Default.EditNote) {
        // Argumentos opcionales para precargar foto + especie cuando se
        // entra desde "Identificar Captura". Son query params opcionales
        // (con defaultValue null), así que `route` solo ("parte_wizard",
        // sin querystring) sigue funcionando para el resto de los
        // llamadores que no tienen nada que precargar.
        const val ARG_FOTO_URI = "fotoUri"
        // Lista de especies precargadas, codificada como "nombre:cantidad|nombre:cantidad".
        const val ARG_ESPECIES = "especies"
        val routeWithArgs = "parte_wizard?$ARG_FOTO_URI={$ARG_FOTO_URI}&$ARG_ESPECIES={$ARG_ESPECIES}"

        fun buildRoute(fotoUri: String? = null, especies: String? = null): String {
            if (fotoUri == null && especies == null) return route
            val params = buildList {
                fotoUri?.let { add("$ARG_FOTO_URI=${Uri.encode(it)}") }
                especies?.let { add("$ARG_ESPECIES=${Uri.encode(it)}") }
            }
            return "parte_wizard?${params.joinToString("&")}"
        }
    }
}




