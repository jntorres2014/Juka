package com.example.juka.ui.theme.chat

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.component.AnalyzingIndicator
import com.example.juka.CampoParte
import com.example.juka.viewmodel.EnhancedChatViewModel
import com.example.juka.ui.theme.MapPickerScreen
import com.example.juka.component.MessageBubble
import com.example.juka.ParteQuickActions
import com.example.juka.component.EnhancedChatHeader
import com.example.juka.component.EnhancedMessageInput
import com.example.juka.component.SimpleParteInput
import com.example.juka.component.TypingIndicator
import com.example.juka.component.WorkingAudioButton
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.ParteSessionChat
import com.example.juka.viewmodel.AppViewModelProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedChatScreen(
    user: FirebaseUser,
    viewModel: EnhancedChatViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onOpenCounter: () -> Unit = {}

) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Estados del ViewModel
    //val currentMode by viewModel.currentMode.collectAsState()
    val generalMessages by viewModel.generalMessages.collectAsState()
    val parteSession by viewModel.parteSession.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val chatEnabled by viewModel.chatEnabled.collectAsState()


    // NUEVOS estados
    val currentFieldInProgress by viewModel.currentFieldInProgress.collectAsState()
    val showMapPickerFromViewModel by viewModel.showMapPicker.collectAsState()
    val showImagePickerFromViewModel by viewModel.showImagePicker.collectAsState()

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

    // Determinar qué mensajes mostrar según el modo
    val currentMessages = when (currentMode) {
        ChatMode.GENERAL -> generalMessages
        ChatMode.CREAR_PARTE -> parteSession?.messages ?: emptyList()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ================== HEADER DINÁMICO ==================
        EnhancedChatHeader(
            user = user,
            currentMode = currentMode,
            parteSession = parteSession,
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
        if (currentMode == ChatMode.CREAR_PARTE && parteSession != null) {
            AnimatedVisibility(
                visible = true,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    // Progreso simplificado
                    LinearProgressIndicator(
                        progress = parteSession!!.parteData.porcentajeCompletado / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = when {
                            parteSession!!.parteData.porcentajeCompletado >= 80 -> Color(0xFF4CAF50)
                            parteSession!!.parteData.porcentajeCompletado >= 50 -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )

                    // NUEVO: Botones de acción rápida
                    ParteQuickActions(
                        parteData = parteSession!!.parteData,
                        onCampoSelected = { campo ->
                            viewModel.onCampoParteSelected(campo)
                        },
                        currentFieldInProgress = currentFieldInProgress,
                        onGuardarBorrador = {
                            //viewModel.guardarParteBorrador()
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
                            onCreateParte = { viewModel.iniciarCrearParte() }
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
}

