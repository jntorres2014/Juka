package com.example.juka.viewmodel

import AchievementsChecker
import GeminiChatService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.CampoParte
import com.example.juka.FishDatabase
import com.example.juka.MLKitManager
import com.example.juka.data.AchievementsViewModel
import com.example.juka.data.ActionResult
import com.example.juka.data.ChatBotActionHandler
import com.example.juka.data.ChatBotManager
import com.example.juka.data.ChatOption
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.domain.chat.ChatQuotaManager
import com.example.juka.domain.model.ChatMessageWithMode
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.EstadoParte
import com.example.juka.domain.model.MLKitExtractionResult

import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.ParteSessionChat
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Importamos el UseCase
import com.example.juka.domain.usecase.ParteLogicUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EnhancedChatViewModel(
    private val quotaManager: ChatQuotaManager,
    private val geminiService: GeminiChatService,
    private val mlKitManager: MLKitManager,
    private val firebaseManager: FirebaseManager,
    private val chatBotManager: ChatBotManager,
    private val chatBotActionHandler: ChatBotActionHandler,
    private val localStorageHelper: LocalStorageHelper,
    private val fishDatabase: FishDatabase,
    // ✅ INYECCIÓN DEL USECASE
    private val parteLogicUseCase: ParteLogicUseCase,
    private val imageHelper: com.example.juka.data.local.ImageHelper,
    private val storageService: com.example.juka.data.firebase.StorageService

) : ViewModel() {

    val quotaState = quotaManager.quotaState
    private val achievementsViewModel = AchievementsViewModel()
    private val _isSendingParte = MutableStateFlow(false)
    val isSendingParte: StateFlow<Boolean> = _isSendingParte
    // Estados principales
    private val _currentMode = MutableStateFlow(ChatMode.GENERAL)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()

    private val _chatEnabled = MutableStateFlow(false)
    val chatEnabled: StateFlow<Boolean> = _chatEnabled.asStateFlow()

    // Chat general
    private val _generalMessages = MutableStateFlow<List<ChatMessageWithMode>>(emptyList())
    val generalMessages: StateFlow<List<ChatMessageWithMode>> = _generalMessages.asStateFlow()

    // Chat de parte actual
    private val _parteSession = MutableStateFlow<ParteSessionChat?>(null)
    val parteSession: StateFlow<ParteSessionChat?> = _parteSession.asStateFlow()

    // Estados de UI
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _firebaseStatus = MutableStateFlow<String?>(null)
    val firebaseStatus: StateFlow<String?> = _firebaseStatus.asStateFlow()

    // Triggers de UI
    private val _showMapPicker = MutableStateFlow(false)
    val showMapPicker: StateFlow<Boolean> = _showMapPicker.asStateFlow()

    private val _showImagePicker = MutableStateFlow(false)
    val showImagePicker: StateFlow<Boolean> = _showImagePicker.asStateFlow()

    // Campos en progreso
    private val _currentFieldInProgress = MutableStateFlow<CampoParte?>(null)
    val currentFieldInProgress: StateFlow<CampoParte?> = _currentFieldInProgress.asStateFlow()

    private val _waitingForFieldResponse = MutableStateFlow<CampoParte?>(null)
    val waitingForFieldResponse: StateFlow<CampoParte?> = _waitingForFieldResponse.asStateFlow()

    companion object {
        private const val TAG = "🎣 EnhancedVM"
    }

    init {
        Log.d(TAG, "✅ Inicializando EnhancedChatViewModel Inyectado")
        chatBotActionHandler.apply {
            onStartParte = { iniciarCrearParte() }
            onDownloadFile = { data -> handleDownload(data) }
        }
        initializeData()
    }


    private fun initializeData() {
        viewModelScope.launch {
            try {
                val resultadoEncuesta = firebaseManager.verificarEncuestaCompletada()
                val encuestaCompleta = when (resultadoEncuesta) {
                    is FirebaseResult.Success -> true
                    else -> false
                }
                if (!encuestaCompleta) Log.i(TAG, "ℹ️ Usuario sin encuesta completa")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error init data: ${e.message}")
            }
        }
        _chatEnabled.value = true
        loadGeneralChatHistory()
        if (_generalMessages.value.isEmpty()) showMainMenu()
    }

    // ================== HELPERS DE UI ==================
    fun dismissMapPicker() { _showMapPicker.value = false }
    fun dismissImagePicker() { _showImagePicker.value = false }

    // ================== LÓGICA CHATBOT ==================
    fun showMainMenu() {
        val node = chatBotManager.getMainMenu()
        addBotMessage(node.message, node.options)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun handleOptionClick(option: ChatOption) {
        val userMessage = ChatMessageWithMode(
            content = option.label.removePrefix(option.icon ?: "").trim(),
            isFromUser = true,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )
        addMessageToGeneralChat(userMessage)

        when (val result = chatBotActionHandler.handleAction(option)) {
            is ActionResult.Navigate -> {
                chatBotManager.currentNode.value?.id?.let { chatBotManager.pushToNavigationStack(it) }
                chatBotManager.navigateToNode(result.nodeId)?.let { node -> addBotMessage(node.message, node.options) }
            }
            is ActionResult.Back -> chatBotManager.navigateBack()?.let { node -> addBotMessage(node.message, node.options) }
            is ActionResult.Home -> showMainMenu()
            is ActionResult.Error -> addBotMessage("❌ ${result.message}")
            else -> if (option.label.contains("Consultar a Huka") || option.label.contains("Chat")) habilitarChat()
        }
    }

    private fun addBotMessage(content: String, options: List<ChatOption>? = null) {
        // 1. Detectamos el modo actual
        val modoActual = _currentMode.value

        val message = ChatMessageWithMode(
            content = content,
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = modoActual, // ✅ Ahora usa el modo real
            options = options
        )

        // 2. Enrutamos a la lista de mensajes que corresponde
        if (modoActual == ChatMode.GENERAL) {
            addMessageToGeneralChat(message)
        } else {
            addMessageToParteSession(message) // ✅ Ahora se verá en el chat del Parte
        }
    }

    private fun handleDownload(data: Map<String, String>?) {
        addBotMessage("📥 Función de descarga simulada.")
    }

    fun iniciarCrearParte() {
        _currentMode.value = ChatMode.CREAR_PARTE

        viewModelScope.launch {
            val borradorGuardado = localStorageHelper.getParteBorrador()

            if (borradorGuardado != null) {
                // 📂 CASO A: RECUPERADO
                // 1. Generamos el texto resumen dinámicamente
                val mensajeResumen = generarResumenBorrador(borradorGuardado)

                _parteSession.value = ParteSessionChat(
                    parteData = borradorGuardado,
                    messages = listOf(
                        ChatMessageWithMode(
                            content = mensajeResumen, // ✅ Usamos el texto generado
                            isFromUser = false,
                            type = MessageType.TEXT,
                            timestamp = getCurrentTimestamp(),
                            mode = ChatMode.CREAR_PARTE
                        )
                    )
                )
            } else {
                // 🆕 CASO B: NUEVO (Igual que antes)
                _parteSession.value = ParteSessionChat()
                val bienvenida = """
                    🎣 **Modo Crear Parte**
                    Vamos a registrar tu jornada.
                    ...
                """.trimIndent()

                val msg = ChatMessageWithMode(bienvenida, false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE)
                addMessageToParteSession(msg)
            }
        }
    }

    fun volverAChatGeneral() {
        _currentMode.value = ChatMode.GENERAL
        showMainMenu()
    }

    fun cancelarParte() {
        _parteSession.value = null
        _currentMode.value = ChatMode.GENERAL
        addBotMessage("❌ **Parte cancelado**\n\nVolviste al menú principal.")
    }

    // ================== ENVÍO DE MENSAJES ==================
    fun sendTextMessage(content: String) {
        when (_currentMode.value) {
            ChatMode.GENERAL -> sendGeneralTextMessage(content)
            ChatMode.CREAR_PARTE -> sendParteTextMessage(content)
        }
    }

    fun sendAudioTranscript(transcript: String) {
        when (_currentMode.value) {
            ChatMode.GENERAL -> sendGeneralAudioMessage(transcript)
            ChatMode.CREAR_PARTE -> sendParteAudioMessage(transcript)
        }
    }

    fun sendImageMessage(imagePath: String) {
        when (_currentMode.value) {
            ChatMode.GENERAL -> sendGeneralImageMessage(imagePath)
            ChatMode.CREAR_PARTE -> sendParteImageMessage(imagePath)
        }
    }

    // --- LÓGICA GENERAL ---
    private fun sendGeneralTextMessage(content: String) {
        if (chatBotManager.isMenuRequest(content)) {
            showMainMenu()
            return
        }

        addUserMessage(content)
        processWithGemini(content)
    }

    private fun processWithGemini(content: String) {
        _isTyping.value = true
        viewModelScope.launch {
            // Verificar quota ANTES de procesar
            if (!quotaManager.canMakeQuery()) {
                addBotMessage(quotaManager.getQuotaMessage())
                _isTyping.value = false
                return@launch
            }

            when (val result = geminiService.processUserMessage(content)) {
                is ChatResult.Success -> {
                    // Ahora consumeQuery también es suspend
                    val consumed = quotaManager.consumeQuery()
                    if (consumed) {
                        addBotMessage("${result.message}\n\n_${quotaManager.getQuotaMessage()}_")
                    } else {
                        addBotMessage("Hubo un error al procesar la consulta, verifica tu conexion a Internet")
                    }
                }
                is ChatResult.Error -> {
                    if (result.shouldConsumeQuota) {
                        quotaManager.consumeQuery()
                    }
                    addBotMessage(result.message)
                }
            }
            _isTyping.value = false
        }
    }

    private fun sendGeneralAudioMessage(transcript: String) {
        addUserMessage("🎤 \"$transcript\"", MessageType.AUDIO)

        _isTyping.value = true
        viewModelScope.launch {
            // Verificar quota primero
            if (!quotaManager.canMakeQuery()) {
                addBotMessage(quotaManager.getQuotaMessage())
                _isTyping.value = false
                return@launch
            }

            when (val result = geminiService.processAudioMessage(transcript)) {
                is ChatResult.Success -> {
                    val consumed = quotaManager.consumeQuery()
                    if (consumed) {
                        addBotMessage("👂 Procesé tu audio:\n\n${result.message}\n\n_${quotaManager.getQuotaMessage()}_")
                    }
                }
                is ChatResult.Error -> addBotMessage(result.message)
            }
            _isTyping.value = false
        }
    }

    private fun sendGeneralImageMessage(uriTemporal: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true

            // 1. Guardamos la imagen de forma SEGURA y PERMANENTE
            val pathSeguro = imageHelper.saveImageToInternalStorage(android.net.Uri.parse(uriTemporal))

            if (pathSeguro != null) {
                // Usamos el path seguro para el mensaje
                val userMessage = ChatMessageWithMode(
                    pathSeguro, // Ahora guardamos /data/user/0/.../hash.jpg
                    true,
                    MessageType.IMAGE,
                    getCurrentTimestamp(),
                    ChatMode.GENERAL
                )
                addMessageToGeneralChat(userMessage)
                saveGeneralMessageToFile(userMessage, "IMAGE: $pathSeguro")

                delay(1000)
                // Aquí podrías llamar al identificador de peces con el path seguro
                addBotMessage("📸 Foto guardada en memoria interna exitosamente.")
            } else {
                addBotMessage("❌ Error al guardar la imagen en memoria.")
            }
            _isAnalyzing.value = false
        }
    }

    private fun sendParteTextMessage(content: String) {
        val userMessage = ChatMessageWithMode(content, true, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE)
        addMessageToParteSession(userMessage)

        // CASO ESPECIAL: Si es observaciones, guardamos el texto plano
        if (_waitingForFieldResponse.value == CampoParte.OBSERVACIONES) {
            actualizarObservaciones(content)
            return
        }

        // PARA TODO LO DEMÁS: Procesamiento Global (Recuperamos la "magia")
        procesarEntradaInteligente(content)
    }

    private fun sendParteAudioMessage(transcript: String) {
        val userMessage = ChatMessageWithMode("🎤 \"$transcript\"", true, MessageType.AUDIO, getCurrentTimestamp(), ChatMode.CREAR_PARTE)
        addMessageToParteSession(userMessage)

        procesarEntradaInteligente(transcript)
    }

    private fun procesarEntradaInteligente(texto: String) {
        _isAnalyzing.value = true
        viewModelScope.launch {
            try {
                // 1. Extraer TODO lo que haya en el texto (ML Kit + Tus Patrones)
                val extractionResult = mlKitManager.extraerInformacionPesca(texto)
                val nuevosData = mlKitManager.convertirEntidadesAParteDatos(extractionResult.entidadesDetectadas)

                _parteSession.value?.let { session ->
                    // 2. Mergear datos nuevos con los existentes usando el UseCase
                    val datosActualizados = parteLogicUseCase.mergearDatos(session.parteData, nuevosData)
                    val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

                    // 3. Si detectamos información, liberamos el "bloqueo" de campo específico
                    if (extractionResult.entidadesDetectadas.isNotEmpty()) {
                        _currentFieldInProgress.value = null
                        _waitingForFieldResponse.value = null
                    }

                    _parteSession.value = session.copy(
                        parteData = datosActualizados.copy(
                            porcentajeCompletado = progreso.porcentaje,
                            camposFaltantes = progreso.camposFaltantes
                        )
                    )

                    delay(1000)

                    // 4. Respuesta dinámica: Resume lo que entendió + Pregunta lo que falta
                    val respuestaBot = generarRespuestaParte(extractionResult, _parteSession.value?.parteData)
                    addBotMessage(respuestaBot)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en procesamiento inteligente: ${e.message}")
                addBotMessage("⚠️ Me costó entender eso. ¿Podrías repetirlo de otra forma?")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
    private fun sendParteImageMessage(uriTemporal: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true // Mostramos indicador de carga

            // 1. EL TRUCO: "Secuestramos" la foto y la guardamos en nuestra memoria interna
            // Esto genera una copia física en: /data/user/0/com.example.juka/files/captured_images/XYZ...HASH.jpg
            val pathSeguro = imageHelper.saveImageToInternalStorage(android.net.Uri.parse(uriTemporal))

            if (pathSeguro != null) {
                // ✅ ÉXITO: Usamos la ruta interna (pathSeguro)

                // A. Actualizamos el Estado del Parte
                _parteSession.value?.let { session ->
                    // Agregamos la ruta SEGURA a la lista de imágenes
                    val nuevasImagenes = session.parteData.imagenes + pathSeguro

                    val datosActualizados = session.parteData.copy(imagenes = nuevasImagenes)

                    // Recalculamos progreso (ahora usando el UseCase)
                    val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

                    _parteSession.value = session.copy(
                        parteData = datosActualizados.copy(
                            porcentajeCompletado = progreso.porcentaje,
                            camposFaltantes = progreso.camposFaltantes
                        )
                    )
                }

                // B. Mostramos el mensaje en el chat (con la foto segura)
                val userMessage = ChatMessageWithMode(
                    content = pathSeguro, // Guardamos el path local, no la URI temporal
                    isFromUser = true,
                    type = MessageType.IMAGE,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(userMessage)

                delay(1000)
                addBotMessage("📸 **Imagen guardada**\n\n${generarResumenProgreso(_parteSession.value?.parteData)}")

            } else {
                // ❌ ERROR: Falló el guardado (espacio lleno, error de lectura, etc)
                addBotMessage("⚠️ No pude guardar la imagen. Intentá de nuevo.")
            }

            _isAnalyzing.value = false
        }
    }
    // ================== MANEJO DE CAMPOS ==================

    fun onCampoParteSelected(campo: CampoParte) {
        _currentFieldInProgress.value = campo
        _waitingForFieldResponse.value = campo

        val pregunta = ChatMessageWithMode(
            content = campo.pregunta ?: "Dime sobre ${campo.name}",
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE,
            metadata = mapOf("fieldType" to campo.name)
        )
        addMessageToParteSession(pregunta)
        addMessageToParteSession(obtenerMensajeDetalleCampo(campo))

        if (campo == CampoParte.UBICACION) _showMapPicker.value = true
        if (campo == CampoParte.FOTOS) _showImagePicker.value = true
    }

    private fun obtenerMensajeDetalleCampo(campo: CampoParte): ChatMessageWithMode {
        val content = obtenerEjemploPorCampo(campo) // Simplificado para usar el helper
        return ChatMessageWithMode(content, false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE)
    }

    private fun procesarRespuestaCampo(content: String, campo: CampoParte) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                if (campo == CampoParte.OBSERVACIONES) {
                    actualizarObservaciones(content)
                } else {
                    val extractionResult = mlKitManager.extraerInformacionPesca(content)
                    // ✅ USAMOS EL USECASE
                    val entidadesRelevantes = parteLogicUseCase.filtrarEntidadesPorCampo(extractionResult, campo)

                    if (entidadesRelevantes.entidadesDetectadas.isNotEmpty()) {
                        actualizarDatosPartePorCampo(campo, entidadesRelevantes)
                        addBotMessage(generarMensajeConfirmacionCampo(campo, entidadesRelevantes))
                        _currentFieldInProgress.value = null
                        _waitingForFieldResponse.value = null
                    } else {
                        addBotMessage("❓ No detecté la info para ${campo.name}. Probá así: ${obtenerEjemploPorCampo(campo)}")
                    }
                }
            } catch (e: Exception) {
                addBotMessage("⚠️ Error procesando respuesta.")
            }
            _isAnalyzing.value = false
        }
    }

    private fun actualizarObservaciones(content: String) {
        _parteSession.value?.let { session ->
            val datos = session.parteData.copy(observaciones = content)
            // ✅ USAMOS EL USECASE
            val progreso = parteLogicUseCase.calcularProgreso(datos)

            _parteSession.value = session.copy(
                parteData = datos.copy(
                    porcentajeCompletado = progreso.porcentaje,
                    camposFaltantes = progreso.camposFaltantes
                )
            )
            addBotMessage("✅ Observaciones guardadas.")
            _currentFieldInProgress.value = null
            _waitingForFieldResponse.value = null
        }
    }

    // ✅ Reemplazamos la función vieja gigante por esta delegada
    private fun actualizarDatosPartePorCampo(campo: CampoParte, extractionResult: MLKitExtractionResult) {
        _parteSession.value?.let { session ->
            // USAMOS EL USECASE
            val datosActualizados = parteLogicUseCase.actualizarDatosPorCampo(session.parteData, campo, extractionResult)
            val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

            _parteSession.value = session.copy(
                parteData = datosActualizados.copy(
                    porcentajeCompletado = progreso.porcentaje,
                    camposFaltantes = progreso.camposFaltantes
                )
            )
        }
    }

    // ================== PERSISTENCIA / HELPERS ==================

    private fun addUserMessage(content: String, type: MessageType = MessageType.TEXT) {
        val msg = ChatMessageWithMode(content, true, type, getCurrentTimestamp(), ChatMode.GENERAL)
        addMessageToGeneralChat(msg)
        saveGeneralMessageToFile(msg)
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun addMessageToGeneralChat(message: ChatMessageWithMode) {
        _generalMessages.value = _generalMessages.value + message
    }

    private fun addMessageToParteSession(message: ChatMessageWithMode) {
        _parteSession.value?.let { session ->
            // 1. Creamos la lista mutable (automáticamente hereda el tipo ChatMessageWithMode)
            val updatedMessages = session.messages.toMutableList()

            // 2. Agregamos el mensaje nuevo
            updatedMessages.add(message)

            // 3. Guardamos SIN hacer cast (ya es del tipo correcto)
            _parteSession.value = session.copy(messages = updatedMessages)
        }
    }

    private fun saveGeneralMessageToFile(message: ChatMessageWithMode, customContent: String? = null) {
        Log.d(TAG, "Guardando mensaje: ${message.content}")
        // viewModelScope.launch { localStorageHelper.saveMessage(message) }
    }

    private fun loadGeneralChatHistory() {
        Log.d(TAG, "Cargando historial...")
        // viewModelScope.launch { ... }
    }

    // ================== GENERACIÓN DE RESPUESTAS VISUALES ==================
    // Estas funciones formatean texto para el usuario. Usan el UseCase para los cálculos.

    private fun generarRespuestaParte(extractionResult: MLKitExtractionResult, datosActuales: ParteEnProgreso?): String {
        if (extractionResult.entidadesDetectadas.isEmpty()) {
            return "🤖 No detecté información específica. ¿Podrías ser más detallado?"
        }
        val respuesta = StringBuilder()
        respuesta.append("🤖 **Información extraída:**\n\n")
        extractionResult.entidadesDetectadas.forEach { entity ->
            respuesta.append("• ${entity.tipo}: ${entity.valor}\n")
        }
        respuesta.append("\n")
        respuesta.append(generarResumenProgreso(datosActuales))
        return respuesta.toString()
    }

    private fun generarResumenProgreso(datos: ParteEnProgreso?): String {
        if (datos == null) return ""
        // ✅ USAMOS EL USECASE PARA CALCULAR
        val progreso = parteLogicUseCase.calcularProgreso(datos)

        val resumen = StringBuilder()
        resumen.append("📋 **Progreso: ${progreso.porcentaje}%**\n")
        if (progreso.camposFaltantes.isNotEmpty()) {
            resumen.append("Falta: ${progreso.camposFaltantes.joinToString(", ")}")
        } else {
            resumen.append("🎉 ¡Parte completo!")
        }
        return resumen.toString()
    }

    // ================== FINALIZACIÓN DEL PARTE ==================

    fun completarYEnviarParte() {
         val auth = FirebaseAuth.getInstance()
        _parteSession.value?.let { session ->
            if (session.parteData.porcentajeCompletado >= 70) {
                _firebaseStatus.value = "Intentando subir..."
                viewModelScope.launch {
                    try {
                        _isSendingParte.value = true
                        val urlsRemotas = session.parteData.imagenes.map { pathLocal ->
                            if (pathLocal.startsWith("http")) pathLocal
                            else {
                                val url = storageService.subirImagen(pathLocal)
                                url ?: throw Exception("No se pudo subir imagen (posiblemente sin señal)")
                            }
                        }

                        val datosFinales = session.parteData.copy(imagenes = urlsRemotas)
                        val sessionFinal = session.copy(parteData = datosFinales)

                        // ✅ VERIFICAR LOGROS CON LA FUNCIÓN MODULAR
                        val achievementsChecker = AchievementsChecker(achievementsViewModel)
                        achievementsChecker.checkParteAchievements(
                            datosFinales,
                            auth.currentUser?.uid ?: ""
                        )


                        // Más ideas: if (totalPeces == 0) { achievementsViewModel.unlockAchievement("cero_peces") } – pero parece que "zapatero_wade" ya es para eso.

                        _firebaseStatus.value = "Sincronizando..."
                        val resultado = firebaseManager.convertirSessionAParte(sessionFinal)

                        if (resultado is FirebaseResult.Success) {
                            _firebaseStatus.value = "¡Subido!"
                            _isSendingParte.value = false
                            addMessageToParteSession(ChatMessageWithMode(
                                "✅ **¡Parte subido a la nube!**", false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE
                            ))
                            localStorageHelper.clearBorrador()
                            delay(2000)
                            volverAChatGeneral()
                        } else {
                            throw Exception("Error al guardar en Firestore")
                        }
                    } catch (e: Exception) {
                        localStorageHelper.saveParteBorrador(session.parteData)
                        _firebaseStatus.value = "Guardado Localmente"
                        addMessageToParteSession(ChatMessageWithMode(
                            "📶 **Sin señal, pero no te preocupes.**\n\nGuardé tu parte y las fotos en este celular como **Borrador**.\nCuando tengas internet, entrá de nuevo y tocá 'Enviar'.",
                            false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE
                        ))
                    }
                }
            } else {
                addBotMessage("⚠️ Faltan datos. Completá al menos el 70%.")
            }
        }
    }

    fun habilitarChat() {
        _chatEnabled.value = true
        addMessageToGeneralChat(ChatMessageWithMode("💬 **Chat activado**", false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.GENERAL))
    }

    fun volverAlMenuPrincipal() {
        _chatEnabled.value = false
        _currentMode.value = ChatMode.GENERAL
        showMainMenu()
    }
    // Función para convertir los datos del borrador en un texto lindo para el chat
    private fun generarResumenBorrador(parte: com.example.juka.domain.model.ParteEnProgreso): String {
        val sb = StringBuilder()

        sb.append("📂 **¡Recuperé tu borrador!**\n")
        sb.append("Esto es lo que tenías guardado:\n\n")

        // 📅 Fecha y Hora
        if (parte.fecha != null) {
            sb.append("📅 **Fecha:** ${parte.fecha}\n")
        }
        if (parte.horaInicio != null || parte.horaFin != null) {
            sb.append("⏰ **Horario:** ${parte.horaInicio ?: "?"} - ${parte.horaFin ?: "?"}\n")
        }

        // 📍 Ubicación
        if (parte.nombreLugar != null) {
            sb.append("📍 **Lugar:** ${parte.nombreLugar}\n")
        }

        // 🐟 Especies (Iteramos la lista si tiene algo)
        if (parte.especiesCapturadas.isNotEmpty()) {
            sb.append("🐟 **Capturas:**\n")
            parte.especiesCapturadas.forEach { pez ->
                sb.append("   • ${pez.numeroEjemplares} ${pez.nombre}\n")
            }
        }

        // 🎣 Modalidad y Cañas
        if (parte.modalidad != null) {
            sb.append("🎣 **Modalidad:** ${parte.modalidad}\n")
        }

        // 📸 Fotos
        if (parte.imagenes.isNotEmpty()) {
            sb.append("📸 **Fotos:** ${parte.imagenes.size} adjuntas\n")
        }

        // 📝 Notas
        if (!parte.observaciones.isNullOrBlank()) {
            sb.append("📝 **Notas:** \"${parte.observaciones}\"\n")
        }

        sb.append("\n👉 Si todo está bien, tocá **Enviar**. Si falta algo, decime y lo agregamos.")

        return sb.toString()
    }
    fun saveLocation(latitude: Double, longitude: Double, name: String?) {
        if (_currentMode.value != ChatMode.CREAR_PARTE) return
        val geoPoint = GeoPoint(latitude, longitude)
        val locationName = name ?: "Ubicación sin nombre"

        _parteSession.value?.let { session ->
            val datosActualizados = session.parteData.copy(ubicacion = geoPoint, nombreLugar = locationName)
            // ✅ USAMOS EL USECASE
            val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

            _parteSession.value = session.copy(
                parteData = datosActualizados.copy(
                    porcentajeCompletado = progreso.porcentaje,
                    camposFaltantes = progreso.camposFaltantes
                )
            )
            addMessageToParteSession(ChatMessageWithMode("✅ **Ubicación:** $locationName", false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE))
        }
    }

    fun retomarBorrador(session: ParteSessionChat) {
        _currentMode.value = ChatMode.CREAR_PARTE
        _parteSession.value = session.copy(estado = EstadoParte.EN_PROGRESO)
        addMessageToParteSession(ChatMessageWithMode("🔄 **Borrador retomado**", false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE))
    }

    fun getConversationStats(): String {
        val generalCount = _generalMessages.value.size
        val parteCount = _parteSession.value?.messages?.size ?: 0
        return "📊 General: $generalCount | Parte: $parteCount"
    }

    private fun obtenerEjemploPorCampo(campo: CampoParte): String {
        return when (campo) {
            /*CampoParte.HORARIOS -> "De 6 a 11"
            CampoParte.ESPECIES -> "2 percas"
            CampoParte.FECHA -> "Hoy"
            CampoParte.CANAS -> "2 cañas"
            CampoParte.MODALIDAD -> "Costa"
            CampoParte.UBICACION -> "ej: Trelew"
            CampoParte.OBSERVACIONES -> "Detalles extra..."*/
            else -> "..."
        }
    }

    private fun generarMensajeConfirmacionCampo(campo: CampoParte, extraction: MLKitExtractionResult): String {
        return "✅ Información registrada para ${campo.name}"
    }
    // ================== CONTADOR DE PECES (Fish Counter) ==================

    // Estado del contador (Lista temporal de especies capturadas)
    private val _contadorPeces = MutableStateFlow<List<com.example.juka.domain.model.EspecieCapturada>>(emptyList())
    val contadorPeces: StateFlow<List<com.example.juka.domain.model.EspecieCapturada>> = _contadorPeces.asStateFlow()

    /**
     * Agrega peces al contador. Si la especie ya existe, SUMA la cantidad.
     */
    fun agregarPezAlContador(nombreEspecie: String, cantidad: Int) {
        val listaActual = _contadorPeces.value.toMutableList()

        // Buscamos si ya existe esa especie en la lista
        val indiceExistente = listaActual.indexOfFirst { it.nombre.equals(nombreEspecie, ignoreCase = true) }

        if (indiceExistente != -1) {
            // CASO A: YA EXISTE -> SUMAMOS
            val itemExistente = listaActual[indiceExistente]
            val nuevaCantidad = itemExistente.numeroEjemplares + cantidad

            // Reemplazamos el objeto con la nueva cantidad
            listaActual[indiceExistente] = itemExistente.copy(numeroEjemplares = nuevaCantidad)
        } else {
            // CASO B: NUEVO -> AGREGAMOS
            listaActual.add(com.example.juka.domain.model.EspecieCapturada(nombre = nombreEspecie, numeroEjemplares = cantidad))
        }

        _contadorPeces.value = listaActual
    }

    /**
     * Resta o elimina un pez del contador
     */
    fun eliminarPezDelContador(nombreEspecie: String) {
        val listaActual = _contadorPeces.value.toMutableList()
        listaActual.removeAll { it.nombre == nombreEspecie }
        _contadorPeces.value = listaActual
    }

    /**
     * Transforma el contador en un Parte y cambia al modo Chat
     */
    fun iniciarParteDesdeContador() {
        val pecesContados = _contadorPeces.value
        if (pecesContados.isEmpty()) return

        // 1. Creamos un ParteEnProgreso ya con los peces cargados
        val datosIniciales = com.example.juka.domain.model.ParteEnProgreso(
            especiesCapturadas = pecesContados
        )

        // 2. Iniciamos el modo crear parte con estos datos
        _currentMode.value = ChatMode.CREAR_PARTE

        // Guardamos borrador por seguridad
        localStorageHelper.saveParteBorrador(datosIniciales)

        // 3. Generamos mensaje inicial
        val resumenPeces = pecesContados.joinToString(", ") { "${it.numeroEjemplares} ${it.nombre}" }

        _parteSession.value = com.example.juka.domain.model.ParteSessionChat(
            parteData = datosIniciales,
            messages = listOf(
                ChatMessageWithMode(
                    "🎣 **¡Parte iniciado desde el Contador!**\n\n" +
                            "Ya cargué tus capturas:\n**$resumenPeces**\n\n" +
                            "Ahora contame el resto: ¿Dónde pescaste y qué fecha?",
                    false, MessageType.TEXT, getCurrentTimestamp(), ChatMode.CREAR_PARTE
                )
            )
        )

        // Limpiamos el contador
        _contadorPeces.value = emptyList()
    }
// ================== CONTADOR DE PECES (Fish Counter) - FUNCIONES ADICIONALES ==================

    /**
     * Actualiza la cantidad de una especie específica en el contador
     */
    fun actualizarCantidadPez(nombreEspecie: String, nuevaCantidad: Int) {
        if (nuevaCantidad <= 0) {
            // Si la cantidad es 0 o negativa, eliminamos la especie
            eliminarPezDelContador(nombreEspecie)
            return
        }

        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst {
            it.nombre.equals(nombreEspecie, ignoreCase = true)
        }

        if (indiceExistente != -1) {
            // Actualizamos la cantidad
            listaActual[indiceExistente] = listaActual[indiceExistente].copy(
                numeroEjemplares = nuevaCantidad
            )
            _contadorPeces.value = listaActual
        }
    }

    /**
     * Limpia completamente el contador de peces
     */
    fun limpiarContador() {
        _contadorPeces.value = emptyList()
    }

    /**
     * Verifica si una especie ya está en el contador
     */
    fun especieYaEnContador(nombreEspecie: String): Boolean {
        return _contadorPeces.value.any {
            it.nombre.equals(nombreEspecie, ignoreCase = true)
        }
    }

    /**
     * Obtiene el total de peces en el contador
     */
    fun getTotalPecesContador(): Int {
        return _contadorPeces.value.sumOf { it.numeroEjemplares }
    }

    /**
     * Incrementa la cantidad de una especie en 1
     */
    fun incrementarEspecie(nombreEspecie: String) {
        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst {
            it.nombre.equals(nombreEspecie, ignoreCase = true)
        }

        if (indiceExistente != -1) {
            val itemExistente = listaActual[indiceExistente]
            listaActual[indiceExistente] = itemExistente.copy(
                numeroEjemplares = itemExistente.numeroEjemplares + 1
            )
            _contadorPeces.value = listaActual
        } else {
            // Si no existe, la agregamos con cantidad 1
            agregarPezAlContador(nombreEspecie, 1)
        }
    }

    /**
     * Decrementa la cantidad de una especie en 1
     */
    fun decrementarEspecie(nombreEspecie: String) {
        val listaActual = _contadorPeces.value.toMutableList()
        val indiceExistente = listaActual.indexOfFirst {
            it.nombre.equals(nombreEspecie, ignoreCase = true)
        }

        if (indiceExistente != -1) {
            val itemExistente = listaActual[indiceExistente]
            val nuevaCantidad = itemExistente.numeroEjemplares - 1

            if (nuevaCantidad > 0) {
                listaActual[indiceExistente] = itemExistente.copy(
                    numeroEjemplares = nuevaCantidad
                )
            } else {
                // Si llega a 0, eliminamos la especie
                listaActual.removeAt(indiceExistente)
            }
            _contadorPeces.value = listaActual
        }
    }

    /**
     * Inicializa el contador desde un ParteEnProgreso existente (útil para editar)
     */
    fun cargarContadorDesdeParteExistente(parte: ParteEnProgreso) {
        _contadorPeces.value = parte.especiesCapturadas
    }

    /**
     * Guarda el estado actual del contador en preferencias (para persistencia)
     */
    fun guardarEstadoContador() {
        viewModelScope.launch {
            try {
                // Especificamos el tipo explícitamente
                val json = Json.encodeToString<List<EspecieCapturada>>(_contadorPeces.value)
                localStorageHelper.savePreference("contador_peces_backup", json)
            } catch (e: Exception) {
                Log.e("FishCounter", "Error guardando contador: ${e.message}")
            }
        }
    }

    /**
     * Restaura el contador desde preferencias
     */
    fun restaurarEstadoContador() {
        viewModelScope.launch {
            try {
                val json = localStorageHelper.getPreference("contador_peces_backup")
                if (!json.isNullOrEmpty()) {
                    _contadorPeces.value = Json.decodeFromString(json)
                }
            } catch (e: Exception) {
                Log.e("FishCounter", "Error restaurando contador: ${e.message}")
            }
        }
    }

}