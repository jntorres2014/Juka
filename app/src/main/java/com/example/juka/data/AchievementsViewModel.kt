package com.example.juka.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AchievementsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _uiState = MutableStateFlow<List<Achievement>>(emptyList())
    val uiState: StateFlow<List<Achievement>> = _uiState

    private val _newAchievementUnlocked = MutableSharedFlow<Achievement>()
    val newAchievementUnlocked = _newAchievementUnlocked.asSharedFlow()

    init {
        loadUnlockedAchievements()
    }

    private fun loadUnlockedAchievements() {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            android.util.Log.e("Achievements", "Usuario no autenticado")
            return
        }

        android.util.Log.d("Achievements", "Cargando logros para usuario: $userId")

        db.collection("users")
            .document(userId)
            .collection("unlocked_achievements")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Achievements", "Error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                android.util.Log.d("Achievements", "Logros encontrados: ${snapshot.size()}")

                // Lista temporal para acumular los logros
                val tempAchievements = mutableListOf<Achievement>()
                var pendingCount = snapshot.size()

                if (pendingCount == 0) {
                    _uiState.value = emptyList()
                    return@addSnapshotListener
                }

                snapshot.documents.forEach { doc ->
                    val achievementId = doc.id
                    val (title, description) = getAchievementInfo(achievementId)

                    // Intentar obtener la imagen desde Storage
                    loadImageUrl(achievementId) { imageUrl ->
                        val achievement = Achievement(
                            id = achievementId,
                            title = title,
                            description = description,
                            imageUrl = imageUrl,
                            isUnlocked = true,
                            timestamp = doc.getLong("timestamp"),
                            type = doc.getString("type") ?: "PERMANENT"
                        )

                        tempAchievements.add(achievement)
                        android.util.Log.d("Achievements", "Logro cargado: ${achievement.id} - ${achievement.title} - URL: $imageUrl")

                        pendingCount--
                        if (pendingCount == 0) {
                            _uiState.value = tempAchievements.sortedBy { it.title }
                        }
                    }
                }
            }
    }

    private fun loadImageUrl(achievementId: String, onComplete: (String) -> Unit) {
        // Intentar primero con espacios (como mencionaste que se llama el archivo)
        val imageName = achievementId.replace("_", " ")
        val imageRef = storage.reference.child("images/$imageName.png")

        android.util.Log.d("Achievements", "Buscando imagen: images/$imageName.png")

        imageRef.downloadUrl
            .addOnSuccessListener { uri ->
                android.util.Log.d("Achievements", "Imagen encontrada con espacios: $uri")
                onComplete(uri.toString())
            }
            .addOnFailureListener { e1 ->
                android.util.Log.e("Achievements", "No se encontró con espacios: ${e1.message}")

                // Si falla, intentar con guión bajo
                val altRef = storage.reference.child("images/${achievementId}.png")
                android.util.Log.d("Achievements", "Intentando con: images/${achievementId}.png")

                altRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        android.util.Log.d("Achievements", "Imagen encontrada con guión bajo: $uri")
                        onComplete(uri.toString())
                    }
                    .addOnFailureListener { e2 ->
                        android.util.Log.e("Achievements", "No se encontró imagen: ${e2.message}")
                        onComplete("") // Sin imagen
                    }
            }
    }
    // Función para mapear IDs a información legible
    private fun getAchievementInfo(id: String): Pair<String, String> {
        return when (id) {
            "solo_un_pez" -> Pair(
                "Solo un pez",
                "No pescaste nada... bueno, casi nada"
            )
            "zapatero_wade" -> Pair(
                "Zapatero Wade",
                "¡No tuviste suerte hoy, espero esto te ayude la proxima!"
            )
            "primera_captura" -> Pair(
                "Primera Captura",
                "Registraste tu primer pez en Juka"
            )
            "pescador_nocturno" -> Pair(
                "Pescador Nocturno",
                "Pescaste después del atardecer"
            )
            "madrugador" -> Pair(
                "Madrugador",
                "Pescaste antes del amanecer"
            )
            "variedad_es_vida" -> Pair(
                "La Variedad es Vida",
                "Pescaste 5 especies diferentes"
            )
            "rey_del_rio" -> Pair(
                "Rey del Río",
                "Pescaste un dorado de más de 5kg"
            )
            else -> {
                // Si no está mapeado, crear título desde el ID
                val title = id.replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                Pair(title, "Logro desbloqueado")
            }
        }
    }
    fun shareAchievement(context: Context, achievement: Achievement) {
        if (achievement.imageUrl.isEmpty()) {
            // Si no hay imagen, compartir solo texto
            shareTextOnly(context, achievement)
            return
        }

        // Descargar y compartir imagen con texto
        viewModelScope.launch {
            try {
                // Descargar la imagen usando Coil
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(achievement.imageUrl)
                    .build()

                val result = withContext(Dispatchers.IO) {
                    loader.execute(request)
                }

                if (result is SuccessResult) {
                    val bitmap = (result.drawable as BitmapDrawable).bitmap
                    shareImageWithText(context, achievement, bitmap)
                } else {
                    shareTextOnly(context, achievement)
                }
            } catch (e: Exception) {
                android.util.Log.e("Share", "Error descargando imagen: ${e.message}")
                shareTextOnly(context, achievement)
            }
        }
    }

    private fun shareImageWithText(context: Context, achievement: Achievement, bitmap: Bitmap) {
        try {
            // Guardar imagen en caché temporal
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()

            val imageFile = File(cachePath, "${achievement.id}.png")
            val stream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            // Obtener URI usando FileProvider
            val imageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            // Crear intent para compartir imagen + texto
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/*"

                // Agregar la imagen
                putExtra(Intent.EXTRA_STREAM, imageUri)

                // Agregar el texto
                putExtra(
                    Intent.EXTRA_TEXT,
                    "¡Mira mi nueva estampita en Huka! 🎣\n\n" +
                            "🏆 *${achievement.title}*\n" +
                            "_${achievement.description}_\n\n" +
                            "Descargá la app y vení a pescar!"
                )

                // Dar permisos de lectura
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Intentar abrir WhatsApp directamente
                setPackage("com.whatsapp")
            }

            context.startActivity(shareIntent)

        } catch (e: Exception) {
            android.util.Log.e("Share", "Error compartiendo con WhatsApp: ${e.message}")

            // Si falla WhatsApp, abrir selector
            try {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/*"
                    //putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, "¡Mira mi logro en Juka! 🎣\n${achievement.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir logro"))
            } catch (e2: Exception) {
                shareTextOnly(context, achievement)
            }
        }
    }

    private fun shareTextOnly(context: Context, achievement: Achievement) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "¡Mira mi nueva estampita en Juka! 🎣\n\n" +
                        "🏆 *${achievement.title}*\n" +
                        "_${achievement.description}_\n\n" +
                        "Descargá la app y vení a pescar!"
            )
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val shareIntent = Intent.createChooser(sendIntent, "Compartir logro vía")
            context.startActivity(shareIntent)
        }
    }
    fun unlockAchievement(achievementId: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("unlocked_achievements")
            .document(achievementId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    val data = hashMapOf(
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("users")
                        .document(userId)
                        .collection("unlocked_achievements")
                        .document(achievementId)
                        .set(data)
                        .addOnSuccessListener {
                            android.util.Log.d("Achievements", "Logro desbloqueado: $achievementId")

                            // Emitir el nuevo logro
                            val (title, description) = getAchievementInfo(achievementId)
                            viewModelScope.launch {
                                _newAchievementUnlocked.emit(
                                    Achievement(
                                        id = achievementId,
                                        title = title,
                                        description = description,
                                        imageUrl = "gs://pesca-huka.firebasestorage.app/images/$achievementId.png",
                                        isUnlocked = true,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                }
            }
    }
}


data class Achievement(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val isUnlocked: Boolean = false,
    val type: String = "PERMANENT",
    val timestamp: Long? = null
)