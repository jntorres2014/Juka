package com.example.juka.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.ui.theme.navigation.Screen
import com.example.juka.ui.tutorial.CoachMarkOverlay
import com.google.firebase.auth.FirebaseUser

/**
 * Drawer principal de Huka. Reemplaza la antigua bottom nav.
 *
 * Estructura:
 *   - Header con avatar/inicial + saludo personalizado + subtítulo.
 *   - Lista vertical de entries (cards con icono + título + subtítulo).
 *   - Botón de cerrar sesión al final.
 *
 * Cada entry es un [DrawerEntry] con su color/categoría asignado para que
 * cada feature tenga su propio acento visual (Pescadex verde, Crear parte
 * naranja, Chat violeta, etc.) — consistente con el estilo del mockup.
 *
 * Adapta colores al tema (claro/oscuro) usando MaterialTheme.colorScheme.
 */
private data class DrawerEntry(
    val screen: Screen,
    val titulo: String,
    val subtitulo: String,
    val icon: ImageVector,
    val acento: Color
)

@Composable
fun HukaNavigationDrawer(
    user: FirebaseUser,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onCloseDrawer: () -> Unit,
    onSignOut: () -> Unit,
    tutorialStep: Int = -1,
    onTutorialNext: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
    onReplayTutorial: () -> Unit = {}
) {
    val acentoPescadex = Color(0xFF1D9E75)
    val acentoCrearParte = Color(0xFFEF9F27)
    val acentoContador = Color(0xFFD4537E)
    val acentoChat = Color(0xFF7F77DD)
    val acentoReportes = Color(0xFF378ADD)
    val acentoLogros = Color(0xFFD85A30)
    val acentoTorneos = Color(0xFFFFB300)
    val acentoIdentificar = Color(0xFF5DCAA5)
    val acentoPerfil = Color(0xFF888780)

    val entries = listOf(
        DrawerEntry(Screen.Pescadex, "Pescadex", "Identificá especies", Icons.Default.Book, acentoPescadex),
        DrawerEntry(Screen.Wizard, "Crear parte", "Registrá tu captura", Icons.Default.EditNote, acentoCrearParte),
        DrawerEntry(Screen.Contador, "Contador", "Sumá capturas en vivo", Icons.Default.Calculate, acentoContador),
        DrawerEntry(Screen.ChatMenu, "Chat Huka", "Consultá con la IA", Icons.Default.Chat, acentoChat),
        DrawerEntry(Screen.Reportes, "Reportes", "Estadísticas y registros", Icons.Default.Assignment, acentoReportes),
        DrawerEntry(Screen.Logros, "Logros", "Tus medallas y logros", Icons.Default.EmojiEvents, acentoLogros),
        DrawerEntry(Screen.Torneos, "Torneos", "Participá y competí", Icons.Default.EmojiEvents, acentoTorneos),
        DrawerEntry(Screen.Identificar, "Identificar pez", "Sacale una foto", Icons.Default.PhotoCamera, acentoIdentificar),
        DrawerEntry(Screen.Profile, "Perfil", "Tu cuenta y ajustes", Icons.Default.Person, acentoPerfil)
    )

    val listState = rememberLazyListState()
    var crearParteBounds by remember { mutableStateOf<Rect?>(null) }
    var identificarBounds by remember { mutableStateOf<Rect?>(null) }
    var pescadexBounds by remember { mutableStateOf<Rect?>(null) }
    var logrosBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(tutorialStep) {
        when (tutorialStep) {
            2 -> {
                crearParteBounds = null
                listState.animateScrollToItem(1)
            }
            3 -> {
                identificarBounds = null
                listState.animateScrollToItem(7)
            }
            4 -> {
                pescadexBounds = null
                listState.animateScrollToItem(0)
            }
            5 -> {
                logrosBounds = null
                listState.animateScrollToItem(5)
            }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onCloseDrawer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar menú")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Huka", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                // Avatar circular con inicial del usuario
                val inicial = (user.displayName?.firstOrNull() ?: user.email?.firstOrNull() ?: '?')
                    .uppercase()
                Surface(
                    shape = CircleShape,
                    color = acentoPescadex,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(inicial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val nombre = user.displayName?.split(" ")?.firstOrNull() ?: "Pescador"
            Text(
                "Hola $nombre 👋",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Buen día para pescar",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Entries ───────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(entries.size) { idx ->
                val entry = entries[idx]
                // El ítem de chat cubre dos rutas (menú + chat libre), así que
                // se resalta si estamos en cualquiera de las dos. El wizard
                // ahora se registra con su patrón de query params opcionales
                // (routeWithArgs), no con route a secas, así que también hay
                // que contemplar esa variante.
                val seleccionado = currentRoute == entry.screen.route ||
                        (entry.screen == Screen.ChatMenu && currentRoute == Screen.Chat.route) ||
                        (entry.screen == Screen.Wizard && currentRoute == Screen.Wizard.routeWithArgs)
                val tutorialModifier = when (entry.screen) {
                    Screen.Wizard -> Modifier.onGloballyPositioned {
                        crearParteBounds = it.boundsInRoot()
                    }
                    Screen.Identificar -> Modifier.onGloballyPositioned {
                        identificarBounds = it.boundsInRoot()
                    }
                    Screen.Pescadex -> Modifier.onGloballyPositioned {
                        pescadexBounds = it.boundsInRoot()
                    }
                    Screen.Logros -> Modifier.onGloballyPositioned {
                        logrosBounds = it.boundsInRoot()
                    }
                    else -> Modifier
                }

                DrawerEntryCard(
                    entry = entry,
                    isSelected = seleccionado,
                    modifier = tutorialModifier,
                    onClick = { onNavigate(entry.screen) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // ── Ayuda / repetir tutorial ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onReplayTutorial() }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                "Ver tutorial",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ── Cerrar sesión ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSignOut() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                "Cerrar sesión",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
            }

            val tutorialTarget = when (tutorialStep) {
                2 -> crearParteBounds
                3 -> identificarBounds
                4 -> pescadexBounds
                5 -> logrosBounds
                else -> null
            }

            tutorialTarget?.let { target ->
                val (title, description) = when (tutorialStep) {
                    2 -> "Registrá tu jornada" to
                        "Creá un parte cada vez que salgas a pescar. También sirve si no tuviste capturas."
                    3 -> "Identificá una especie" to
                        "Sacale una foto a un pez y Huka puede ayudarte a identificarlo."
                    4 -> "Completá tu Pescadex" to
                        "Las especies que registres se van sumando a tu colección."
                    else -> "Desbloqueá logros" to
                        "Tus registros y participación en Huka te permiten conseguir nuevos logros."
                }

                CoachMarkOverlay(
                    target = target,
                    title = title,
                    description = description,
                    stepLabel = "${tutorialStep + 1} de 6",
                    nextLabel = if (tutorialStep == 5) "Listo" else "Siguiente",
                    onNext = onTutorialNext,
                    onSkip = onTutorialSkip
                )
            }
        }
    }
}

@Composable
private fun DrawerEntryCard(
    entry: DrawerEntry,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        entry.acento.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = if (isSelected) entry.acento.copy(alpha = 0.5f) else Color.Transparent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.dp else 0.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar cuadrado del color de la categoría con el ícono
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(entry.acento.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = entry.acento,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.titulo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    entry.subtitulo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
