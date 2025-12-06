package com.example.juka.data


import kotlinx.serialization.Serializable

@Serializable
data class ChatBotConfig(
    val nodes: Map<String, ChatNode>
)

@Serializable
data class ChatNode(
    val id: String,
    val message: String,
    val options: List<ChatOption>,
    val type: NodeType = NodeType.MENU
)

@Serializable
data class ChatOption(
    val label: String,
    val icon: String? = null,
    val action: ActionType,
    val target: String? = null,
    val data: Map<String, String>? = null
)

@Serializable
enum class NodeType {
    MENU,           // Muestra opciones
    INFO,           // Información final
    ACTION,         // Ejecuta una acción
    FORM_START      // Inicia un formulario (como crear parte)
}

@Serializable
enum class ActionType {
    NAVIGATE,       // Navega a otro nodo
    OPEN_SCREEN,    // Abre una pantalla de la app
    DOWNLOAD,       // Descarga archivo
    START_PARTE,    // Inicia crear parte
    SHOW_MAP,       // Muestra mapa
    EXTERNAL_LINK,  // Abre link externo
    BACK,          // Vuelve al menú anterior
    HOME,           // Vuelve al inicio
    ENABLE_CHAT
}