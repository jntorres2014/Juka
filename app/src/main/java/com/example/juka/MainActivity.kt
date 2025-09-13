// MainActivity.kt - MODIFICACIÓN MÍNIMA CON AUTH
package com.example.juka

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.juka.ui.theme.HukaTheme
import kotlinx.coroutines.delay
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    private val RECORD_AUDIO_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        setContent {
            HukaTheme {
                // ✅ NUEVA ESTRUCTURA CON AUTH
                AppWithAuth()
            }
        }
    }
}

// ✅ TUS COMPOSABLES ACTUALES - AGREGAR AL FINAL DEL ARCHIVO
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            Text(
                                text = message.content,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        MessageType.AUDIO -> {
                            Text(
                                text = message.content,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        MessageType.IMAGE -> {
                            ImageMessageContent(
                                imagePath = message.content,
                                isUser = isUser
                            )
                        }
                    }
                }
            }

            Text(
                text = message.timestamp,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Juka está escribiendo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AnalyzingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onTertiary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Analizando imagen con IA...",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun ImageMessageContent(imagePath: String, isUser: Boolean) {
    Column {
        Image(
            painter = rememberAsyncImagePainter(imagePath),
            contentDescription = "Imagen enviada",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Imagen enviada",
            fontSize = 12.sp,
            color = if (isUser)
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

// ✅ FUNCIÓN PRINCIPAL CON AUTH
@Composable
fun AppWithAuth() {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val authState by authManager.authState.collectAsState()

    when (authState) {
        is AuthState.Loading -> {
            // Pantalla de carga
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎣", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando Juka...")
                }
            }
        }

        is AuthState.Unauthenticated -> {
            // Mostrar login
            LoginScreen(
                authManager = authManager,
                onLoginSuccess = { /* El estado cambiará automáticamente */ }
            )
        }

        is AuthState.Authenticated -> {
            // ✅ MOSTRAR TU APP ACTUAL CON USUARIO
            JukaAppWithUser(user = (authState as AuthState.Authenticated).user, authManager = authManager)
        }

        is AuthState.Error -> {
            // Error screen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Error: ${(authState as AuthState.Error).message}")
                }
            }
        }
    }
}

// ✅ TU APP ACTUAL (PRÁCTICAMENTE SIN CAMBIOS)
sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Identificar : Screen("identificar", "Identificar", Icons.Default.PhotoCamera)
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    object Reportes : Screen("reportes", "Reportes", Icons.Default.Book)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person) // ✅ NUEVA
}

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
                ChatScreenCompleto(user = user) // ← Versión completa
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

// ✅ TU CHATSCREEN CON PEQUEÑO CAMBIO
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenWithUser(
    user: com.google.firebase.auth.FirebaseUser,
    viewModel: ChatViewModel = viewModel()
) {
    // TODO: Aquí va tu ChatScreen actual, solo agregamos el nombre del usuario en el header

    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    // Launcher para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.sendImageMessage(it.toString())
        }
    }

    // Auto-scroll al último mensaje
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // ✅ HEADER CON NOMBRE DEL USUARIO
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Hola ${user.displayName?.split(" ")?.first() ?: "Pescador"} 🎣",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (isTyping) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "escribiendo...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            actions = {
                IconButton(onClick = {
                    val stats = viewModel.getConversationStats()
                    Toast.makeText(context, stats, Toast.LENGTH_LONG).show()
                }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Estadísticas",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )

        // ✅ RESTO DE TU CHATSCREEN ACTUAL (sin cambios)
        // Lista de mensajes
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }

            if (isAnalyzing) {
                item {
                    AnalyzingIndicator()
                }
            }

            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Input area (tu código actual)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Botón de imagen
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Enviar imagen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Campo de texto
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botones de envío
                Row {
                    // Botón de audio (tu componente actual)
                    AudioRecordButton(
                        onAudioTranscribed = { transcript ->
                            viewModel.sendAudioTranscript(transcript)
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Botón de texto
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendTextMessage(messageText.trim())
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar texto",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

// ✅ PANTALLA DE PERFIL SÚPER SIMPLE
@Composable
fun SimpleProfileScreen(
    user: com.google.firebase.auth.FirebaseUser,
    authManager: AuthManager
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Foto del usuario (si tiene)
        if (user.photoUrl != null) {
            coil.compose.AsyncImage(
                model = user.photoUrl,
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Información del usuario
        Text(
            text = user.displayName ?: "Usuario sin nombre",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = user.email ?: "Sin email",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Información de la app
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Juka - Tu Asistente de Pesca",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ Chat inteligente\n✅ Identificación de peces\n✅ Reportes automáticos\n✅ Datos sincronizados",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Botón de cerrar sesión
        Button(
            onClick = { authManager.signOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar Sesión")
        }
    }
}