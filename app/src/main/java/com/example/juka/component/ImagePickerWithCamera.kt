package com.example.juka.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper composable que devuelve una lambda para disparar un selector unificado
 * de imagen: el usuario elige entre tomar foto con cámara o seleccionar de
 * galería.
 *
 * Flujo:
 *  1. La lambda devuelta abre un ModalBottomSheet con las dos opciones.
 *  2. "Tomar foto" → si no hay permiso CAMERA lo pide runtime → crea un Uri
 *     temporal vía FileProvider en cacheDir → lanza ACTION_IMAGE_CAPTURE →
 *     cuando vuelve OK, invoca `onImagePicked(uri)` con la Uri del archivo
 *     ya escrito.
 *  3. "Elegir de la galería" → lanza GetContent con tipo image, e invoca
 *     `onImagePicked(uri)` con la content:// devuelta.
 *
 * Tanto la Uri de cámara (via FileProvider) como la de galería son
 * legibles por el resto del pipeline existente (`imageHelper.saveImageToInternalStorage`
 * acepta ambas).
 *
 * Uso típico:
 * ```
 * val showPicker = rememberImagePickerWithCamera { uri ->
 *     viewModel.sendImageMessage(uri.toString())
 * }
 * IconButton(onClick = showPicker) { ... }
 * 
 **/
@Composable
fun rememberImagePickerWithCamera(
    onImagePicked: (Uri) -> Unit
): () -> Unit {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    // ── Launcher: galería ─────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(onImagePicked)
    }

    // Para la cámara necesitamos crear PRIMERO el Uri donde se va a
    // escribir la foto y pasarlo al contract TakePicture. Lo guardamos en
    // un state para poder reusarlo en el callback (el contract devuelve
    // solo un Boolean de éxito, no la Uri).
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // ── Launcher: cámara ──────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            pendingCameraUri?.let(onImagePicked)
        }
        pendingCameraUri = null
    }

    // Función que efectivamente lanza la cámara (asumiendo que ya hay permiso).
    val lanzarCamara: () -> Unit = {
        try {
            val uri = crearUriTemporalCamara(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("ImagePicker", "Error preparando cámara: ${e.message}", e)
            Toast.makeText(
                context,
                "No pudimos abrir la cámara",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Launcher: permiso de cámara runtime ───────────────────────────
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lanzarCamara()
        } else {
            Toast.makeText(
                context,
                "Sin permiso de cámara no podemos tomar la foto",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Sheet de selección ────────────────────────────────────────────
    if (showSheet) {
        ImagePickerBottomSheet(
            onDismiss = { showSheet = false },
            onPickCamera = {
                showSheet = false
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) lanzarCamara()
                else cameraPermLauncher.launch(Manifest.permission.CAMERA)
            },
            onPickGallery = {
                showSheet = false
                galleryLauncher.launch("image/*")
            }
        )
    }

    return { showSheet = true }
}

/**
 * Crea un archivo temporal en `cacheDir` y devuelve su Uri vía FileProvider,
 * lista para pasársela a ActivityResultContracts.TakePicture. El archivo
 * se sobrescribe en cada captura — no acumulamos. Android limpia cacheDir
 * automáticamente cuando hace falta espacio.
 */
private fun crearUriTemporalCamara(context: Context): Uri {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

/**
 * Bottom sheet con dos opciones: cámara o galería. Estilo minimal, sin
 * decoración de más, para que el usuario decida rápido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePickerBottomSheet(
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Agregar foto",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            OpcionPicker(
                titulo = "Tomar foto",
                descripcion = "Usar la cámara del teléfono",
                icono = Icons.Default.CameraAlt,
                onClick = onPickCamera
            )

            OpcionPicker(
                titulo = "Elegir de la galería",
                descripcion = "Seleccionar una foto existente",
                icono = Icons.Default.PhotoLibrary,
                onClick = onPickGallery
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OpcionPicker(
    titulo: String,
    descripcion: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = titulo,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = descripcion,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}