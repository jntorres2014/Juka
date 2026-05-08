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
import com.example.juka.FishInfo
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
import com.example.juka.util.DateUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    val fishCounterManager: FishCounterManager,
    val achievementsViewModel: AchievementsViewModel
) : ViewModel() {

    val quotaState = quotaManager.quotaState
    private val _isSendingParte = MutableStateFlow(false)
    val isSendingParte: StateFlow<Boolean> = _isSendingParte
    val newAchievementUnlocked = achievementsViewModel.newAchievementUnlocked
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

    private val _parteSavedEvent = MutableSharedFlow<Pair<String, ParteEnProgreso>>()
    val parteSavedEvent = _parteSavedEvent.asSharedFlow()
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
            timestamp = DateUtils.timestampChat(),
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
            timestamp = DateUtils.timestampChat(),
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
                            timestamp = DateUtils.timestampChat(),
                            mode = ChatMode.CREAR_PARTE
                        )
                    )
                )
            } else {
                _parteSession.value = ParteSessionChat()
                val bienvenida = """
                    🎣 **¡Empecemos tu parte de pesca!**

                    Contame en una sola frase lo que recuerdes y yo lo ordeno. Por ejemplo:
                    • "Ayer pesqué 3 pejerreyes en Trelew, de 7 a 12"
                    • "Salí de costa con 2 cañas, no saqué nada"

                    También podés tocar los íconos de arriba (📅 📍 🐟) para ir campo por campo.
                """.trimIndent()

                val msg = ChatMessageWithMode(bienvenida, false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
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
        addBotMessage("Listo, **cancelé el parte**.\nVolviste al menú principal. 👋")
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
                    DateUtils.timestampChat(),
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
        val userMessage = ChatMessageWithMode(content, true, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
        addMessageToParteSession(userMessage)

        if (_waitingForFieldResponse.value == CampoParte.OBSERVACIONES) {
            actualizarObservaciones(content)
            return
        }
        procesarEntradaInteligente(content)
    }

    private fun sendParteAudioMessage(transcript: String) {
        val userMessage = ChatMessageWithMode("🎤 \"$transcript\"", true, MessageType.AUDIO, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
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
                        addBotMessage(
                            "📝 **¡Anotado!**\n\n" +
                                    "Los días sin pique también son datos valiosos para cuidar el ecosistema. 🌎\n\n" +
                                    generarResumenProgreso(_parteSession.value?.parteData)
                        )
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
                addBotMessage("⚠️ Me costó entender eso. ¿Podés repetirlo de otra forma?")
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
                    timestamp = DateUtils.timestampChat(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(userMessage)

                delay(1000)
                addBotMessage(
                    "📸 **Foto agregada al parte.**\n\n" +
                            generarResumenProgreso(_parteSession.value?.parteData)
                )

            } else {
                addBotMessage("⚠️ No pude guardar la imagen. Probá de nuevo.")
            }

            _isAnalyzing.value = false
        }
    }

    fun onCampoParteSelected(campo: CampoParte) {
        _currentFieldInProgress.value = campo
        _waitingForFieldResponse.value = campo

        val pregunta = ChatMessageWithMode(
            content = campo.pregunta,
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = DateUtils.timestampChat(),
            mode = ChatMode.CREAR_PARTE,
            metadata = mapOf("fieldType" to campo.name)
        )
        addMessageToParteSession(pregunta)

        if (campo == CampoParte.UBICACION) _showMapPicker.value = true
        if (campo == CampoParte.FOTOS) _showImagePicker.value = true
    }
    fun guardarParteDesdeWizard(parteData: ParteEnProgreso) {
        // Armamos una sesión mínima con los datos del wizard
        _parteSession.value = ParteSessionChat(
            parteData = parteData.copy(porcentajeCompletado = 100),
            messages = emptyList(),
            estado = EstadoParte.EN_PROGRESO
        )
        _currentMode.value = ChatMode.CREAR_PARTE
        // Reutilizamos la lógica existente de subida a Firebase + imágenes
        completarYEnviarParte()
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
                        val nombre = nombreLimpioCampo(campo)
                        addBotMessage(
                            "🤔 No te entendí el dato de **$nombre**.\n\n" +
                                    "Probá así: ${obtenerEjemploPorCampo(campo)}"
                        )
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
            addBotMessage("📝 **Notas anotadas.**")
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
        val msg = ChatMessageWithMode(content, true, type, DateUtils.timestampChat(), ChatMode.GENERAL)
        addMessageToGeneralChat(msg)
        saveGeneralMessageToFile(msg)
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
            return "No te entendí del todo 🤔\n\n" +
                    "Probá contarme algo como:\n" +
                    "• \"Pesqué 2 pejerreyes ayer en Trelew\"\n" +
                    "• \"Salí de costa de 6 a 11\"\n" +
                    "• \"Usé 2 cañas con masa\""
        }
        val respuesta = StringBuilder()
        respuesta.append("Anoté esto de tu mensaje:\n\n")
        extractionResult.entidadesDetectadas.forEach { entity ->
            val (emoji, label) = etiquetaEntidad(entity.tipo)
            val valor = formatearValor(entity.valor)
            respuesta.append("$emoji **$label:** $valor\n")
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
            val faltantes = progreso.camposFaltantes.joinToString(", ") { campo ->
                campo.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
            resumen.append("Te falta: $faltantes")
        } else {
            resumen.append("🎉 **¡Tu parte está listo para enviar!**")
        }
        return resumen.toString()
    }

    // ─────────── Helpers de formato para los mensajes del bot ───────────

    /** Devuelve (emoji, etiqueta legible) para cada tipo de entidad ML Kit. */
    private fun etiquetaEntidad(tipo: String): Pair<String, String> = when (tipo) {
        "FECHA" -> "📅" to "Fecha"
        "FECHA_HORA" -> "📅" to "Fecha y hora"
        "HORA_INICIO" -> "⏰" to "Hora de inicio"
        "HORA_FIN" -> "⏰" to "Hora de fin"
        "HORA" -> "⏰" to "Hora"
        "LUGAR" -> "📍" to "Lugar"
        "PROVINCIA" -> "🗺️" to "Provincia"
        "MODALIDAD" -> "🎣" to "Modalidad"
        "ESPECIE" -> "🐟" to "Especie"
        "CANTIDAD_PECES" -> "📊" to "Cantidad"
        "NUMERO_CANAS" -> "🎯" to "Cañas"
        else -> "ℹ️" to tipo.replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    /** Si un valor viene en MAYÚSCULAS (típico de ML Kit), lo pasamos a Title Case. */
    private fun formatearValor(valor: String): String {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return limpio
        val esTodoMayus = limpio.any { it.isLetter() } && limpio == limpio.uppercase()
        return if (esTodoMayus) {
            limpio.split(" ").joinToString(" ") { palabra ->
                if (palabra.length <= 2) palabra.lowercase()
                else palabra.lowercase().replaceFirstChar { it.uppercase() }
            }
        } else limpio
    }

    /** Nombre del campo sin emojis, listo para mostrar en oraciones. */
    private fun nombreLimpioCampo(campo: CampoParte): String =
        campo.displayName.replace(Regex("[^a-zA-ZáéíóúñÁÉÍÓÚÑ ]"), "").trim()

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
                            // DESPUÉS — emit() garantiza entrega
                            val eventoId = java.util.UUID.randomUUID().toString()
                            _parteSavedEvent.emit(Pair(eventoId, datosFinales))
                            _isSendingParte.value = false
                            addMessageToParteSession(ChatMessageWithMode(
                                "🎉 **¡Parte enviado!**\n\nQuedó guardado en la nube. Gracias por sumar tu jornada. 🎣",
                                false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE
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
                            false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE
                        ))
                    }
                }
            } else {
                addBotMessage(
                    "⚠️ **Faltan datos para enviar el parte.**\n\n" +
                            "Tenés que completar al menos el 70%. " +
                            "Tocá los íconos de arriba para sumar lo que falta."
                )
            }
        }
    }

    fun habilitarChat() {
        _chatEnabled.value = true
        addMessageToGeneralChat(ChatMessageWithMode("💬 **Chat activado**", false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.GENERAL))
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
                sb.append("   • ${pez.numeroEjemplares} ${formatearValor(pez.nombre)}\n")
            }
        }

        if (parte.modalidad != null) {
            sb.append("🎣 **Modalidad:** ${parte.modalidad.displayName}\n")
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
            addMessageToParteSession(ChatMessageWithMode("📍 **Lugar:** $locationName", false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE))
        }
    }

    fun retomarBorrador(session: ParteSessionChat) {
        _currentMode.value = ChatMode.CREAR_PARTE
        _parteSession.value = session.copy(estado = EstadoParte.EN_PROGRESO)
        addMessageToParteSession(ChatMessageWithMode("🔄 **Retomamos el borrador.** Seguimos donde habíamos quedado.", false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE))
    }

    fun getConversationStats(): String {
        val generalCount = _generalMessages.value.size
        val parteCount = _parteSession.value?.messages?.size ?: 0
        return "📊 General: $generalCount | Parte: $parteCount"
    }

    private fun obtenerEjemploPorCampo(campo: CampoParte): String = when (campo) {
        CampoParte.FECHA -> "\"hoy\", \"ayer\" o una fecha como \"23/02/2024\""
        CampoParte.HORARIOS -> "\"de 6 a 11\" o \"empecé 7am, terminé 13hs\""
        CampoParte.UBICACION -> "\"Playa Unión\" o tocá el botón del mapa 📍"
        CampoParte.ESPECIES -> "\"2 pejerreyes y 1 róbalo\""
        CampoParte.MODALIDAD -> "\"de costa\", \"embarcado\" o \"submarina\""
        CampoParte.CANAS -> "\"con 3 cañas\" o el número directo"
        CampoParte.OBSERVACIONES -> "\"llovía, viento del sur, carnada masa\""
        CampoParte.FOTOS -> "tocá el botón de cámara 📸"
    }

    private fun generarMensajeConfirmacionCampo(campo: CampoParte, extraction: MLKitExtractionResult): String {
        val nombre = nombreLimpioCampo(campo)
        val detalles = extraction.entidadesDetectadas
            .joinToString(", ") { formatearValor(it.valor) }
            .takeIf { it.isNotBlank() }
        return if (detalles != null) {
            "✅ **$nombre:** $detalles"
        } else {
            "✅ Listo, anoté tu **$nombre**."
        }
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

        val resumenPeces = pecesContados.joinToString("\n") { pez ->
            "   • ${pez.numeroEjemplares} ${formatearValor(pez.nombre)}"
        }

        _parteSession.value = ParteSessionChat(
            parteData = datosIniciales,
            messages = listOf(
                ChatMessageWithMode(
                    "🎣 **Parte iniciado desde el contador.**\n\n" +
                            "Ya cargué tus capturas:\n$resumenPeces\n\n" +
                            "Ahora contame el resto: ¿dónde pescaste y qué fecha?",
                    false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE
                )
            )
        )

        // Limpiamos usando el manager
        fishCounterManager.limpiarContador()
    }
    suspend fun loadAllSpecies(): List<FishInfo> {
        fishDatabase.initialize()
        return fishDatabase.getAllSpecies()
    }
}