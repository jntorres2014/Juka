package com.example.juka.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.juka.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HukaMessagingService : FirebaseMessagingService() {

    // Se llama cuando llega una notificación con la app EN PRIMER PLANO
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "Juka"
        val body  = remoteMessage.notification?.body  ?: ""
        showNotification(title, body)
    }

    // Se llama cuando se renueva el token del dispositivo
    override fun onNewToken(token: String) {
        // Guardar el token en Firestore para poder enviar notificaciones a este usuario
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showNotification(title: String, body: String) {
        val channelId = "huka_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal (Android 8+)
        val channel = NotificationChannel(
            channelId, "Huka Notificaciones",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.hukalogo4) // tu ícono
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}