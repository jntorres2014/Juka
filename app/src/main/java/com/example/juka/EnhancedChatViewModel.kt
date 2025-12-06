// EnhancedChatViewModel.kt - ViewModel mejorado con dos modos de chat
package com.example.juka

import GeminiChatService
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.ActionResult
import com.example.juka.data.ChatBotActionHandler
import com.example.juka.data.ChatBotManager
import com.example.juka.data.ChatOption
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import com.example.juka.domain.chat.ChatQuotaManager
import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.MessageType
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EnhancedChatViewModel(application: Application) : AndroidViewModel(application) {
    private val quotaManager = ChatQuotaManager(application)
    private val geminiService = GeminiChatService()
    val quotaState = quotaManager.quotaState
    // Estados principales
    private val _currentMode = MutableStateFlow(ChatMode.GENERAL)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()
    private val _chatEnabled = MutableStateFlow(false)
    val chatEnabled: StateFlow<Boolean> = _chatEnabled.asStateFlow()
    // Chat general (como antes)
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

    // Managers
    private val mlKitManager = MLKitManager(getApplication())
    private val fishDatabase = FishDatabase(getApplication())
    private val intelligentResponses = IntelligentResponses(fishDatabase)
    private val firebaseManager = FirebaseManager(getApplication())
    // ================== CHATBOT MANAGERS ==================
    private val chatBotManager = ChatBotManager(application)
    private val chatBotActionHandler = ChatBotActionHandler(application).apply {
        // Configurar callbacks
        onStartParte = { iniciarCrearParte() }
        onDownloadFile = { data -> handleDownload(data) }
    }

    // Exponer estados del chatbot
/*    val showMapPicker = chatBotActionHandler.showMapPicker
    val showImagePicker = chatBotActionHandler.showImagePicker
    val navigationEvent = chatBotActionHandler.navigationEvent*/
    private val _showMapPicker = MutableStateFlow(false)
    val showMapPicker: StateFlow<Boolean> = _showMapPicker.asStateFlow()  // ← este es el bueno

    private val _showImagePicker = MutableStateFlow(false)
    val showImagePicker: StateFlow<Boolean> = _showImagePicker.asStateFlow()  // ← este es el bueno

    fun dismissMapPicker() {
        _showMapPicker.value = false
    }

    fun dismissImagePicker() {
        _showImagePicker.value = false
    }

    // ================== FUNCIONES DEL CHATBOT (SIMPLIFICADAS) ==================

    fun showMainMenu() {
        val node = chatBotManager.getMainMenu()
        val message = ChatMessageWithMode(
            content = node.message,
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL,
            options = node.options
        )
        addMessageToGeneralChat(message)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun handleOptionClick(option: ChatOption) {
        // Agregar mensaje del usuario
        val userMessage = ChatMessageWithMode(
            content = option.label.removePrefix(option.icon ?: "").trim(),
            isFromUser = true,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )
        addMessageToGeneralChat(userMessage)

        // Procesar la acción
        when (val result = chatBotActionHandler.handleAction(option)) {
            is ActionResult.Navigate -> {
                chatBotManager.currentNode.value?.id?.let {
                    chatBotManager.pushToNavigationStack(it)
                }
                chatBotManager.navigateToNode(result.nodeId)?.let { node ->
                    addBotMessage(node.message, node.options)
                }
            }

            is ActionResult.Back -> {
                chatBotManager.navigateBack()?.let { node ->
                    addBotMessage(node.message, node.options)
                }
            }

            is ActionResult.Home -> {
                showMainMenu()
            }

            is ActionResult.Error -> {
                addBotMessage("❌ ${result.message}")
            }

            // Otros casos ya se manejan en los callbacks
            else -> {
                // NUEVO: Verificar si es la opción de habilitar chat
                if (option.label.contains("Consultar a Huka") ||
                    option.label.contains("Consultar") ||
                    option.label.contains("Chat")) {
                    habilitarChat()
                }
            }
        }
    }

    private fun addBotMessage(content: String, options: List<ChatOption>? = null) {
        val message = ChatMessageWithMode(
            content = content,
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL,
            options = options
        )
        addMessageToGeneralChat(message)
    }

    private fun handleDownload(data: Map<String, String>?) {
        // Tu lógica de descarga
        addBotMessage("📥 Iniciando descarga...")
    }

//    fun dismissMapPicker() = chatBotActionHandler.dismissMapPicker()
  //  fun dismissImagePicker() = chatBotActionHandler.dismissImagePicker()


    // Archivos
    private val generalChatFile =
        File(getApplication<Application>().filesDir, "general_chat_history.txt")
    private val partesSessionsFile =
        File(getApplication<Application>().filesDir, "partes_sessions.txt")

    companion object {
        private const val TAG = "🎣 EnhancedChatViewModel"
    }

    init {
        android.util.Log.d(TAG, "✅ Inicializando EnhancedChatViewModel con botones tipo Telegram")

        // Inicializar base de datos
        viewModelScope.launch {
            try {
                fishDatabase.initialize()
                android.util.Log.i(TAG, "✅ Base de datos de peces inicializada")

                val resultadoEncuesta = firebaseManager.verificarEncuestaCompletada()
                val encuestaCompleta = when (resultadoEncuesta) {
                    is FirebaseResult.Success -> true
                    is FirebaseResult.Error -> false
                    is FirebaseResult.Loading -> false
                }

                if (!encuestaCompleta) {
                    Log.i(TAG, "✅ Mensaje de bienvenida con encuesta")
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error inicializando base de datos: ${e.message}")
            }
        }
        _chatEnabled.value = true

        // Cargar historial del chat general
        loadGeneralChatHistory()

        // Mostrar menú principal si no hay mensajes
        if (_generalMessages.value.isEmpty()) {
            showMainMenu()  // Esto muestra el menú con botones
        }
    }
    // ================== FUNCIONES DE NAVEGACIÓN ENTRE MODOS ==================

    /**
     * Cambiar al modo crear parte
     */
    fun iniciarCrearParte() {
        android.util.Log.d(TAG, "🆕 Iniciando modo CREAR_PARTE")

        _currentMode.value = ChatMode.CREAR_PARTE

        // Crear nueva sesión de parte
        val nuevaSession = ParteSessionChat()
        _parteSession.value = nuevaSession

        // Mensaje inicial del modo parte
        val mensajeBienvenida = ChatMessageWithMode(
            content = """
🎣 **Modo Crear Parte **

Vamos a registrar tu jornada de pesca paso a paso.

Contame todo sobre tu pesca:
• 📅 Fecha y horarios
• 📍 Lugar y provincia  
• 🎣 Modalidad (costa/embarcado/etc)
• 🐟 Especies capturadas
• 📊 Cantidades y detalles

**Empezá contándome:** ¿Cuándo y dónde pescaste?
            """.trimIndent(),
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE
        )

        addMessageToParteSession(mensajeBienvenida)

        //android.util.Log.i(TAG, "✅ Sesión de parte creada: ${nuevaSession.sessionId}")
    }

    /**
     * Volver al chat general
     */
    fun volverAChatGeneral() {
        android.util.Log.d(TAG, "🔙 Volviendo a modo GENERAL")
        _currentMode.value = ChatMode.GENERAL
        showMainMenu()
        // Mantener la sesión de parte para poder retomarla después
        // No la eliminamos, solo cambiamos el modo
    }

    /**
     * Cancelar la creación del parte actual
     */
    fun openMapPicker() {
        _showMapPicker.value = true
    }
    fun cancelarParte() {
        android.util.Log.d(TAG, "❌ Cancelando parte actual")

        _parteSession.value?.let { session ->
            val sessionCancelada = session.copy(estado = EstadoParte.CANCELADO)
            //guardarParteSession(sessionCancelada)
        }

        _parteSession.value = null
        _currentMode.value = ChatMode.GENERAL

        // Mensaje en chat general
        val mensajeCancelacion = ChatMessageWithMode(
            content = "❌ **Parte cancelado**\n\nVolviste al chat general. Si querés crear otro parte, toca el botón 'Crear Parte' nuevamente.",
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )

        addMessageToGeneralChat(mensajeCancelacion)
    }

    // ================== FUNCIONES DE ENVÍO DE MENSAJES ==================

    /**
     * Enviar mensaje de texto (se dirige al modo actual)
     */
    fun sendTextMessage(content: String) {
        when (_currentMode.value) {
            ChatMode.GENERAL -> sendGeneralTextMessage(content)
            ChatMode.CREAR_PARTE -> sendParteTextMessage(content)
        }
    }

    /**
     * Enviar mensaje de audio (se dirige al modo actual)
     */
    fun sendAudioTranscript(transcript: String) {
        when (_currentMode.value) {
            ChatMode.GENERAL -> sendGeneralAudioMessage(transcript)
            //Este es para que ande los campos seleccionados
            //ChatMode.CREAR_PARTE -> sendParteTextMessage(transcript)
             //Este es para que tome todo lo que el usuario diga
            ChatMode.CREAR_PARTE -> sendParteAudioMessage(transcript)
        }
    }

    /**
     * Enviar imagen (se dirige al modo actual)
     */
    fun sendImageMessage(imagePath: String) {
        when (_currentMode.value) {
            ChatMode.GENERAL -> sendGeneralImageMessage(imagePath)
            ChatMode.CREAR_PARTE -> sendParteImageMessage(imagePath)
        }
    }

    // ================== CHAT GENERAL ==================

   /* private fun sendGeneralTextMessage(content: String) {
        android.util.Log.d(TAG, "💬 Mensaje general: '$content'")
        if (chatBotManager.isMenuRequest(content)) {
            showMainMenu()
            return
        }

        val userMessage = ChatMessageWithMode(
            content = content,
            isFromUser = true,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )

        addMessageToGeneralChat(userMessage)
        saveGeneralMessageToFile(userMessage)

        _isTyping.value = true

        viewModelScope.launch {
            delay(kotlin.random.Random.nextLong(1000, 3000))

            val response = intelligentResponses.getResponse(content)

            val botMessage = ChatMessageWithMode(
                content = response,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp(),
                mode = ChatMode.GENERAL
            )

            _isTyping.value = false
            addMessageToGeneralChat(botMessage)
            saveGeneralMessageToFile(botMessage)
        }
    }
*/
   private fun sendGeneralTextMessage(content: String) {
       if (chatBotManager.isMenuRequest(content)) {
           showMainMenu()
           return
       }

       // Agregar mensaje del usuario
       addUserMessage(content)

       // Verificar quota
       if (!quotaManager.canMakeQuery()) {
           addBotMessage(quotaManager.getQuotaMessage())
           return
       }

       // Procesar con Gemini
       processWithGemini(content)
   }
    private fun processWithGemini(content: String) {
        _isTyping.value = true

        viewModelScope.launch {
            when (val result = geminiService.processUserMessage(content)) {
                is ChatResult.Success -> {
                    quotaManager.consumeQuery()
                    val responseWithQuota = """
                        ${result.message}
                        
                        _${quotaManager.getQuotaMessage()}_
                    """.trimIndent()
                    addBotMessage(responseWithQuota)
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


    /*private fun sendGeneralAudioMessage(transcript: String) {
        val userMessage = ChatMessageWithMode(
            content = "🎤 \"$transcript\"",
            isFromUser = true,
            type = MessageType.AUDIO,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )

        addMessageToGeneralChat(userMessage)
        saveGeneralMessageToFile(userMessage, "AUDIO_TRANSCRIPT: $transcript")

        _isTyping.value = true

        viewModelScope.launch {
            delay(kotlin.random.Random.nextLong(1000, 2500))

            val response = intelligentResponses.getAudioResponse()

            val botMessage = ChatMessageWithMode(
                content = "👂 Perfecto, entendí: \"$transcript\"\n\n$response",
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp(),
                mode = ChatMode.GENERAL
            )

            _isTyping.value = false
            addMessageToGeneralChat(botMessage)
            saveGeneralMessageToFile(botMessage)
        }
    }*/
    private fun sendGeneralAudioMessage(transcript: String) {
        addUserMessage("🎤 \"$transcript\"", MessageType.AUDIO)

        if (!quotaManager.canMakeQuery()) {
            addBotMessage(quotaManager.getQuotaMessage())
            return
        }

        _isTyping.value = true

        viewModelScope.launch {
            when (val result = geminiService.processAudioMessage(transcript)) {
                is ChatResult.Success -> {
                    quotaManager.consumeQuery()
                    val responseWithQuota = """
                        👂 Procesé tu audio:
                        
                        ${result.message}
                        
                        _${quotaManager.getQuotaMessage()}_
                    """.trimIndent()
                    addBotMessage(responseWithQuota)
                }

                is ChatResult.Error -> {
                    addBotMessage(result.message)
                }
            }
            _isTyping.value = false
        }
    }
    private fun addUserMessage(
        content: String,
        type: MessageType = MessageType.TEXT
    ) {
        val message = ChatMessageWithMode(
            content = content,
            isFromUser = true,
            type = type,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )
        addMessageToGeneralChat(message)
    }

    private fun addBotMessage(content: String) {
        val message = ChatMessageWithMode(
            content = content,
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )
        addMessageToGeneralChat(message)
    }

    private fun sendGeneralImageMessage(imagePath: String) {
        val userMessage = ChatMessageWithMode(
            content = imagePath,
            isFromUser = true,
            type = MessageType.IMAGE,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )

        addMessageToGeneralChat(userMessage)
        saveGeneralMessageToFile(userMessage, "IMAGE: $imagePath")

        _isAnalyzing.value = true

        viewModelScope.launch {
            delay(kotlin.random.Random.nextLong(2000, 4000))

            val response =
                "📸 ¡Excelente foto! Si querés crear un reporte completo de esta pesca, toca el botón **'Crear Parte'** y te ayudo a registrar todos los detalles automáticamente."

            val botMessage = ChatMessageWithMode(
                content = response,
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp(),
                mode = ChatMode.GENERAL
            )

            _isAnalyzing.value = false
            addMessageToGeneralChat(botMessage)
            saveGeneralMessageToFile(botMessage)
        }
    }

    // ================== CHAT CREAR PARTE (CON ML KIT) ==================

    /*
    private fun sendParteTextMessage(content: String) {
        android.util.Log.d(TAG, "🎯 Mensaje parte: '$content'")

        val userMessage = ChatMessageWithMode(
            content = content,
            isFromUser = true,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE
        )

        addMessageToParteSession(userMessage)

        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                // 🤖 USAR ML KIT PARA EXTRAER INFORMACIÓN
                val extractionResult = mlKitManager.extraerInformacionPesca(content)

                // Convertir entidades a datos del parte
                val nuevosDataParte =
                    mlKitManager.convertirEntidadesAParteDatos(extractionResult.entidadesDetectadas)

                // Actualizar sesión con nuevos datos
                _parteSession.value?.let { session ->
                    val datosActualizados = mergearDatosParte(session.parteData, nuevosDataParte)
                    val sessionActualizada = session.copy(parteData = datosActualizados)
                    _parteSession.value = sessionActualizada

                    // Calcular progreso
                    val progreso = calcularProgresoParte(datosActualizados)
                    val sessionConProgreso = sessionActualizada.copy(
                        parteData = datosActualizados.copy(
                            porcentajeCompletado = progreso.porcentaje,
                            camposFaltantes = progreso.camposFaltantes
                        )
                    )
                    _parteSession.value = sessionConProgreso
                }

                delay(2000) // Simular tiempo de procesamiento

                // Generar respuesta inteligente basada en lo extraído
                val response =
                    generarRespuestaParte(extractionResult, _parteSession.value?.parteData)

                val botMessage = ChatMessageWithMode(
                    content = response,
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )

                _isAnalyzing.value = false
                addMessageToParteSession(botMessage)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 Error procesando mensaje de parte: ${e.message}", e)

                val errorMessage = ChatMessageWithMode(
                    content = "⚠️ Hubo un error procesando tu mensaje. ¿Podrías repetir la información de otra forma?",
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )

                _isAnalyzing.value = false
                addMessageToParteSession(errorMessage)
            }
        }
    }
*/

    private fun sendParteAudioMessage(transcript: String) {
        val userMessage = ChatMessageWithMode(
            content = "🎤 \"$transcript\"",
            isFromUser = true,
            type = MessageType.AUDIO,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE
        )

        addMessageToParteSession(userMessage)

        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                // Procesar audio igual que texto
                val extractionResult = mlKitManager.extraerInformacionPesca(transcript)

                val nuevosDataParte =
                    mlKitManager.convertirEntidadesAParteDatos(extractionResult.entidadesDetectadas)
                if (extractionResult.entidadesDetectadas.size > 1) {
                    _currentFieldInProgress.value = null
                    _waitingForFieldResponse.value = null
                }
                val soloObservaciones = extractionResult.entidadesDetectadas.all {
                    it.tipo == "OBSERVACION"
                }
                if (!soloObservaciones && extractionResult.entidadesDetectadas.size > 1) {
                    _currentFieldInProgress.value = null
                    _waitingForFieldResponse.value = null
                }
                _parteSession.value?.let { session ->
                    val datosActualizados = mergearDatosParte(session.parteData, nuevosDataParte)
                    val progreso = calcularProgresoParte(datosActualizados)

                    val sessionActualizada = session.copy(
                        parteData = datosActualizados.copy(
                            porcentajeCompletado = progreso.porcentaje,
                            camposFaltantes = progreso.camposFaltantes
                        )
                    )
                    _parteSession.value = sessionActualizada
                }

                delay(1500)

                val response = "🎤 **Audio procesado con ML Kit**\n\n" +
                        generarRespuestaParte(extractionResult, _parteSession.value?.parteData)

                val botMessage = ChatMessageWithMode(
                    content = response,
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )

                _isAnalyzing.value = false
                addMessageToParteSession(botMessage)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error procesando audio de parte: ${e.message}")
                _isAnalyzing.value = false
            }
        }
    }

    private fun sendParteImageMessage(imagePath: String) {
        val userMessage = ChatMessageWithMode(
            content = imagePath,
            isFromUser = true,
            type = MessageType.IMAGE,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE
        )

        addMessageToParteSession(userMessage)

        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                // Agregar imagen a los datos del parte
                _parteSession.value?.let { session ->
                    val imagenesActualizadas = session.parteData.imagenes + imagePath
                    val datosActualizados = session.parteData.copy(imagenes = imagenesActualizadas)

                    val progreso = calcularProgresoParte(datosActualizados)

                    val sessionActualizada = session.copy(
                        parteData = datosActualizados.copy(
                            porcentajeCompletado = progreso.porcentaje,
                            camposFaltantes = progreso.camposFaltantes
                        )
                    )
                    _parteSession.value = sessionActualizada
                }

                delay(2000)

                val response = """
📸 **Imagen agregada al parte**

¡Excelente! La foto se agregó a tu reporte.

${generarResumenProgreso(_parteSession.value?.parteData)}

¿Hay más detalles que quieras agregar sobre esta jornada?
                """.trimIndent()

                val botMessage = ChatMessageWithMode(
                    content = response,
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )

                _isAnalyzing.value = false
                addMessageToParteSession(botMessage)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error procesando imagen de parte: ${e.message}")
                _isAnalyzing.value = false
            }
        }
    }

    // ================== FUNCIONES DE DATOS DEL PARTE ==================

    private fun mergearDatosParte(
        existente: ParteEnProgreso,
        nuevo: ParteEnProgreso
    ): ParteEnProgreso {
        return ParteEnProgreso(
            fecha = nuevo.fecha ?: existente.fecha,
            horaInicio = nuevo.horaInicio ?: existente.horaInicio,
            horaFin = nuevo.horaFin ?: existente.horaFin,
            // lugar ya no existe
            provincia = nuevo.provincia ?: existente.provincia,
            ubicacion = nuevo.ubicacion ?: existente.ubicacion, // Añadido
            nombreLugar = nuevo.nombreLugar ?: existente.nombreLugar, // Añadido
            modalidad = nuevo.modalidad ?: existente.modalidad,
            numeroCanas = nuevo.numeroCanas ?: existente.numeroCanas,
            tipoEmbarcacion = nuevo.tipoEmbarcacion ?: existente.tipoEmbarcacion,
            especiesCapturadas = (existente.especiesCapturadas + nuevo.especiesCapturadas).distinctBy { it.nombre },
            imagenes = existente.imagenes + nuevo.imagenes,
            observaciones = nuevo.observaciones ?: existente.observaciones,
            noIdentificoEspecie = nuevo.noIdentificoEspecie || existente.noIdentificoEspecie
        )
    }

    private fun calcularProgresoParte(datos: ParteEnProgreso): ProgresoInfo {
        // Quitamos "lugar" de los campos obligatorios
        val camposObligatorios = listOf(
            "fecha" to datos.fecha,
            // "lugar" to datos.lugar, // Se quita
            "modalidad" to datos.modalidad?.displayName,
            "especies" to if (datos.especiesCapturadas.isNotEmpty()) "completado" else null
        )

        // Podemos añadir la ubicación a los opcionales si queremos,
        // pero como no afecta el %, lo dejamos fuera del cálculo.
        val camposOpcionales = listOf(
            "provincia" to datos.provincia?.displayName,
            "hora_inicio" to datos.horaInicio,
            "hora_fin" to datos.horaFin,
            "numero de cañas" to datos.numeroCanas?.toString(),
            "imagenes" to if (datos.imagenes.isNotEmpty()) "completado" else null,
            "ubicacion" to datos.nombreLugar  // hace que aparezca en chips si falta
        )

        val obligatoriosCompletos = camposObligatorios.count { it.second != null }
        val opcionalesCompletos = camposOpcionales.count { it.second != null }

        val totalCompletos = obligatoriosCompletos + opcionalesCompletos
        val totalCampos = camposObligatorios.size + camposOpcionales.size

        val porcentaje = (totalCompletos.toFloat() / totalCampos * 100).toInt()

        val faltantes = camposObligatorios.filter { it.second == null }.map { it.first } +
                camposOpcionales.filter { it.second == null }.map { it.first }

        return ProgresoInfo(porcentaje, faltantes)
    }

    private data class ProgresoInfo(val porcentaje: Int, val camposFaltantes: List<String>)

    private fun generarRespuestaParte(
        extractionResult: MLKitExtractionResult,
        datosActuales: ParteEnProgreso?
    ): String {
        if (extractionResult.entidadesDetectadas.isEmpty()) {
            return """
🤖 No detecté información específica de pesca en tu mensaje.

¿Podrías contarme más detalladamente? Por ejemplo:
• **Cuándo:** "Ayer de mañana" o "El sábado"
• **Dónde:** "En Puerto Madryn" o "Playa tal"
• **Qué pescaste:** "Dos pejerreyes" o "Un salmón"
• **Cómo:** "Desde costa" o "Embarcado"
            """.trimIndent()
        }

        val respuesta = StringBuilder()
        respuesta.append("🤖 **Información extraída automáticamente:**\n\n")

        // Mostrar lo que se detectó
        extractionResult.entidadesDetectadas.forEach { entity ->
            val emoji = when (entity.tipo) {
                "FECHA" -> "📅"
                "HORA_INICIO", "HORA_FIN", "HORA" -> "⏰"
                "LUGAR" -> "📍"
                "PROVINCIA" -> "🗺️"
                "MODALIDAD" -> "🎣"
                "ESPECIE" -> "🐟"
                "NUMERO_CANAS" -> "🎯"
                "CANTIDAD_PECES" -> "📊"
                else -> "ℹ️"
            }

            respuesta.append(
                "$emoji **${
                    entity.tipo.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                }:** ${entity.valor}\n"
            )
        }

        respuesta.append("\n")
        respuesta.append(generarResumenProgreso(datosActuales))

        return respuesta.toString()
    }

    private fun generarResumenProgreso(datos: ParteEnProgreso?): String {
        if (datos == null) return ""

        val progreso = calcularProgresoParte(datos)

        val resumen = StringBuilder()
        resumen.append("📋 **Progreso del parte: ${progreso.porcentaje}%**\n\n")

        if (datos.fecha != null) resumen.append("✅ Fecha: ${datos.fecha}\n")
        //if (datos.lugar != null) resumen.append("✅ Lugar: ${datos.lugar}\n")
        if (datos.modalidad != null) resumen.append("✅ Modalidad: ${datos.modalidad.displayName}\n")
        if (datos.especiesCapturadas.isNotEmpty()) {
            resumen.append("✅ Especies: ${datos.especiesCapturadas.joinToString(", ") { "${it.numeroEjemplares} ${it.nombre}" }}\n")
        }

        if (progreso.camposFaltantes.isNotEmpty()) {
            resumen.append("\n📝 **Todavía falta:**\n")
            progreso.camposFaltantes.take(3).forEach { campo ->
                val pregunta = when (campo) {
                    "fecha" -> "¿Qué día pescaste?"
                    "lugar" -> "¿En qué lugar/playa?"
                    "modalidad" -> "¿Desde costa o embarcado?"
                    "especies" -> "¿Qué especies capturaste?"
                    "provincia" -> "¿En qué provincia?"
                    "hora_inicio" -> "¿A qué hora empezaste?"
                    "ubicacion" -> "¿Dónde pescaste exactamente?"  // ≪≪≪ NUEVO ≫≫≫
                    "hora_fin" -> "¿A qué hora terminaste?"
                    else -> "¿Podés completar $campo?"
                }
                resumen.append("• $pregunta\n")
            }
        }

        if (progreso.porcentaje >= 80) {
            resumen.append("\n🎉 **¡Casi completo!** Ya podés guardar el parte como borrador o completarlo.")
        }

        return resumen.toString()
    }

    // ================== FUNCIONES DE PERSISTENCIA ==================

    private fun addMessageToGeneralChat(message: ChatMessageWithMode) {
        _generalMessages.value = _generalMessages.value + message
    }

    private fun addMessageToParteSession(message: ChatMessageWithMode) {
        _parteSession.value?.let { session ->
            val updatedMessages = session.messages + message
            _parteSession.value = session.copy(messages = updatedMessages as List<ChatMessage>)
        }
    }

    private fun saveGeneralMessageToFile(
        message: ChatMessageWithMode,
        customContent: String? = null
    ) {
        try {
            val messageText =
                "${message.timestamp} - ${if (message.isFromUser) "USER" else "BOT"}: ${customContent ?: message.content}\n"
            generalChatFile.appendText(messageText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadGeneralChatHistory() {
        try {
            if (generalChatFile.exists()) {
                val lines = generalChatFile.readLines().takeLast(50)
                val loadedMessages = mutableListOf<ChatMessageWithMode>()

                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split(" - ", limit = 2)
                        if (parts.size == 2) {
                            val timestamp = parts[0]
                            val content = parts[1]

                            when {
                                content.startsWith("USER: AUDIO_TRANSCRIPT:") -> {
                                    val transcript =
                                        content.removePrefix("USER: AUDIO_TRANSCRIPT: ")
                                    loadedMessages.add(
                                        ChatMessageWithMode(
                                            "🎤 \"$transcript\"",
                                            true,
                                            MessageType.AUDIO,
                                            timestamp,
                                            ChatMode.GENERAL
                                        )
                                    )
                                }

                                content.startsWith("USER: IMAGE:") -> {
                                    val imagePath = content.removePrefix("USER: IMAGE: ")
                                    loadedMessages.add(
                                        ChatMessageWithMode(
                                            imagePath,
                                            true,
                                            MessageType.IMAGE,
                                            timestamp,
                                            ChatMode.GENERAL
                                        )
                                    )
                                }

                                content.startsWith("USER: ") -> {
                                    loadedMessages.add(
                                        ChatMessageWithMode(
                                            content.removePrefix("USER: "),
                                            true,
                                            MessageType.TEXT,
                                            timestamp,
                                            ChatMode.GENERAL
                                        )
                                    )
                                }

                                content.startsWith("BOT: ") -> {
                                    loadedMessages.add(
                                        ChatMessageWithMode(
                                            content.removePrefix("BOT: "),
                                            false,
                                            MessageType.TEXT,
                                            timestamp,
                                            ChatMode.GENERAL
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                _generalMessages.value = loadedMessages
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessageWithMode(
            content = """
🎣 **¡Hola! Soy Huka, tu asistente de pesca inteligente**

**Dos formas de usar la app:**

🗨️ **Chat General** (este chat):
• Consejos de pesca
• Identificación de especies
• Charla sobre técnicas
• Preguntas generales
¿En qué te ayudo hoy?
            """.trimIndent(),
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )
        addMessageToGeneralChat(welcomeMessage)
    }


    fun completarYEnviarParte() {
        _parteSession.value?.let { session ->
            if (session.parteData.porcentajeCompletado >= 70) {
                _firebaseStatus.value = "Completando parte..."

                viewModelScope.launch {
                    try {
                        // 1. Convertir sesión a parte completo

                        val resultadoConversion = firebaseManager.convertirSessionAParte(session)

                        when (resultadoConversion) {
                            is FirebaseResult.Success -> {
                                _firebaseStatus.value = "Parte completado y guardado"

                                // Mensaje de éxito en el chat
                                val mensajeExito = ChatMessageWithMode(
                                    content = """
                                🎉 **¡Parte de pesca completado exitosamente!**
                                
                                ✅ **Datos guardados en Firebase:**
                                - Fecha: ${session.parteData.fecha ?: "No especificada"}
                                - Especies: ${session.parteData.especiesCapturadas.size} registradas
                                - Total capturas: ${session.parteData.especiesCapturadas.sumOf { it.numeroEjemplares }}
                                - Modalidad: ${session.parteData.modalidad?.displayName ?: "No especificada"}
                                
                                Tu reporte ya está disponible en **"Mis Reportes"** 📊
                                
                                ¿Querés crear otro parte o volver al chat general?
                                """.trimIndent(),
                                    isFromUser = false,
                                    type = MessageType.TEXT,
                                    timestamp = getCurrentTimestamp(),
                                    mode = ChatMode.CREAR_PARTE
                                )
                                addMessageToParteSession(mensajeExito)

                                // Volver al chat general después de un delay
                                delay(2000)
                                volverAChatGeneral()
                                _parteSession.value = null

                                delay(2000)
                                _firebaseStatus.value = null

                            }

                            is FirebaseResult.Error -> {
                                _firebaseStatus.value = "Error completando parte"

                                val mensajeError = ChatMessageWithMode(
                                    content = "❌ **Error guardando el parte:** ${resultadoConversion.message}\n\nTus datos están guardados como borrador. Intentá de nuevo más tarde.",
                                    isFromUser = false,
                                    type = MessageType.TEXT,
                                    timestamp = getCurrentTimestamp(),
                                    mode = ChatMode.CREAR_PARTE
                                )
                                addMessageToParteSession(mensajeError)

                                android.util.Log.e(
                                    TAG,
                                    "Error completando parte: ${resultadoConversion.message}"
                                )
                                delay(3000)
                                _firebaseStatus.value = null
                            }

                            else -> {}
                        }

                    } catch (e: Exception) {
                        _firebaseStatus.value = "Error completando parte"
                        android.util.Log.e(TAG, "Error en completarYEnviarParte: ${e.message}")
                        delay(3000)
                        _firebaseStatus.value = null
                    }
                }
            } else {
                // Mensaje si no está suficientemente completo
                val mensajeIncompleto = ChatMessageWithMode(
                    content = """
⚠️ **Parte incompleto**

Para enviar el parte necesitas al menos 70% completado.

**Progreso actual:** ${session.parteData.porcentajeCompletado}%

**Falta agregar:**
${session.parteData.camposFaltantes.joinToString("\n") { "• $it" }}

¿Querés continuar completando o guardarlo como borrador?
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(mensajeIncompleto)
            }
        }
    }
    fun habilitarChat() {
        _chatEnabled.value = true

        val welcomeMessage = ChatMessageWithMode(
            content = """
            💬 **Chat con Huka activado**
            
            ¡Hola! Soy Huka, tu asistente de pesca 🎣
            
            Podés preguntarme lo que necesites sobre pesca.
            
            ¿En qué te puedo ayudar?
        """.trimIndent(),
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )

        addMessageToGeneralChat(welcomeMessage)
    }
    fun volverAlMenuPrincipal() {
        _chatEnabled.value = false
        _currentMode.value = ChatMode.GENERAL
        showMainMenu()
    }
    // ================== NUEVA FUNCIÓN PARA GUARDAR UBICACIÓN ==================

    fun saveLocation(latitude: Double, longitude: Double, name: String?) {
        if (_currentMode.value != ChatMode.CREAR_PARTE) return

        val geoPoint = GeoPoint(latitude, longitude)
        val locationName = name ?: "Ubicación sin nombre"

        Log.d(TAG, "📍 Guardando ubicación: $locationName ($geoPoint)")

        _parteSession.value?.let { session ->
            // Actualizar los datos del parte con la nueva ubicación
            val datosActualizados = session.parteData.copy(
                ubicacion = geoPoint,
                nombreLugar = locationName
            )

            // Volver a calcular el progreso (sin que la ubicación afecte el %)
            val progreso = calcularProgresoParte(datosActualizados)
            val sessionConProgreso = session.copy(
                parteData = datosActualizados.copy(
                    porcentajeCompletado = progreso.porcentaje,
                    camposFaltantes = progreso.camposFaltantes
                )
            )
            _parteSession.value = sessionConProgreso

            // Añadir un mensaje de confirmación al chat
            val confirmMessage = ChatMessageWithMode(
                content = "✅ **Ubicación guardada:** $locationName",
                isFromUser = false,
                type = MessageType.TEXT,
                timestamp = getCurrentTimestamp(),
                mode = ChatMode.CREAR_PARTE
            )
            addMessageToParteSession(confirmMessage)
        }
    }

    /*    // Nueva función para cargar borradores
    suspend fun cargarBorradores(): List<ParteSessionChat> {
        return try {
            firebaseManager.obtenerSesionesUsuario(EstadoParte.BORRADOR)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error cargando borradores: ${e.message}")
            emptyList()
        }
    }*/

    // Nueva función para retomar borrador
    fun retomarBorrador(session: ParteSessionChat) {
        _currentMode.value = ChatMode.CREAR_PARTE
        _parteSession.value = session.copy(estado = EstadoParte.EN_PROGRESO)

        val mensajeRetomar = ChatMessageWithMode(
            content = """
🔄 **Borrador retomado**

Continuando desde donde lo dejaste:
- Progreso: ${session.parteData.porcentajeCompletado}%
- Especies registradas: ${session.parteData.especiesCapturadas.size}

¡Sigamos completando tu parte de pesca!
        """.trimIndent(),
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE
        )
        addMessageToParteSession(mensajeRetomar)
    }

    fun getConversationStats(): String {
        val generalCount = _generalMessages.value.size
        val parteCount = _parteSession.value?.messages?.size ?: 0
        val modo = if (_currentMode.value == ChatMode.GENERAL) "General" else "Crear Parte"

        return "📊 Modo: $modo | General: $generalCount | Parte: $parteCount"
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        mlKitManager.cleanup()
    }

    private val _currentFieldInProgress = MutableStateFlow<CampoParte?>(null)
    val currentFieldInProgress: StateFlow<CampoParte?> = _currentFieldInProgress.asStateFlow()

    // NUEVO: Estado de espera de respuesta específica
    private val _waitingForFieldResponse = MutableStateFlow<CampoParte?>(null)
    val waitingForFieldResponse: StateFlow<CampoParte?> = _waitingForFieldResponse.asStateFlow()

    // NUEVO: Función para manejar selección de campo
    fun onCampoParteSelected(campo: CampoParte) {
        Log.d(TAG, "🔵 onCampoParteSelected - Campo seleccionado: ${campo.name}")
        _currentFieldInProgress.value = campo
        _waitingForFieldResponse.value = campo

        // Agregar mensaje del bot con la pregunta específica (genérico inicial)
        val preguntaMessage = ChatMessageWithMode(
            content = campo.pregunta,  // Asumiendo que cada CampoParte tiene su .pregunta
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE,
            // NUEVO: Agregar metadata del campo
            metadata = mapOf("fieldType" to campo.name)
        )

        addMessageToParteSession(preguntaMessage)

        // Manejo específico por campo: agregar mensaje detallado de instrucciones
        when (campo) {
            CampoParte.FECHA -> {
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                📅 **Fecha de la jornada**
                
                ¿En qué día saliste a pescar? Ejemplo: 15/10/2025
                
                • Formato: DD/MM/AAAA
                • Si es hoy, decí "hoy" y lo auto-completo.
                • Podés editar después si querés.
                
                ¡Empecemos por ahí!
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.HORARIOS -> {
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                ⏰ **Horarios de pesca**
                
                ¿Cuándo arrancaste y terminaste la jornada? Ejemplo: 6:00 - 18:00
                
                • Formato: HH:MM - HH:MM (hora de salida y regreso)
                • Si no sabés exacto, aproximá.
                • Incluí si hubo pausas largas.
                
                Contame tus timings...
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.UBICACION -> {
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                📍 **Ubicación de la pesca**
                
                ¿Dónde pescaste hoy? Ejemplo: "Río Paraná, cerca de Rosario" o coordenadas.
                
                • Podés describir: río, mar, lago, spot conocido.
                • Si querés precisión, decime y abro el mapa para pinchar.
                
                ¿Dejame saber el lugar!
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.ESPECIES -> {
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                🐟 **Especies capturadas**
                
                ¿Qué pescaste? Ejemplo: "Dorados (3), Bogas (2)"
                
                • Lista las especies y cantidades aproximadas.
                • Si no pescaste nada, decí "cero" o "sin capturas".
                • Podés agregar tamaños o notas después.
                
                ¡Mostrame tus trofeos!
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.MODALIDAD -> {
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                🎣 **Modalidad de pesca**
                
                ¿Cómo pescaste? Ejemplo: "de costa, embarcado"
                
                • kayak.
                • Con red.
                • Medio mundo.
                
                ¿Cuál fue tu estilo hoy?
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.FOTOS -> {
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                📸 **Fotos de la jornada**
                
                ¿Querés agregar imágenes? Ejemplo: "Foto del dorado de 5kg" o subí directamente.
                
                • Ideal para capturas, spots o equipo.
                
                ¡Subí tus mejores fotos!
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.CANAS -> {  // Asumiendo que es "Cañas" (equipo) o "Capturas" - ajustá si es otra cosa
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                🎣 **Cañas y equipo utilizado**
                
                ¿Cuantas cañas usarte? "
                
                • Lo que funcionó mejor.
                • Ayuda para futuras salidas.
                
                Contame tu equipo...
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            CampoParte.OBSERVACIONES -> {
                // Tu código original, lo mantengo intacto
                _waitingForFieldResponse.value = campo
                val pregunta = ChatMessageWithMode(
                    content = """
                📝 **Observaciones adicionales**
                
                Podés agregar cualquier comentario sobre tu jornada:
                • Estado del mar o clima
                • Carnada utilizada
                • Técnicas de pesca
                • Anécdotas o detalles importantes
                • Lo que quieras recordar
                
                Escribí libremente lo que quieras registrar...
                """.trimIndent(),
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(pregunta)
            }

            // Default para campos futuros
            else -> {
                val preguntaDefault = ChatMessageWithMode(
                    content = "Por favor, proporcioná la info para: ${campo.name}. ¡Estoy listo para ayudarte!",
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(preguntaDefault)
            }
        }

        // Si es ubicación, abrir directamente el mapa
        if (campo == CampoParte.UBICACION) {
            Log.d(TAG, "🗺️ Campo UBICACION detectado - Intentando abrir mapa")


            // Esto triggereará el MapPicker en la UI
            _showMapPicker.value = true
        }

        // Si es fotos, abrir selector de imágenes
        if (campo == CampoParte.FOTOS) {
            Log.d(TAG, "📸 Campo FOTOS detectado - Intentando abrir selector")
            _showImagePicker.value = true
        }
    }
    private fun procesarRespuestaCampo(content: String, campo: CampoParte) {
        viewModelScope.launch {
            _isAnalyzing.value = true

            try {
                // CASO ESPECIAL: Observaciones es texto libre, no necesita extracción
                if (campo == CampoParte.OBSERVACIONES) {
                    // Guardar directamente el texto sin procesar
                    _parteSession.value?.let { session ->
                        val datosActualizados = session.parteData.copy(
                            observaciones = content
                        )

                        val progreso = calcularProgresoParte(datosActualizados)
                        _parteSession.value = session.copy(
                            parteData = datosActualizados.copy(
                                porcentajeCompletado = progreso.porcentaje,
                                camposFaltantes = progreso.camposFaltantes
                            )
                        )

                        // Mensaje de confirmación
                        val confirmacion = """
                        ✅ **Observaciones guardadas:**
                        
                        "$content"
                        
                        Tus notas han sido registradas correctamente.
                    """.trimIndent()

                        val botMessage = ChatMessageWithMode(
                            content = confirmacion,
                            isFromUser = false,
                            type = MessageType.TEXT,
                            timestamp = getCurrentTimestamp(),
                            mode = ChatMode.CREAR_PARTE
                        )

                        addMessageToParteSession(botMessage)

                        // Limpiar estados
                        _currentFieldInProgress.value = null
                        _waitingForFieldResponse.value = null
                    }
                    return@launch
                }

                // Para los demás campos, usar el proceso normal de extracción
                val extractionResult = mlKitManager.extraerInformacionPesca(content)

                // Filtrar SOLO las entidades del campo específico
                val entidadesRelevantes = filtrarEntidadesPorCampo(extractionResult, campo)

                // Solo actualizar si encontramos entidades relevantes
                if (entidadesRelevantes.entidadesDetectadas.isNotEmpty()) {
                    // Actualizar SOLO el campo específico
                    actualizarDatosPartePorCampo(campo, entidadesRelevantes)

                    // Generar respuesta de confirmación
                    val confirmacion = generarMensajeConfirmacionCampo(campo, entidadesRelevantes)

                    val botMessage = ChatMessageWithMode(
                        content = confirmacion,
                        isFromUser = false,
                        type = MessageType.TEXT,
                        timestamp = getCurrentTimestamp(),
                        mode = ChatMode.CREAR_PARTE
                    )

                    addMessageToParteSession(botMessage)

                    // Limpiar estados
                    _currentFieldInProgress.value = null
                    _waitingForFieldResponse.value = null
                } else {
                    // No se encontró información relevante
                    val mensajeNoDetectado = ChatMessageWithMode(
                        content = """
                    ❓ No pude detectar ${campo.displayName.drop(3).lowercase()} en tu respuesta.
                    
                    Por favor, intentá de nuevo con el formato sugerido:
                    ${obtenerEjemploPorCampo(campo)}
                    """.trimIndent(),
                        isFromUser = false,
                        type = MessageType.TEXT,
                        timestamp = getCurrentTimestamp(),
                        mode = ChatMode.CREAR_PARTE
                    )

                    addMessageToParteSession(mensajeNoDetectado)
                    // Mantener el campo en progreso para que el usuario reintente
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando campo $campo: ${e.message}")

                val errorMessage = ChatMessageWithMode(
                    content = "⚠️ Error procesando tu respuesta. Por favor, intentá de nuevo.",
                    isFromUser = false,
                    type = MessageType.TEXT,
                    timestamp = getCurrentTimestamp(),
                    mode = ChatMode.CREAR_PARTE
                )
                addMessageToParteSession(errorMessage)

            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // NUEVO: Función auxiliar para dar ejemplos
    private fun obtenerEjemploPorCampo(campo: CampoParte): String {
        return when (campo) {
            CampoParte.HORARIOS -> "• De 6 a 11\n• Desde las 5:30 hasta las 10:00"
            CampoParte.ESPECIES -> "• 2 pejerreyes y 1 róbalo\n• Saqué 3 merluzas"
            CampoParte.FECHA -> "• Hoy\n• Ayer\n• 25/10/2024"
            CampoParte.CANAS -> "• 2 cañas\n• Usé tres cañas"
            CampoParte.MODALIDAD -> "• Desde costa\n• Embarcado\n• Con kayak"
            CampoParte.UBICACION -> "• Puerto Madryn\n• Playa Unión"
            CampoParte.OBSERVACIONES -> "Cualquier comentario sobre tu jornada"
            else -> ""
        }
    }

    // Modificar sendParteTextMessage para considerar el campo en progreso
    private fun sendParteTextMessage(content: String) {
        val userMessage = ChatMessageWithMode(
            content = content,
            isFromUser = true,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.CREAR_PARTE
        )

        addMessageToParteSession(userMessage)

        // Si estamos esperando respuesta de un campo específico
        _waitingForFieldResponse.value?.let { campo ->
            procesarRespuestaCampo(content, campo)
            return
        }

        // Si no, procesar normalmente
        _isAnalyzing.value = true
        // ... resto del código existente ...
    }

    private fun generarMensajeConfirmacionCampo(
        campo: CampoParte,
        extraction: MLKitExtractionResult
    ): String {
        val datos = _parteSession.value?.parteData

        return when (campo) {
            CampoParte.ESPECIES -> {
                val especies = datos?.especiesCapturadas ?: emptyList()
                if (especies.isNotEmpty()) {
                    """
                ✅ **Peces registrados:**
                ${especies.joinToString("\n") { "• ${it.numeroEjemplares} ${it.nombre}" }}
                
                Total: ${especies.sumOf { it.numeroEjemplares }} ejemplares
                
                ¿Querés agregar más especies o continuar con otro campo?
                """.trimIndent()
                } else {
                    "❓ No pude identificar especies. ¿Podrías ser más específico?"
                }
            }

            CampoParte.FECHA -> {
                datos?.fecha?.let { fecha ->
                    "✅ **Fecha registrada:** $fecha"
                } ?: "❓ No pude identificar la fecha. Probá con 'hoy', 'ayer' o una fecha específica"
            }

            CampoParte.HORARIOS -> {
                val inicio = datos?.horaInicio
                val fin = datos?.horaFin

                when {
                    inicio != null && fin != null -> {
                        "✅ **Horarios registrados:** de $inicio a $fin"
                    }
                    inicio != null -> {
                        "✅ **Hora de inicio registrada:** $inicio\n\n¿A qué hora terminaste?"
                    }
                    fin != null -> {
                        "✅ **Hora de fin registrada:** $fin\n\n¿A qué hora empezaste?"
                    }
                    else -> {
                        "❓ No pude detectar los horarios. Intentá con formato 'de 6 a 11' o '6:00 hasta 11:30'"
                    }
                }
            }

            CampoParte.MODALIDAD -> {
                datos?.modalidad?.let { modalidad ->
                    "✅ **Modalidad registrada:** ${modalidad.displayName}"
                } ?: "❓ No pude detectar la modalidad. Decime si fue desde costa, embarcado, etc."
            }

            CampoParte.CANAS -> {
                datos?.numeroCanas?.let { numero ->
                    "✅ **Número de cañas:** $numero"
                } ?: "❓ No pude detectar el número. Decime cuántas cañas usaste (1, 2, 3...)"
            }

            CampoParte.UBICACION -> {
                val lugar = datos?.nombreLugar
                val provincia = datos?.provincia

                when {
                    lugar != null && provincia != null -> {
                        "✅ **Ubicación completa:** $lugar, ${provincia.displayName}"
                    }
                    lugar != null -> {
                        "✅ **Lugar registrado:** $lugar"
                    }
                    provincia != null -> {
                        "✅ **Provincia registrada:** ${provincia.displayName}"
                    }
                    else -> {
                        "❓ No pude detectar la ubicación. ¿Dónde pescaste?"
                    }
                }
            }

            CampoParte.OBSERVACIONES -> {
                "✅ **Observaciones guardadas**"
            }

            else -> "✅ Información registrada correctamente"
        }
    }
    private fun filtrarEntidadesPorCampo(
        extractionResult: MLKitExtractionResult,
        campo: CampoParte
    ): MLKitExtractionResult {
        val entidadesFiltradas = when (campo) {
            CampoParte.ESPECIES -> {
                // SOLO especies y cantidades de peces
                extractionResult.entidadesDetectadas.filter {
                    it.tipo in listOf("ESPECIE", "CANTIDAD_PECES")
                }
            }

            CampoParte.FECHA -> {
                // SOLO fechas
                extractionResult.entidadesDetectadas.filter {
                    it.tipo == "FECHA"
                }
            }

            CampoParte.HORARIOS -> {
                // SOLO horas (inicio, fin o genérica)
                extractionResult.entidadesDetectadas.filter {
                    it.tipo in listOf("HORA_INICIO", "HORA_FIN", "HORA")
                }
            }

            CampoParte.MODALIDAD -> {
                // SOLO modalidad
                extractionResult.entidadesDetectadas.filter {
                    it.tipo == "MODALIDAD"
                }
            }

            CampoParte.CANAS -> {
                // SOLO número de cañas
                extractionResult.entidadesDetectadas.filter {
                    it.tipo == "NUMERO_CANAS"
                }
            }

            CampoParte.UBICACION -> {
                // SOLO lugar y provincia
                extractionResult.entidadesDetectadas.filter {
                    it.tipo in listOf("LUGAR", "PROVINCIA")
                }
            }

            CampoParte.OBSERVACIONES -> {
                // Para observaciones, no filtrar (es texto libre)
                extractionResult.entidadesDetectadas
            }

            else -> emptyList()
        }

        return MLKitExtractionResult(
            textoExtraido = extractionResult.textoExtraido,
            entidadesDetectadas = entidadesFiltradas,
            confianza = if (entidadesFiltradas.isNotEmpty()) extractionResult.confianza else 0f
        )
    }

    // Nuevos estados para triggers de UI
    //private val _showMapPicker = MutableStateFlow(false)

    val showMapPickerForParte: StateFlow<Boolean> = _showMapPicker.asStateFlow()

    /*private val _showImagePicker = MutableStateFlow(false)
        val showImagePickerForParte: StateFlow<Boolean> = _showImagePicker.asStateFlow()

        fun dismissMapPicker() {
            Log.d(TAG, "❌ dismissMapPicker - Estado actual: ${_showMapPicker.value}")
            _showMapPicker.value = false
        }


        fun dismissImagePicker() {
            _showImagePicker.value = false
        }
*/

    // ACTUALIZAR: Actualizar datos del parte según el campo (más estricto)
    private fun actualizarDatosPartePorCampo(
        campo: CampoParte,
        extractionResult: MLKitExtractionResult
    ) {
        _parteSession.value?.let { session ->
            var datosActualizados = session.parteData

            // IMPORTANTE: Solo actualizar el campo específico, ignorar cualquier otra información
            when (campo) {
                CampoParte.ESPECIES -> {
                    // Solo procesar entidades de tipo ESPECIE y CANTIDAD_PECES
                    val entidadesEspecies = extractionResult.entidadesDetectadas.filter {
                        it.tipo in listOf("ESPECIE", "CANTIDAD_PECES")
                    }

                    if (entidadesEspecies.isNotEmpty()) {
                        val nuevosDataParte =
                            mlKitManager.convertirEntidadesAParteDatos(entidadesEspecies)

                        // Agregar nuevas especies a las existentes
                        val especiesExistentes =
                            datosActualizados.especiesCapturadas.toMutableList()
                        nuevosDataParte.especiesCapturadas.forEach { nuevaEspecie ->
                            val existente =
                                especiesExistentes.find { it.nombre == nuevaEspecie.nombre }
                            if (existente != null) {
                                val index = especiesExistentes.indexOf(existente)
                                especiesExistentes[index] = existente.copy(
                                    numeroEjemplares = existente.numeroEjemplares + nuevaEspecie.numeroEjemplares
                                )
                            } else {
                                especiesExistentes.add(nuevaEspecie)
                            }
                        }
                        datosActualizados =
                            datosActualizados.copy(especiesCapturadas = especiesExistentes)
                    }
                }

                CampoParte.FECHA -> {
                    // SOLO actualizar fecha si encontramos una entidad FECHA
                    extractionResult.entidadesDetectadas
                        .firstOrNull { it.tipo == "FECHA" }
                        ?.let { entity ->
                            datosActualizados = datosActualizados.copy(fecha = entity.valor)
                        }
                }

                CampoParte.HORARIOS -> {
                    // SOLO actualizar horarios
                    var horaInicioEncontrada = false
                    var horaFinEncontrada = false

                    extractionResult.entidadesDetectadas.forEach { entity ->
                        when (entity.tipo) {
                            "HORA_INICIO" -> {
                                datosActualizados =
                                    datosActualizados.copy(horaInicio = entity.valor)
                                horaInicioEncontrada = true
                            }

                            "HORA_FIN" -> {
                                datosActualizados = datosActualizados.copy(horaFin = entity.valor)
                                horaFinEncontrada = true
                            }

                            "HORA" -> {
                                // Si solo detecta una hora genérica, asignarla según lo que falta
                                if (!horaInicioEncontrada && datosActualizados.horaInicio == null) {
                                    datosActualizados =
                                        datosActualizados.copy(horaInicio = entity.valor)
                                } else if (!horaFinEncontrada && datosActualizados.horaFin == null) {
                                    datosActualizados =
                                        datosActualizados.copy(horaFin = entity.valor)
                                }
                            }
                        }
                    }
                }

                CampoParte.MODALIDAD -> {
                    // SOLO actualizar modalidad
                    extractionResult.entidadesDetectadas
                        .firstOrNull { it.tipo == "MODALIDAD" }
                        ?.let { entity ->
                            val modalidad = ModalidadPesca.fromString(entity.valor)
                            datosActualizados = datosActualizados.copy(modalidad = modalidad)
                        }
                }

                CampoParte.CANAS -> {
                    // SOLO actualizar número de cañas
                    extractionResult.entidadesDetectadas
                        .firstOrNull { it.tipo == "NUMERO_CANAS" }
                        ?.let { entity ->
                            val numero = entity.valor.toIntOrNull()
                            if (numero != null) {
                                datosActualizados = datosActualizados.copy(numeroCanas = numero)
                            }
                        }
                }

                CampoParte.UBICACION -> {
                    // SOLO actualizar ubicación y provincia
                    extractionResult.entidadesDetectadas.forEach { entity ->
                        when (entity.tipo) {
                            "LUGAR" -> datosActualizados =
                                datosActualizados.copy(nombreLugar = entity.valor)

                            "PROVINCIA" -> {
                                val provincia = Provincia.fromString(entity.valor)
                                datosActualizados = datosActualizados.copy(provincia = provincia)
                            }
                            // Ignorar cualquier otra entidad
                        }
                    }
                }

                CampoParte.OBSERVACIONES -> {
                    // Para observaciones, guardar el texto completo como está
                    datosActualizados = datosActualizados.copy(
                        observaciones = extractionResult.textoExtraido
                    )
                }

                CampoParte.FOTOS -> {
                    // Las fotos se manejan diferente, no por texto
                    // Este caso no debería llegar aquí
                }
            }

            // Recalcular progreso con los datos actualizados
            val progreso = calcularProgresoParte(datosActualizados)
            val sessionActualizada = session.copy(
                parteData = datosActualizados.copy(
                    porcentajeCompletado = progreso.porcentaje,
                    camposFaltantes = progreso.camposFaltantes
                )
            )
            _parteSession.value = sessionActualizada
        }
    }
}