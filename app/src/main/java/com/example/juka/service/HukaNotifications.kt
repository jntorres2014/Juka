package com.example.juka.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.juka.R

/**
 * Helper centralizado para canal + notificaciones locales de Huka.
 *
 * Se usa desde:
 *  - `HukaMessagingService.onMessageReceived` cuando llega un push con la app
 *    en primer plano.
 *  - `MainActivity.onCreate` para crear el canal proactivamente al arrancar
 *    (así cualquier intento posterior de mostrar una notif lo encuentra).
 *  - El botón "Probar notificación" del perfil, para verificar end-to-end
 *    sin tocar el backend.
 *
 * Centralizar acá evita que el `id` del canal, el ícono, o la importancia
 * queden duplicados en varios archivos y se vayan desincronizando.
 */
object HukaNotifications {

    const val CHANNEL_ID = "huka_channel"
    const val CHANNEL_NAME = "Huka Notificaciones"
    const val CHANNEL_DESCRIPTION = "Logros, recordatorios y alertas de Huka"

    /**
     * Crea el canal si no existe. Es idempotente — Android ignora la
     * segunda llamada si ya existe un canal con el mismo id. Seguro
     * llamarla en cada arranque.
     */
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Muestra una notificación local. Crea el canal antes por las dudas.
     * Devuelve true si efectivamente se mostró, false si el sistema la
     * bloqueó (típicamente porque el usuario no concedió POST_NOTIFICATIONS).
     */
    fun mostrar(
        context: Context,
        titulo: String,
        cuerpo: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ): Boolean {
        crearCanal(context)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            // Usamos el launcher como ícono por defecto. Idealmente generar un
            // ic_stat_huka (silueta blanca) con File → New → Image Asset →
            // Notification Icons en Android Studio.
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notif)
            true
        } catch (e: SecurityException) {
            // Android 13+ sin POST_NOTIFICATIONS lanza SecurityException.
            // No crasheamos; el caller puede mostrar un mensaje pidiendo
            // habilitar las notificaciones en Ajustes.
            false
        }
    }
}
