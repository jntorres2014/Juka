package com.example.juka.ui.tutorial

import android.content.Context

/**
 * Preferencias separadas del resto de Huka para que cerrar sesión no haga
 * aparecer nuevamente el tutorial inicial.
 */
class TutorialPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(
        "HukaTutorialPreferences",
        Context.MODE_PRIVATE
    )

    fun hasSeenMainTutorial(): Boolean =
        preferences.getBoolean(KEY_MAIN_TUTORIAL_SEEN, false)

    fun markMainTutorialSeen() {
        preferences.edit()
            .putBoolean(KEY_MAIN_TUTORIAL_SEEN, true)
            .apply()
    }

    fun resetMainTutorial() {
        preferences.edit()
            .putBoolean(KEY_MAIN_TUTORIAL_SEEN, false)
            .apply()
    }

    private companion object {
        const val KEY_MAIN_TUTORIAL_SEEN = "main_tutorial_seen_v1"
    }
}
