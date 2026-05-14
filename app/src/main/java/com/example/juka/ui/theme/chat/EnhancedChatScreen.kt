package com.example.juka.ui.theme.chat

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.component.AnalyzingIndicator
import com.example.juka.CampoParte
import com.example.juka.data.local.BorradorMeta
import com.example.juka.viewmodel.EnhancedChatViewModel
import com.example.juka.ui.theme.MapPickerScreen
import com.example.juka.component.MessageBubble
import com.example.juka.ParteQuickActions
import com.example.juka.R
import com.example.juka.component.EnhancedChatHeader
import com.example.juka.component.EnhancedMessageInput
import com.example.juka.component.SimpleParteInput
import com.example.juka.component.TypingIndicator
import com.example.juka.component.WorkingAudioButton
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.viewmodel.AppViewModelProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedChatScreen(
    user: FirebaseUser,
    viewModel: EnhancedChatViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToWizard: () -> Unit = {}

) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Estados del ViewModel
    //val currentMode by viewModel.currentMode.collectAsState()
    val generalMessages by viewModel.generalMessages.collectAsState()
    val parteData by viewModel.parteData.collectAsState()
    val parteMessages by viewModel.parteMessages.collectAsState()
    val borradoresPendientes by viewModel.borradoresPendientes.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val chatEnabled by viewModel.chatEnabled.collectAsState()


    // NUEVOS estados
    val currentFieldInProgress by viewModel.currentFieldInProgress.collectAsState()
    val showMapPickerFromViewModel by viewModel.showMapPicker.collectAsState()
    val showImagePickerFromViewModel by viewModel.showImagePicker.collectAsState()

    // NUEVO: Estado para el spinner de loading al completar/enviar parte
    val isSendingParte by viewModel.isSendingParte.collectAsState()

    // Estados locales
    //var messageText by remember { mutableStateOf("") }
    //var showMapPicker by remember { mutableStateOf(false) }
    var messageText by rememberSaveable { mutableStateOf("") }  // ← CAMBIO PARA PERSISTENCIA: rememberSaveable en lugar de remember
    var showMapPicker by rememberSaveable { mutableStateOf(false) }  // ← CAMBIO PARA PERSISTENCIA: rememberSaveable
    if (showMapPicker || showMapPickerFromViewModel) {
        MapPickerScreen(
            onDismiss = {
                showMapPicker = false
                viewModel.dismissMapPicker()
            },
            onLocationSelected = { lat, lon, name ->
                viewModel.saveLocation(lat, lon, name)
                showMapPicker = false
                viewModel.dismissMapPicker()
            }
        )
    }

    // Dialog de borradores pendientes: aparece al entrar a "Crear parte"
    // cuando hay 1+ partes sin terminar y el usuario aún no eligió cuál
    // retomar (parteData == null).
    if (
        currentMode == ChatMode.CREAR_PARTE &&
        parteData == null &&
        borradoresPendientes.isNotEmpty()
    ) {
        BorradoresPendientesDialog(
            borradores = borradoresPendientes,
            onRetomar = { id -> viewModel.retomarBorradorPorId(id) },
            onDescartar = { id -> viewModel.descartarBorrador(id) },
            onNuevoParte = { viewModel.crearNuevoParte() },
            onCerrar = { viewModel.volverAChatGeneral() }
        )
    }

    // Determinar qué mensajes mostrar según el modo
    val currentMessages = when (currentMode) {
        ChatMode.GENERAL -> generalMessages
        ChatMode.CREAR_PARTE -> parteMessages
    }

    // Launcher para imágenes
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendImageMessage(it.toString()) }
    }

    // Auto-scroll cuando cambian los mensajes
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    // NUEVO: Wrap en Box para overlay del spinner
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ================== HEADER DINÁMICO ==================
            EnhancedChatHeader(
                user = user,
                currentMode = currentMode,
                parteData = parteData,
                firebaseStatus = firebaseStatus,
                onModeChange = { mode ->
                    when (mode) {
                        ChatMode.GENERAL -> viewModel.volverAChatGeneral()
                        ChatMode.CREAR_PARTE -> viewModel.iniciarCrearParte()
                    }
                },
                onCancelarParte = { viewModel.cancelarParte() },
                onInfoClick = {
                    val stats = viewModel.getConversationStats()
                    Toast.makeText(context, stats, Toast.LENGTH_LONG)
                        .show()
                },  // ← CERRAR EL onInfoClick AQUÍ
                showMenuButton = chatEnabled && currentMode == ChatMode.GENERAL,  // ← ESTOS VAN FUERA
                onMenuClick = { viewModel.volverAlMenuPrincipal() }  // ← ESTOS VAN FUERA
            )


            // ================== PROGRESO DEL PARTE (SI APLICA) ==================
            if (currentMode == ChatMode.CREAR_PARTE && parteData != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // Progreso simplificado
                        LinearProgressIndicator(
                            progress = parteData!!.porcentajeCompletado / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = when {
                                parteData!!.porcentajeCompletado >= 80 -> Color(0xFF4CAF50)
                                parteData!!.porcentajeCompletado >= 50 -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )

                        // NUEVO: Botones de acción rápida
                        ParteQuickActions(
                            parteData = parteData!!,
                            onCampoSelected = { campo ->
                                viewModel.onCampoParteSelected(campo)
                            },
                            currentFieldInProgress = currentFieldInProgress,
                            onGuardarBorrador = {
                                // Cierra este parte sin enviarlo; queda en la
                                // lista de borradores para retomarlo después.
                                viewModel.guardarBorradorYVolver()
                            },
                            onCompletarParte = {
                                viewModel.completarYEnviarParte()
                            },
                            firebaseStatus = firebaseStatus,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

            }
            // ================== LISTA DE MENSAJES ==================
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentMessages) { message ->
                    // ✅ USANDO COMPONENTE CENTRALIZADO
                    MessageBubble(
                        message = message,
                        currentMode = currentMode,
                        onOptionClick = { option ->  // ← AGREGAR ESTE HANDLER
                            viewModel.handleOptionClick(option)
                        }
                    )

                }

                // Indicadores de estado
                if (isAnalyzing) {
                    item {
                        // ✅ COMPONENTE CENTRALIZADO
                        AnalyzingIndicator()
                    }
                }

                if (isTyping) {
                    item {
                        // ✅ COMPONENTE CENTRALIZADO
                        TypingIndicator()
                    }
                }

                // Espaciador final
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ================== INPUT MEJORADO ==================
            when {
                // Si estamos en modo crear parte, mostrar input de parte
                currentMode == ChatMode.CREAR_PARTE -> {
                    SimpleParteInput(
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        onSendMessage = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendTextMessage(messageText.trim())
                                messageText = ""
                            }
                        },
                        onSendAudio = { transcript ->
                            viewModel.sendAudioTranscript(transcript)
                        },
                        isWaitingForResponse = currentFieldInProgress != null,
                        currentField = currentFieldInProgress
                    )
                }

                // Si el chat está habilitado en modo general, mostrar input
                currentMode == ChatMode.GENERAL -> {
                    EnhancedMessageInput(
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        onSendMessage = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendTextMessage(messageText.trim())
                                messageText = ""
                            }
                        },
                        onSendImage = { imagePickerLauncher.launch("image/*") },
                        onSendAudio = { transcript -> viewModel.sendAudioTranscript(transcript) },
                        onSendLocation = { showMapPicker = true },
                        currentMode = currentMode,
                        isProcessing = isTyping || isAnalyzing,
                        onCreateParte = { viewModel.iniciarCrearParte() },
                        onNavigateToWizard = onNavigateToWizard
                    )
                }

                // Si no, no mostrar input (solo botones del menú)
                else -> {
                    // Espacio vacío para mantener el layout consistente
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        // Trigger para selector de imágenes
        LaunchedEffect(showImagePickerFromViewModel) {
            if (showImagePickerFromViewModel) {
                imagePickerLauncher.launch("image/*")
                viewModel.dismissImagePicker()
            }
        }

        // Usa iconos de Material y animalos
        if (isSendingParte) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Animación de la caña de pescar
                        val infiniteTransition = rememberInfiniteTransition(label = "fishing")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = -15f,
                            targetValue = 15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "rod_rotation"
                        )

                        Icon(
                            painter = painterResource(id = R.drawable.fishing), // Usa un icono PNG/XML
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Puntos animados de carga
                        val dotAnimation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "dots"
                        )

                        Text(
                            text = "Generando reporte" + ".".repeat(dotAnimation.toInt()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog de borradores pendientes
//
// Aparece al entrar a "Crear parte" cuando hay 1+ partes sin terminar. Le da
// al usuario tres opciones: retomar uno existente, descartar uno, o arrancar
// un parte nuevo (que se suma a la lista). Cerrar el dialog vuelve al menú
// general sin tocar los borradores.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorradoresPendientesDialog(
    borradores: List<BorradorMeta>,
    onRetomar: (String) -> Unit,
    onDescartar: (String) -> Unit,
    onNuevoParte: () -> Unit,
    onCerrar: () -> Unit
) {
    Dialog(onDismissRequest = onCerrar) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📂 Borradores pendientes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tenés ${borradores.size} parte${if (borradores.size == 1) "" else "s"} sin terminar.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onNuevoParte,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nuevo parte", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(borradores) { borrador ->
                        BorradorCard(
                            borrador = borrador,
                            onRetomar = { onRetomar(borrador.id) },
                            onDescartar = { onDescartar(borrador.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorradorCard(
    borrador: BorradorMeta,
    onRetomar: () -> Unit,
    onDescartar: () -> Unit
) {
    val lugar = borrador.resumenLugar?.takeIf { it.isNotBlank() } ?: "Sin lugar"
    val fecha = borrador.resumenFecha?.takeIf { it.isNotBlank() } ?: "Sin fecha"
    val pct = borrador.porcentajeCompletado

    Card(
        onClick = onRetomar,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lugar,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "📅 $fecha",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = when {
                        pct >= 80 -> Color(0xFF4CAF50)
                        pct >= 50 -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    "$pct% completo",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDescartar) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Descartar borrador",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}