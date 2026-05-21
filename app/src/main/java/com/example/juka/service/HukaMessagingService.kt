package com.example.juka.service

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.juka.HukaApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Servicio FCM de Huka.
 *
 * - `onMessageReceived` se ejecuta SOLO cuando llega un push con la app en
 *   primer plano. Cuando la app está en background o killed, Android maneja
 *   las notificaciones por su cuenta usando el payload `notification` y la
 *   config del manifest (`default_notification_channel_id` + `default_notification_icon`).
 *
 * - `onNewToken` se ejecuta cuando FCM rota el token del dispositivo. Lo
 *   persistimos en Firestore para que el backend pueda mandar pushes a este
 *   usuario.
 */
class HukaMessagingService : FirebaseMessagingService() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "Huka"
        val body = remoteMessage.notification?.body ?: ""

        // 1. Mostrar la notificación del sistema.
        HukaNotifications.mostrar(context = this, titulo = title, cuerpo = body)

        // 2. Persistirla en el historial local para que aparezca en la
        //    campanita del header. Usamos el storage del Application.
        val storage = (applicationContext as? HukaApplication)?.localStorageHelper
        if (storage != null) {
            CoroutineScope(Dispatchers.IO).launch {
                storage.guardarNotificacion(titulo = title, cuerpo = body, origen = "FCM")
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "🔄 Token rotado: ${token.take(20)}...")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // set(merge) en lugar de update: garantiza que si el documento del
        // usuario no existe todavía (caso edge en logins recientes), igual
        // se crea con el token persistido.
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "✅ Token persistido en Firestore") }
            .addOnFailureListener { e -> Log.w(TAG, "⚠️ No se pudo persistir el token: ${e.message}") }
    }

    companion object {
        private const val TAG = "🔔 HukaFCM"
    }
}
