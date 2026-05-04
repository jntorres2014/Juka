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

import com.example.juka.domain.usecase.ParteLogicUseCase
import com.google.firebase.auth.FirebaseAuth
// IMPORTANTE: Asegurate de importar tu nuevo FishCounterManager
import com.example.juka.data.FishCounterManager

class EnhancedChatViewModel(
    private val quotaManager: ChatQuotaManager,
    private val geminiService: GeminiChatService,
    private val mlKitManager: MLKitManager,
    private val firebaseManager: FirebaseManager,
    private val chatBotManager: ChatBotManager,
    private val chatBotActionHandler: ChatBotActionHandler,
    private val localStorageHelper: LocalStorageHelper,
    private val fishDatabase: FishDatabase,
    private val parteLogicUseCase: ParteLogicUseCase,
    private val imageHelper: com.example.juka.data.local.ImageHelper,
    private val storageService: com.example.juka.data.firebase.StorageService,
    // ✅ MANAGER INYECTADO
    val fishCounterManager: FishCounterManager
) : ViewModel() {

    val quotaState = quotaManager.quotaState
    private val achievementsViewModel = AchievementsViewModel()
    private val _isSendingParte = MutableStateFlow(false)
    val isSendingParte: StateFlow<Boolean> = _isSendingParte

    private val _currentMode = MutableStateFlow(ChatMode.GENERAL)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()

    private val _chatEnabled = MutableStateFlow(false)
    val chatEnabled: StateFlow<Boolean> = _chatEnabled.asStateFlow()

    private val _generalMessages = MutableStateFlow<List<ChatMessageWithMode>>(emptyList())
    val generalMessages: StateFlow<List<ChatMessageWithMode>> = _generalMessages.asStateFlow()

    private val _parteSession = MutableStateFlow<ParteSessionChat?>(null)
    val parteSession: StateFlow<ParteSessionChat?> = _parteSession.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _firebaseStatus = MutableStateFlow<String?>(null)
    val firebaseStatus: StateFlow<String?> = _firebaseStatus.asStateFlow()

    private val _showMapPicker = MutableStateFlow(false)
    val showMapPicker: StateFlow<Boolean> = _showMapPicker.asStateFlow()

    private val _showImagePicker = MutableStateFlow(false)
    val showImagePicker: StateFlow<Boolean> = _showImagePicker.asStateFlow()

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

    fun dismissMapPicker() { _showMapPicker.value = false }
    fun dismissImagePicker() { _showImagePicker.value = false }

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
        val modoActual = _currentMode.value

        val message = ChatMessageWithMode(
            content = content,
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = modoActual,
            options = options
        )

        if (modoActual == ChatMode.GENERAL) {
            addMessageToGeneralChat(message)
        } else {
            addMessageToParteSession(message)
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
                val mensajeResumen = generarResumenBorrador(borradorGuardado)

                _parteSession.value = ParteSessionChat(
                    parteData = borradorGuardado,
                    messages = listOf(
                        ChatMessageWithMode(
                            content = mensajeResumen,
                            isFromUser = false,
                            type = MessageType.TEXT,
                            timestamp = getCurrentTimestamp(),
                            mode = ChatMode.CREAR_PARTE
                        )
                    )
                )
            } else {
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
            if (!quotaManager.canMakeQuery()) {
                addBotMessage(quotaManager.getQuotaMessage())
                _isTyping.value = false
                return@launch
            }

            when (val result = geminiService.processUserMessage(content)) {
                is ChatResult.Success -> {
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

            val pathSeguro = imageHelper.saveImageToInternalStorage(android.net.Uri.parse(uriTemporal))

            if (pathSeguro != null) {
                val userMessage = ChatMessageWithMode(
                    pathSeguro,
                    true,
                    MessageType.IMAGE,
                    getCurrentTimestamp(),
                    ChatMode.GENERAL
                )
                addMessageToGeneralChat(userMessage)
                saveGeneralMessageToFile(userMessage, "IMAGE: $pathSeguro")

                delay(1000)
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

        if (_waitingForFieldResponse.value == CampoParte.OBSERVACIONES) {
            actualizarObservaciones(content)
            return
        }
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
                // 1. Detectar si el pescador está declarando explícitamente que no pescó nada
                val textoLower = texto.lowercase()
                val declaroZapatero = textoLower.contains("no pesque nada") ||
                        textoLower.contains("no saque nada") ||
                        textoLower.contains("zapatero") ||
                        textoLower.contains("cero capturas") ||
                        textoLower.contains("ningun pez")

                if (declaroZapatero) {
                    _parteSession.value?.let { session ->
                        // Marcamos el parte como "sin capturas" y vaciamos la lista por las dudas
                        val datosActualizados = session.parteData.copy(
                            sinCapturas = true,
                            especiesCapturadas = emptyList()
                        )
                        val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

                        _currentFieldInProgress.value = null
                        _waitingForFieldResponse.value = null

                        _parteSession.value = session.copy(
                            parteData = datosActualizados.copy(
                                porcentajeCompletado = progreso.porcentaje,
                                camposFaltantes = progreso.camposFaltantes
                            )
                        )

                        delay(1000)
                        addBotMessage("📝 **¡Anotado!**\n\nRegistrar los días sin pique es información súper valiosa para cuidar el ecosistema.\n\n${generarResumenProgreso(_parteSession.value?.parteData)}")
                    }
                } else {
                    // 2. Si pescó algo, seguimos el flujo normal con ML Kit / Extractor
                    val extractionResult = mlKitManager.extraerInformacionPesca(texto)
                    val nuevosData = mlKitManager.convertirEntidadesAParteDatos(extractionResult.entidadesDetectadas)

                    _parteSession.value?.let { session ->
                        val datosActualizados = parteLogicUseCase.mergearDatos(session.parteData, nuevosData)
                        val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

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
                        val respuestaBot = generarRespuestaParte(extractionResult, _parteSession.value?.parteData)
                        addBotMessage(respuestaBot)
                    }
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
            _isAnalyzing.value = true

            val pathSeguro = imageHelper.saveImageToInternalStorage(android.net.Uri.parse(uriTemporal))

            if (pathSeguro != null) {
                _parteSession.value?.let { session ->
                    val nuevasImagenes = session.parteData.imagenes + pathSeguro
                    val datosActualizados = session.parteData.copy(imagenes = nuevasImagenes)
                    val progreso = parteLogicUseCase.calcularProgreso(datosActualizados)

                    _parteSession.value = session.copy(
                        parteData = datosActualizados.copy(
                            porcentajeCompletado = progreso.porcentaje,
                            camposFaltantes = progreso.camposFaltantes
                        )
                    )
                }

                val userMessage = ChatMessageWithMode(
                    content = pathSeguro,
                    isFromUser = true,
                    type = MessageType.IMAGE,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(userMessage)

                delay(1000)
                addBotMessage("📸 **Imagen guardada**\n\n${generarResumenProgreso(_parteSession.value?.parteData)}")

            } else {
                addBotMessage("⚠️ No pude guardar la imagen. Intentá de nuevo.")
            }

            _isAnalyzing.value = false
        }
    }

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
        val content = obtenerEjemploPorCampo(campo)
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

    private fun actualizarDatosPartePorCampo(campo: CampoParte, extractionResult: MLKitExtractionResult) {
        _parteSession.value?.let { session ->
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
            val updatedMessages = session.messages.toMutableList()
            updatedMessages.add(message)
            _parteSession.value = session.copy(messages = updatedMessages)
        }
    }

    private fun saveGeneralMessageToFile(message: ChatMessageWithMode, customContent: String? = null) {
        Log.d(TAG, "Guardando mensaje: ${message.content}")
    }

    private fun loadGeneralChatHistory() {
        Log.d(TAG, "Cargando historial...")
    }

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

                        val achievementsChecker = AchievementsChecker(achievementsViewModel)
                        achievementsChecker.checkParteAchievements(
                            datosFinales,
                            auth.currentUser?.uid ?: ""
                        )

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

    private fun generarResumenBorrador(parte: com.example.juka.domain.model.ParteEnProgreso): String {
        val sb = StringBuilder()

        sb.append("📂 **¡Recuperé tu borrador!**\n")
        sb.append("Esto es lo que tenías guardado:\n\n")

        if (parte.fecha != null) {
            sb.append("📅 **Fecha:** ${parte.fecha}\n")
        }
        if (parte.horaInicio != null || parte.horaFin != null) {
            sb.append("⏰ **Horario:** ${parte.horaInicio ?: "?"} - ${parte.horaFin ?: "?"}\n")
        }

        if (parte.nombreLugar != null) {
            sb.append("📍 **Lugar:** ${parte.nombreLugar}\n")
        }

        if (parte.especiesCapturadas.isNotEmpty()) {
            sb.append("🐟 **Capturas:**\n")
            parte.especiesCapturadas.forEach { pez ->
                sb.append("   • ${pez.numeroEjemplares} ${pez.nombre}\n")
            }
        }

        if (parte.modalidad != null) {
            sb.append("🎣 **Modalidad:** ${parte.modalidad}\n")
        }

        if (parte.imagenes.isNotEmpty()) {
            sb.append("📸 **Fotos:** ${parte.imagenes.size} adjuntas\n")
        }

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
            else -> "..."
        }
    }

    private fun generarMensajeConfirmacionCampo(campo: CampoParte, extraction: MLKitExtractionResult): String {
        return "✅ Información registrada para ${campo.name}"
    }

    // ================== CONTADOR DE PECES ==================

    // ✅ Delegamos el estado al manager
    val contadorPeces: StateFlow<List<EspecieCapturada>> = fishCounterManager.contadorPeces

    // ✅ La lógica de navegación se mantiene acá
    fun iniciarParteDesdeContador() {
        val pecesContados = fishCounterManager.contadorPeces.value
        if (pecesContados.isEmpty()) return

        val datosIniciales = ParteEnProgreso(especiesCapturadas = pecesContados)

        _currentMode.value = ChatMode.CREAR_PARTE
        localStorageHelper.saveParteBorrador(datosIniciales)

        val resumenPeces = pecesContados.joinToString(", ") { "${it.numeroEjemplares} ${it.nombre}" }

        _parteSession.value = ParteSessionChat(
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

        // Limpiamos usando el manager
        fishCounterManager.limpiarContador()
    }
}