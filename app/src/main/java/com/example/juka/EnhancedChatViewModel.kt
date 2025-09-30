// EnhancedChatViewModel.kt - ViewModel mejorado con dos modos de chat
package com.example.juka

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.data.firebase.FirebaseResult
import com.example.juka.viewmodel.ChatMessage
import com.example.juka.viewmodel.MessageType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EnhancedChatViewModel(application: Application) : AndroidViewModel(application) {

    // Estados principales
    private val _currentMode = MutableStateFlow(ChatMode.GENERAL)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()

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

    // Archivos
    private val generalChatFile =
        File(getApplication<Application>().filesDir, "general_chat_history.txt")
    private val partesSessionsFile =
        File(getApplication<Application>().filesDir, "partes_sessions.txt")

    companion object {
        private const val TAG = "🎣 EnhancedChatViewModel"
    }

    init {
        android.util.Log.d(TAG, "✅ Inicializando EnhancedChatViewModel con dos modos")

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
                if (encuestaCompleta) {
                    // Agregar mensaje sugiriendo completar encuesta
                    //addWelcomeMessageWithSurvey()
                    Log.i(TAG, "✅ Mensaje de bienvenida con encuesta")
                } else {
                    addWelcomeMessage() // Tu mensaje actual
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error inicializando base de datos: ${e.message}")
            }

        }

        // Cargar historial del chat general
        loadGeneralChatHistory()

        // Agregar mensaje de bienvenida si no hay mensajes
        if (_generalMessages.value.isEmpty()) {
            addWelcomeMessage()
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
🎣 **Modo Crear Parte Activado**

¡Perfecto! Ahora vamos a registrar tu jornada de pesca paso a paso.

Contame todo sobre tu pesca - yo me encargo de extraer automáticamente:
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

        // Mantener la sesión de parte para poder retomarla después
        // No la eliminamos, solo cambiamos el modo
    }

    /**
     * Cancelar la creación del parte actual
     */
    fun cancelarParte() {
        android.util.Log.d(TAG, "❌ Cancelando parte actual")

        _parteSession.value?.let { session ->
            val sessionCancelada = session.copy(estado = EstadoParte.CANCELADO)
            guardarParteSession(sessionCancelada)
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

    private fun sendGeneralTextMessage(content: String) {
        android.util.Log.d(TAG, "💬 Mensaje general: '$content'")

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

    private fun sendGeneralAudioMessage(transcript: String) {
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

¡Excelente! La foto se agregó automáticamente a tu reporte.

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
            lugar = nuevo.lugar ?: existente.lugar,
            provincia = nuevo.provincia ?: existente.provincia,
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
        val camposObligatorios = listOf(
            "fecha" to datos.fecha,
            "lugar" to datos.lugar,
            "modalidad" to datos.modalidad?.displayName,
            "especies" to if (datos.especiesCapturadas.isNotEmpty()) "completado" else null
        )

        val camposOpcionales = listOf(
            "provincia" to datos.provincia?.displayName,
            "hora_inicio" to datos.horaInicio,
            "hora_fin" to datos.horaFin,
            "numero_canas" to datos.numeroCanas?.toString(),
            "imagenes" to if (datos.imagenes.isNotEmpty()) "completado" else null
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
        if (datos.lugar != null) resumen.append("✅ Lugar: ${datos.lugar}\n")
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
🎣 **¡Hola! Soy Juka, tu asistente de pesca inteligente**

**Dos formas de usar la app:**

🗨️ **Chat General** (este chat):
• Consejos de pesca
• Identificación de especies
• Charla sobre técnicas
• Preguntas generales

📋 **Crear Parte** (toca el botón):
• Registro automático de jornadas
• Extracción inteligente con ML Kit
• Guarda todo en Firebase
• Chat específico para cada reporte

¿En qué te ayudo hoy?
            """.trimIndent(),
            isFromUser = false,
            type = MessageType.TEXT,
            timestamp = getCurrentTimestamp(),
            mode = ChatMode.GENERAL
        )
        addMessageToGeneralChat(welcomeMessage)
    }

    // Reemplazar las funciones existentes en tu EnhancedChatViewModel

    private fun guardarParteSession(session: ParteSessionChat) {
        viewModelScope.launch {
            try {
                val resultado = firebaseManager.guardarParteSession(session)
                when (resultado) {
                    is FirebaseResult.Success -> {
                        android.util.Log.i(TAG, "✅ Sesión guardada exitosamente")
                    }
                    is FirebaseResult.Error -> {
                        android.util.Log.e(TAG, "❌ Error guardando sesión: ${resultado.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 Error en guardarParteSession: ${e.message}")
            }
        }
    }

    fun guardarParteBorrador() {
        _parteSession.value?.let { session ->
            val sessionBorrador = session.copy()

            _firebaseStatus.value = "Guardando borrador..."

            viewModelScope.launch {
                try {
                    val resultado = firebaseManager.guardarParteSession(sessionBorrador)

                    when (resultado) {
                        is FirebaseResult.Success -> {
                            _parteSession.value = sessionBorrador
                            _firebaseStatus.value = "Borrador guardado en Firebase"

                            // Mensaje en el chat
                            val mensajeBorrador = ChatMessageWithMode(
                                content = "💾 **Borrador guardado automáticamente**\n\nTu progreso está seguro en Firebase. Podés continuar después desde donde lo dejaste.",
                                isFromUser = false,
                                type = MessageType.TEXT,
                                timestamp = getCurrentTimestamp(),
                                mode = ChatMode.CREAR_PARTE
                            )
                            addMessageToParteSession(mensajeBorrador)

                            delay(3000)
                            _firebaseStatus.value = null
                        }
                        is FirebaseResult.Error -> {
                            _firebaseStatus.value = "Error guardando borrador"
                            android.util.Log.e(TAG, "Error: ${resultado.message}")
                            delay(3000)
                            _firebaseStatus.value = null
                        }
                        else -> {}
                    }

                } catch (e: Exception) {
                    _firebaseStatus.value = "Error guardando borrador"
                    android.util.Log.e(TAG, "Error en guardarParteBorrador: ${e.message}")
                    delay(3000)
                    _firebaseStatus.value = null
                }
            }
        }
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

                                android.util.Log.e(TAG, "Error completando parte: ${resultadoConversion.message}")
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
}