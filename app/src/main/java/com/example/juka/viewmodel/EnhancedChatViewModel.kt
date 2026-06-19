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
import com.example.juka.PescadexManager
import com.example.juka.data.AchievementsViewModel
import com.example.juka.data.ActionResult
import com.example.juka.data.ChatBotActionHandler
import com.example.juka.data.ChatBotManager
import com.example.juka.data.ChatOption
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import com.example.juka.data.local.BorradorMeta
import com.example.juka.data.local.LocalStorageHelper
import com.example.juka.data.network.IMAGE_UPLOAD_TIMEOUT_MS
import com.example.juka.data.network.NetworkMonitor
import com.example.juka.data.network.NetworkResult
import com.example.juka.data.network.withNetworkTimeout
import com.example.juka.domain.chat.ChatQuotaManager
import com.example.juka.domain.model.ChatMessageWithMode
import com.example.juka.domain.model.ChatMode
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.MLKitExtractionResult
import com.example.juka.domain.model.ParteEnProgreso
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
    val achievementsViewModel: AchievementsViewModel,
    private val networkMonitor: NetworkMonitor,
    private val pescadexManager: PescadexManager,
    private val application: com.example.juka.HukaApplication
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

    // Datos del parte que se está creando. Cuando es null, no hay parte activo
    // (la UI puede estar mostrando el listado de borradores pendientes).
    // Cualquier modificación pasa por updateParteData(...) para que el borrador
    // se auto-guarde en Room.
    private val _parteData = MutableStateFlow<ParteEnProgreso?>(null)
    val parteData: StateFlow<ParteEnProgreso?> = _parteData.asStateFlow()

    // Mensajes del chat de creación de parte. Viven en memoria mientras dura
    // la pantalla (no se persisten: el chat es efímero, el parte no).
    private val _parteMessages = MutableStateFlow<List<ChatMessageWithMode>>(emptyList())
    val parteMessages: StateFlow<List<ChatMessageWithMode>> = _parteMessages.asStateFlow()

    // ID del borrador activo. Cuando hay un parte siendo editado, este id es
    // la clave en Room: cada updateParteData persiste sobre este id.
    private val _borradorActivoId = MutableStateFlow<String?>(null)
    val borradorActivoId: StateFlow<String?> = _borradorActivoId.asStateFlow()

    // Lista de borradores pendientes (no completados, no descartados). La UI
    // la consume cuando el usuario toca "Crear parte" para ofrecerle elegir uno
    // o crear nuevo. Se refresca después de completar/descartar/guardar.
    private val _borradoresPendientes = MutableStateFlow<List<BorradorMeta>>(emptyList())
    val borradoresPendientes: StateFlow<List<BorradorMeta>> = _borradoresPendientes.asStateFlow()

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

    // Se emite con la lista de especies NUEVAS descubiertas al guardar un
    // parte. La UI del chat lo observa para disparar el modal de celebración
    // del Pescadex (CelebracionNuevaEspecieModal) sin tener que polear.
    private val _nuevasEspeciesEvent =
        MutableSharedFlow<List<com.example.juka.RegistroResult.Success>>()
    val nuevasEspeciesEvent = _nuevasEspeciesEvent.asSharedFlow()

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
        // Antes acá había un chequeo de verificarEncuestaCompletada() que solo
        // loggeaba info y no hacía nada accionable. El estado real de la
        // encuesta lo controla AuthManager + AppWithAuth (si no está completa
        // ni siquiera se llega al chat). Lo sacamos.
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
            addMessageToParteChat(message)
        }
    }

    private fun handleDownload(data: Map<String, String>?) {
        addBotMessage("📥 Función de descarga simulada.")
    }

    /**
     * Entrada al flujo de "Crear parte". Carga los borradores pendientes y
     * deja decidir a la UI:
     *   - Si no hay borradores → arrancamos un parte nuevo limpio.
     *   - Si hay borradores → exponemos la lista en _borradoresPendientes y
     *     dejamos parteData en null. La UI muestra el dialog para que el
     *     usuario elija retomar uno o crear nuevo.
     */
    fun iniciarCrearParte() {
        _currentMode.value = ChatMode.CREAR_PARTE

        viewModelScope.launch {
            val pendientes = localStorageHelper.getAllBorradores()
            _borradoresPendientes.value = pendientes

            if (pendientes.isEmpty()) {
                crearNuevoParte()
            } else {
                // Dejamos parteData en null para que la UI sepa que tiene que
                // mostrar el dialog/lista de borradores.
                _parteData.value = null
                _parteMessages.value = emptyList()
                _borradorActivoId.value = null
            }
        }
    }

    /**
     * Crea un parte nuevo en blanco con su propio id. Llamar desde el dialog
     * de borradores cuando el usuario elige "+ Nuevo parte", o directamente
     * cuando no hay borradores pendientes.
     */
    fun crearNuevoParte() {
        _currentMode.value = ChatMode.CREAR_PARTE
        val nuevoId = localStorageHelper.generarBorradorId()
        _borradorActivoId.value = nuevoId
        _parteData.value = ParteEnProgreso()
        _parteMessages.value = emptyList()

        val bienvenida = """
            🎣 **¡Empecemos tu parte de pesca!**

            Contame en una sola frase lo que recuerdes y yo lo ordeno. Por ejemplo:
            • "Ayer pesqué 3 pejerreyes en Trelew, de 7 a 12"
            • "Salí de costa con 2 cañas, no saqué nada"

            También podés tocar los íconos de arriba (📅 📍 🐟) para ir campo por campo.
        """.trimIndent()

        addMessageToParteChat(
            ChatMessageWithMode(bienvenida, false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
        )
    }

    /**
     * Retoma un borrador específico de la lista de pendientes.
     */
    fun retomarBorradorPorId(borradorId: String) {
        _currentMode.value = ChatMode.CREAR_PARTE
        viewModelScope.launch {
            val borrador = localStorageHelper.getBorrador(borradorId)
            if (borrador == null) {
                // El borrador desapareció (raro, pero por las dudas) — caemos
                // a un parte nuevo en lugar de quedar en estado roto.
                crearNuevoParte()
                return@launch
            }
            _borradorActivoId.value = borradorId
            _parteData.value = borrador
            _parteMessages.value = listOf(
                ChatMessageWithMode(
                    content = generarResumenBorrador(borrador),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = DateUtils.timestampChat(),
                    mode = ChatMode.CREAR_PARTE
                )
            )
        }
    }

    /**
     * Descarta un borrador desde el dialog (sin abrirlo). Útil para limpiar
     * partes viejos que el usuario no piensa retomar.
     */
    fun descartarBorrador(borradorId: String) {
        viewModelScope.launch {
            localStorageHelper.deleteBorrador(borradorId)
            _borradoresPendientes.value = localStorageHelper.getAllBorradores()
            // Si quedaba uno solo y lo descartó, arrancamos parte nuevo.
            if (_borradoresPendientes.value.isEmpty() && _currentMode.value == ChatMode.CREAR_PARTE && _parteData.value == null) {
                crearNuevoParte()
            }
        }
    }

    /**
     * Guarda el parte actual como borrador y vuelve al menú general SIN
     * descartar. El parte queda en la lista para retomarlo después.
     * (El auto-save ya persistió el último estado, así que esta función
     * sólo limpia el state in-memory y refresca la lista.)
     */
    fun guardarBorradorYVolver() {
        if (_parteData.value == null) return
        _parteData.value = null
        _parteMessages.value = emptyList()
        _borradorActivoId.value = null
        viewModelScope.launch {
            _borradoresPendientes.value = localStorageHelper.getAllBorradores()
        }
        _currentMode.value = ChatMode.GENERAL
        showMainMenu()
    }

    fun volverAChatGeneral() {
        _currentMode.value = ChatMode.GENERAL
        showMainMenu()
    }

    fun cancelarParte() {
        // El usuario descartó el parte explícitamente: borramos ESTE borrador
        // específico del Room (los demás borradores no se tocan).
        val idActivo = _borradorActivoId.value
        _parteData.value = null
        _parteMessages.value = emptyList()
        _borradorActivoId.value = null
        if (idActivo != null) {
            viewModelScope.launch {
                localStorageHelper.deleteBorrador(idActivo)
                _borradoresPendientes.value = localStorageHelper.getAllBorradores()
            }
        }
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
        // Solo se procesan imágenes en el flujo de CREAR_PARTE (la foto se
        // asocia al parte). El botón de imagen del chat general fue removido
        // porque no había procesamiento real (no Gemini Vision, no upload).
        // Para identificar especies está la pantalla "Identificar pez".
        when (_currentMode.value) {
            ChatMode.CREAR_PARTE -> sendParteImageMessage(imagePath)
            ChatMode.GENERAL -> {
                Log.w(
                    TAG,
                    "sendImageMessage llamado en modo GENERAL — el botón debería estar oculto. Ignorando."
                )
            }
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

    // NOTA: `sendGeneralImageMessage` fue eliminado. Solo guardaba la imagen
    // en memoria interna sin procesarla, y el botón que la disparaba se sacó
    // del chat general (ver `EnhancedMessageInput` en ChatInputs.kt).

    private fun sendParteTextMessage(content: String) {
        val userMessage = ChatMessageWithMode(content, true, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
        addMessageToParteChat(userMessage)

        if (_waitingForFieldResponse.value == CampoParte.OBSERVACIONES) {
            actualizarObservaciones(content)
            return
        }
        procesarEntradaInteligente(content)
    }

    private fun sendParteAudioMessage(transcript: String) {
        val userMessage = ChatMessageWithMode("🎤 \"$transcript\"", true, MessageType.AUDIO, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
        addMessageToParteChat(userMessage)
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
                    if (_parteData.value != null) {
                        // Marcamos el parte como "sin capturas" y vaciamos la lista por las dudas
                        _currentFieldInProgress.value = null
                        _waitingForFieldResponse.value = null
                        updateParteData { it.copy(sinCapturas = true, especiesCapturadas = emptyList()) }

                        delay(1000)
                        addBotMessage(
                            "📝 **¡Anotado!**\n\n" +
                                    "Los días sin pique también son datos valiosos para cuidar el ecosistema. 🌎\n\n" +
                                    generarResumenProgreso(_parteData.value)
                        )
                    }
                } else {
                    // 2. Si pescó algo, seguimos el flujo normal con ML Kit / Extractor
                    val extractionResult = mlKitManager.extraerInformacionPesca(texto)
                    val nuevosData = mlKitManager.convertirEntidadesAParteDatos(extractionResult.entidadesDetectadas)

                    if (_parteData.value != null) {
                        if (extractionResult.entidadesDetectadas.isNotEmpty()) {
                            _currentFieldInProgress.value = null
                            _waitingForFieldResponse.value = null
                        }

                        updateParteData { actual -> parteLogicUseCase.mergearDatos(actual, nuevosData) }

                        delay(1000)
                        val respuestaBot = generarRespuestaParte(extractionResult, _parteData.value)
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
                updateParteData { it.copy(imagenes = it.imagenes + pathSeguro) }

                val userMessage = ChatMessageWithMode(
                    content = pathSeguro,
                    isFromUser = true,
                    type = MessageType.IMAGE,
                    timestamp = DateUtils.timestampChat(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteChat(userMessage)

                delay(1000)
                addBotMessage(
                    "📸 **Foto agregada al parte.**\n\n" +
                            generarResumenProgreso(_parteData.value)
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
        addMessageToParteChat(pregunta)

        if (campo == CampoParte.UBICACION) _showMapPicker.value = true
        if (campo == CampoParte.FOTOS) _showImagePicker.value = true
    }
    fun guardarParteDesdeWizard(parteData: ParteEnProgreso) {
        // Tomamos los datos del wizard y los pasamos por la misma infra del chat:
        // se persisten como borrador en Room hasta que el upload remoto sale OK.
        val nuevoId = localStorageHelper.generarBorradorId()
        _borradorActivoId.value = nuevoId
        _parteData.value = parteData.copy(porcentajeCompletado = 100)
        _parteMessages.value = emptyList()
        _currentMode.value = ChatMode.CREAR_PARTE
        viewModelScope.launch {
            localStorageHelper.saveBorrador(nuevoId, _parteData.value!!)
            // Reutilizamos la lógica existente de subida a Firebase + imágenes
            completarYEnviarParte()
        }
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
        if (_parteData.value == null) return
        updateParteData { it.copy(observaciones = content) }
        addBotMessage("📝 **Notas anotadas.**")
        _currentFieldInProgress.value = null
        _waitingForFieldResponse.value = null
    }

    private fun actualizarDatosPartePorCampo(campo: CampoParte, extractionResult: MLKitExtractionResult) {
        if (_parteData.value == null) return
        updateParteData { actual ->
            parteLogicUseCase.actualizarDatosPorCampo(actual, campo, extractionResult)
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

    /**
     * Agrega un mensaje al chat de creación de parte. Sólo agrega si hay un
     * parte activo (parteData != null) — de lo contrario se descarta para no
     * acumular mensajes huérfanos.
     */
    private fun addMessageToParteChat(message: ChatMessageWithMode) {
        if (_parteData.value == null) return
        _parteMessages.value = _parteMessages.value + message
    }

    /**
     * Actualiza el ParteEnProgreso aplicando una transformación, recalcula el
     * progreso y persiste el resultado como borrador en Room (asíncrono,
     * fire-and-forget). Es el único lugar donde se debe modificar _parteData
     * mientras hay un parte activo.
     */
    private fun updateParteData(transform: (ParteEnProgreso) -> ParteEnProgreso) {
        val actual = _parteData.value ?: return
        val idActivo = _borradorActivoId.value ?: return
        val transformado = transform(actual)
        val progreso = parteLogicUseCase.calcularProgreso(transformado)
        val finales = transformado.copy(
            porcentajeCompletado = progreso.porcentaje,
            camposFaltantes = progreso.camposFaltantes
        )
        // Update sincrónico del StateFlow: la UI ve el cambio inmediato.
        _parteData.value = finales
        // Persistencia async sobre el borrador activo. Si la app se cierra
        // antes de que termine, el siguiente save igual sobrescribe.
        viewModelScope.launch { localStorageHelper.saveBorrador(idActivo, finales) }
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
        val parteActual = _parteData.value ?: return
        val idActivo = _borradorActivoId.value

        if (parteActual.porcentajeCompletado < 70) {
            addBotMessage(
                "⚠️ **Faltan datos para enviar el parte.**\n\n" +
                        "Tenés que completar al menos el 70%. " +
                        "Tocá los íconos de arriba para sumar lo que falta."
            )
            return
        }

        // Chequeo upfront: si no hay red, no vale la pena ni intentar —
        // vamos directo al borrador con mensaje claro. Esto evita el caso
        // típico de quedar girando el spinner sin feedback.
        if (!networkMonitor.isOnlineNow()) {
            viewModelScope.launch {
                guardarComoBorradorOffline(idActivo, parteActual, motivo = OfflineMotivo.SIN_SENAL)
            }
            return
        }

        _firebaseStatus.value = "Intentando subir..."
        viewModelScope.launch {
            _isSendingParte.value = true
            try {
                // 1. Subir imágenes con timeout por cada una. Mostramos
                //    progreso para que el usuario vea que algo pasa.
                val totalImgs = parteActual.imagenes.size
                val urlsRemotas = mutableListOf<String>()
                parteActual.imagenes.forEachIndexed { idx, pathLocal ->
                    if (pathLocal.startsWith("http")) {
                        urlsRemotas.add(pathLocal)
                        return@forEachIndexed
                    }
                    if (totalImgs > 0) {
                        _firebaseStatus.value = "Subiendo foto ${idx + 1}/$totalImgs..."
                    }
                    val res = withNetworkTimeout(
                        monitor = networkMonitor,
                        timeoutMillis = IMAGE_UPLOAD_TIMEOUT_MS
                    ) { storageService.subirImagen(pathLocal) }

                    when (res) {
                        is NetworkResult.Success -> {
                            val url = res.data ?: throw Exception("upload-null")
                            urlsRemotas.add(url)
                        }
                        is NetworkResult.NoConnection -> {
                            guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.SIN_SENAL)
                            return@launch
                        }
                        is NetworkResult.Timeout -> {
                            guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.TIMEOUT)
                            return@launch
                        }
                        is NetworkResult.Error -> {
                            guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.ERROR)
                            return@launch
                        }
                    }
                }

                val datosFinales = parteActual.copy(imagenes = urlsRemotas)

                val achievementsChecker = AchievementsChecker(achievementsViewModel)
                achievementsChecker.checkParteAchievements(
                    datosFinales,
                    auth.currentUser?.uid ?: ""
                )

                _firebaseStatus.value = "Sincronizando..."
                val transcripcion = com.example.juka.data.firebase.UtilsFirebase
                    .extraerTranscripcion(_parteMessages.value)

                // 2. Guardar el parte en Firestore con timeout.
                val resPersist = withNetworkTimeout(networkMonitor) {
                    firebaseManager.guardarParteCompletado(datosFinales, transcripcion)
                }

                when (resPersist) {
                    is NetworkResult.NoConnection -> {
                        guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.SIN_SENAL)
                        return@launch
                    }
                    is NetworkResult.Timeout -> {
                        guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.TIMEOUT)
                        return@launch
                    }
                    is NetworkResult.Error -> {
                        guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.ERROR)
                        return@launch
                    }
                    is NetworkResult.Success -> {
                        if (resPersist.data !is FirebaseResult.Success) {
                            guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.ERROR)
                            return@launch
                        }
                    }
                }

                _firebaseStatus.value = "¡Subido!"
                val eventoId = java.util.UUID.randomUUID().toString()
                _parteSavedEvent.emit(Pair(eventoId, datosFinales))

                // Auto-registro en Pescadex (best-effort, no bloquea el flujo).
                // Pasamos cantidad por especie y fecha del parte para que el
                // Pescadex pueda mantener el récord de "mejor día" por especie.
                try {
                    val resultadosPescadex = pescadexManager.registrarEspeciesDeParte(
                        especiesDelParte = datosFinales.especiesCapturadas.map {
                            com.example.juka.EspecieDelParte(
                                nombre = it.nombre,
                                cantidad = it.numeroEjemplares
                            )
                        },
                        locacion = datosFinales.nombreLugar,
                        fechaParte = datosFinales.fecha
                    )
                    // Si hubo especies nuevas, las emitimos por un SharedFlow
                    // para que la UI del chat dispare la celebración.
                    val nuevas = resultadosPescadex
                        .filterIsInstance<com.example.juka.RegistroResult.Success>()
                        .filter { it.esNuevaEspecie }
                    if (nuevas.isNotEmpty()) {
                        _nuevasEspeciesEvent.emit(nuevas)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo actualizar Pescadex: ${e.message}")
                }

                _isSendingParte.value = false
                addMessageToParteChat(ChatMessageWithMode(
                    "🎉 **¡Parte enviado!**\n\nQuedó guardado en la nube. Gracias por sumar tu jornada. 🎣",
                    false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE
                ))
                if (idActivo != null) localStorageHelper.deleteBorrador(idActivo)
                _borradorActivoId.value = null
                _parteData.value = null
                _parteMessages.value = emptyList()
                _borradoresPendientes.value = localStorageHelper.getAllBorradores()
                delay(2000)
                volverAChatGeneral()
            } catch (e: Exception) {
                // Cualquier otra excepción inesperada cae acá. Tratamos como
                // "error" — el borrador queda persistido para reintento.
                Log.e(TAG, "Error inesperado enviando parte: ${e.message}")
                guardarComoBorradorOffline(idActivo, parteActual, OfflineMotivo.ERROR)
            }
        }
    }

    /**
     * Razones por las cuales no pudimos subir el parte. Cada una merece
     * un mensaje distinto en el chat para no confundir al usuario.
     */
    private enum class OfflineMotivo { SIN_SENAL, TIMEOUT, ERROR }

    /**
     * Persiste el parte como borrador local y muestra al usuario un mensaje
     * acorde al motivo. Termina el flujo de envío (resetea spinners) pero
     * NO sale del modo "crear parte" — el usuario puede tocar "Enviar"
     * de nuevo cuando vea el banner de "Conexión restablecida".
     */
    private suspend fun guardarComoBorradorOffline(
        idActivo: String?,
        parteActual: ParteEnProgreso,
        motivo: OfflineMotivo
    ) {
        if (idActivo != null) {
            localStorageHelper.saveBorrador(idActivo, parteActual)
        }
        // Encolar sync para cuando vuelva la red (aunque el job ya exista
        // con KEEP, si completó se vuelve a registrar correctamente).
        application.programarSyncBorradores()
        _isSendingParte.value = false
        _firebaseStatus.value = when (motivo) {
            OfflineMotivo.SIN_SENAL -> "Guardado sin conexión"
            OfflineMotivo.TIMEOUT -> "Guardado (red lenta)"
            OfflineMotivo.ERROR -> "Guardado localmente"
        }
        val mensaje = when (motivo) {
            OfflineMotivo.SIN_SENAL -> "📶 **No tenés conexión a internet.**\n\n" +
                    "Tu parte quedó guardado en este celular como **borrador**.\n" +
                    "Cuando vuelva la conexión vas a ver el aviso arriba: entrá de nuevo a 'Crear parte' y tocá **Enviar**."
            OfflineMotivo.TIMEOUT -> "⏳ **La red está lenta y se cortó el envío.**\n\n" +
                    "Tu parte quedó guardado en este celular como **borrador**.\n" +
                    "Probá enviarlo de nuevo más tarde cuando tengas mejor señal."
            OfflineMotivo.ERROR -> "⚠️ **No pudimos subir el parte ahora mismo.**\n\n" +
                    "Lo guardé en este celular como **borrador** así no perdés nada.\n" +
                    "Volvé a 'Crear parte' más tarde y tocá **Enviar**."
        }
        addMessageToParteChat(ChatMessageWithMode(
            mensaje, false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE
        ))
        _borradoresPendientes.value = localStorageHelper.getAllBorradores()
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
        if (_parteData.value == null) return
        val geoPoint = GeoPoint(latitude, longitude)
        val locationName = name ?: "Ubicación sin nombre"

        updateParteData { it.copy(ubicacion = geoPoint, nombreLugar = locationName) }
        addMessageToParteChat(
            ChatMessageWithMode("📍 **Lugar:** $locationName", false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE)
        )
    }

    fun getConversationStats(): String {
        val generalCount = _generalMessages.value.size
        val parteCount = _parteMessages.value.size
        return "📊 General: $generalCount | Parte: $parteCount"
    }

    private fun obtenerEjemploPorCampo(campo: CampoParte): String = when (campo) {
        CampoParte.FECHA -> "\"hoy\", \"ayer\" o una fecha como \"23/02/2024\""
        CampoParte.HORARIOS -> "\"de 6 a 11\" o \"empecé 7am, terminé 13hs\""
        CampoParte.UBICACION -> "\"Playa Unión\" o tocá el botón del mapa 📍"
        CampoParte.ESPECIES -> "\"2 pejerreyes y 1 róbalo\""
        CampoParte.MODALIDAD -> "\"de costa\", \"embarcado\", \"con red\", \"submarina costa\", \"submarina embarcado\", o describime cualquier otra forma"
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
        val nuevoId = localStorageHelper.generarBorradorId()

        _currentMode.value = ChatMode.CREAR_PARTE
        _borradorActivoId.value = nuevoId
        _parteData.value = datosIniciales

        val resumenPeces = pecesContados.joinToString("\n") { pez ->
            "   • ${pez.numeroEjemplares} ${formatearValor(pez.nombre)}"
        }

        _parteMessages.value = listOf(
            ChatMessageWithMode(
                "🎣 **Parte iniciado desde el contador.**\n\n" +
                        "Ya cargué tus capturas:\n$resumenPeces\n\n" +
                        "Ahora contame el resto: ¿dónde pescaste y qué fecha?",
                false, MessageType.TEXT, DateUtils.timestampChat(), ChatMode.CREAR_PARTE
            )
        )

        // Persistimos el borrador inicial y limpiamos el contador.
        viewModelScope.launch {
            localStorageHelper.saveBorrador(nuevoId, datosIniciales)
        }
        fishCounterManager.limpiarContador()
    }
    suspend fun loadAllSpecies(): List<FishInfo> {
        fishDatabase.initialize()
        return fishDatabase.getAllSpecies()
    }
}