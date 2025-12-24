package com.example.juka.data



import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.juka.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class ChatBotManager(private val application: Application) {

    companion object {
        private const val TAG = "ChatBotManager"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Estado del nodo actual
    private val _currentNode = MutableStateFlow<ChatNode?>(null)
    val currentNode: StateFlow<ChatNode?> = _currentNode.asStateFlow()

    // Stack de navegación
    private val navigationStack = mutableListOf<String>()

    // Configuración del bot
    private val chatBotConfig: ChatBotConfig by lazy {
        loadChatBotConfig()
    }

    /**
     * Carga la configuración del chatbot desde el archivo JSON
     */
    private fun loadChatBotConfig(): ChatBotConfig {
        return try {
            val inputStream = application.assets.open("chatbot_config.json")
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()

            json.decodeFromString(ChatBotConfig.serializer(), jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando config JSON: ${e.message}")
            getDefaultConfig()
        }
    }

    /**
     * Configuración por defecto si falla la carga del JSON
     */
    private fun getDefaultConfig(): ChatBotConfig {
        return ChatBotConfig(
            nodes = mapOf(
                "main_menu" to ChatNode(
                    id = "main_menu",
                    message = "¡Hola pescador! 🎣 ¿En qué puedo ayudarte?",
                    type = NodeType.MENU,
                    options = listOf(
                        ChatOption(
                            label = "📝 Crear Registro de Pesca",
                            icon = "📝",
                            action = ActionType.START_PARTE
                        ),
                        ChatOption(
                            label = "🌊 Información de Mareas",
                            icon = "🌊",
                            action = ActionType.NAVIGATE,
                            target = "tides_menu"
                        ),
                        ChatOption(
                            label = "📍 Lugares de Pesca",
                            icon = "📍",
                            action = ActionType.NAVIGATE,
                            target = "locations_menu"
                        ),
                        ChatOption(
                            label = "🐟 Especies y Regulaciones",
                            icon = "🐟",
                            action = ActionType.NAVIGATE,
                            target = "species_menu"
                        ),
                        ChatOption(
                            label = "📊 Mis Estadísticas",
                            icon = "📊",
                            action = ActionType.OPEN_SCREEN,
                            data = mapOf("screen" to "statistics")
                        )
                    )
                ),
                "tides_menu" to ChatNode(
                    id = "tides_menu",
                    message = "📊 Información de mareas disponible:",
                    type = NodeType.MENU,
                    options = listOf(
                        ChatOption(
                            label = "📅 Tabla de Mareas Mensual",
                            action = ActionType.DOWNLOAD,
                            data = mapOf("type" to "tide_table", "format" to "pdf")
                        ),
                        ChatOption(
                            label = "🌊 Marea Actual",
                            action = ActionType.NAVIGATE,
                            target = "current_tide"
                        ),
                        ChatOption(
                            label = "🔙 Volver",
                            action = ActionType.BACK
                        )
                    )
                ),
                "species_menu" to ChatNode(
                    id = "species_menu",
                    message = "🐟 ¿Qué información necesitas sobre especies?",
                    type = NodeType.MENU,
                    options = listOf(
                        ChatOption(
                            label = "📖 Guía de Especies",
                            action = ActionType.OPEN_SCREEN,
                            data = mapOf("screen" to "species_guide")
                        ),
                        ChatOption(
                            label = "⚖️ Tallas Mínimas",
                            action = ActionType.NAVIGATE,
                            target = "minimum_sizes"
                        ),
                        ChatOption(
                            label = "🔙 Volver",
                            action = ActionType.BACK
                        )
                    )
                ),
                "locations_menu" to ChatNode(
                    id = "locations_menu",
                    message = "📍 Seleccioná una opción para lugares de pesca:",
                    type = NodeType.MENU,
                    options = listOf(
                        ChatOption(
                            label = "🗺️ Ver Mapa de Spots",
                            action = ActionType.SHOW_MAP,
                            data = mapOf("layer" to "fishing_spots")
                        ),
                        ChatOption(
                            label = "⭐ Lugares Recomendados",
                            action = ActionType.NAVIGATE,
                            target = "recommended_spots"
                        ),
                        ChatOption(
                            label = "📍 Mis Lugares Guardados",
                            action = ActionType.OPEN_SCREEN,
                            data = mapOf("screen" to "saved_locations")
                        ),
                        ChatOption(
                            label = "🔙 Volver",
                            action = ActionType.BACK
                        )
                    )
                )
            )
        )
    }

    /**
     * Muestra el menú principal
     */
    fun getMainMenu(): ChatNode {
        navigationStack.clear()
        val node = chatBotConfig.nodes["main_menu"]!!
        _currentNode.value = node
        return node
    }

    /**
     * Navega a un nodo específico
     */
    fun navigateToNode(nodeId: String): ChatNode? {
        return chatBotConfig.nodes[nodeId]?.also { node ->
            _currentNode.value = node
        }
    }

    /**
     * Navega hacia atrás en el stack
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun navigateBack(): ChatNode? {
        if (navigationStack.isNotEmpty()) {
            // Verificar que hay elementos antes de remover
            if (navigationStack.size > 0) {
                navigationStack.removeAt(navigationStack.size - 1)
            }
            val previousNodeId = navigationStack.lastOrNull() ?: "main"
            return navigateToNode(previousNodeId)
        }
        return null
    }

    /**
     * Agrega el nodo actual al stack de navegación
     */
    fun pushToNavigationStack(nodeId: String) {
        navigationStack.add(nodeId)
    }

    /**
     * Limpia el stack de navegación
     */
    fun clearNavigationStack() {
        navigationStack.clear()
    }

    /**
     * Verifica si el mensaje del usuario es una solicitud de menú
     */
    fun isMenuRequest(message: String): Boolean {
        val menuKeywords = listOf("menu", "menú", "inicio", "volver")
        return menuKeywords.any { message.lowercase().contains(it) }
    }
}