package com.example.juka.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log

class ChatBotActionHandler(private val application: Application) {
    var onEnableChat: (() -> Unit)? = null
    companion object {
        private const val TAG = "ChatBotActionHandler"
    }
    var onEnableConsejos: (() -> Unit)? = null

    // Callbacks que realmente usás
    var onStartParte: (() -> Unit)? = null
    var onDownloadFile: ((Map<String, String>?) -> Unit)? = null

    fun handleAction(option: ChatOption): ActionResult {
        return when (option.action) {
            ActionType.ENABLE_CHAT -> {  // O usa un nuevo ActionType si prefieres separar
                onEnableConsejos?.invoke()
                ActionResult.Success("Consejos activados")
            }
          /*  ActionType.ENABLE_CHAT -> {
                onEnableChat?.invoke()
                ActionResult.Success("Chat habilitado")
            }*/
            ActionType.START_PARTE -> {
                onStartParte?.invoke()
                ActionResult.StartParte
            }

            ActionType.DOWNLOAD -> {
                onDownloadFile?.invoke(option.data)
                ActionResult.Download(option.data)
            }

            ActionType.EXTERNAL_LINK -> {
                option.data?.get("url")?.let { url ->
                    openExternalLink(url)
                    ActionResult.ExternalLink(url)
                } ?: ActionResult.Error("Falta URL")
            }

            ActionType.NAVIGATE -> option.target?.let { ActionResult.Navigate(it) }
                ?: ActionResult.Error("Falta target")

            ActionType.OPEN_SCREEN -> option.data?.get("screen")?.let { ActionResult.OpenScreen(it) }
                ?: ActionResult.Error("Falta screen")

            ActionType.BACK -> ActionResult.Back

            ActionType.HOME -> ActionResult.Home

            else -> ActionResult.Error("Acción no implementada")
        }
    }

    private fun openExternalLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            application.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo link: ${e.message}")
        }
    }
}

// Solo las ActionResult que realmente usás
sealed class ActionResult {
    data class Navigate(val nodeId: String) : ActionResult()
    object StartParte : ActionResult()
    data class Download(val data: Map<String, String>?) : ActionResult()
    data class ExternalLink(val url: String) : ActionResult()
    data class OpenScreen(val screen: String) : ActionResult()
    object Back : ActionResult()
    object Home : ActionResult()
    data class Error(val message: String) : ActionResult()
    data class Success(val message: String = "") : ActionResult()
}