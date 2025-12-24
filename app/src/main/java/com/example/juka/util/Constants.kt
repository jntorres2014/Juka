package com.example.juka.util

/**
 * Archivo centralizado de constantes.
 * Aquí guardamos nombres de tablas, claves, y configuraciones globales.
 */
object Constants {

    // 🔥 FIREBASE COLLECTIONS
    object Firebase {
        const val TAG = "🔥 FirebaseManager"
        const val USERS_COLLECTION = "users"
        const val PARTES_COLLECTION = "partes_pesca"
        const val SUBCOLLECTION_PARTES = "partes" // Si la usas

        // Campos frecuentes (para evitar errores de dedo)
        const val FIELD_USER_ID = "userId"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_FECHA = "fecha"
    }

    // ⚙️ CONFIGURACIÓN GENERAL
    object Config {
        const val DATABASE_NAME = "fish_database"
        const val DATE_FORMAT_DISPLAY = "dd/MM/yyyy"
        const val TIME_FORMAT_DISPLAY = "HH:mm"
    }

    // 💬 CHAT
    object Chat {
        const val BOT_SENDER_ID = "bot"
        const val USER_SENDER_ID = "user"
    }
}