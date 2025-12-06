package com.example.juka.identificar

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.juka.FishIdentifier // Asegúrate de importar tu clase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificarPezScreen() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Aquí guardaremos la respuesta completa de Gemini
    var resultadoIdentificacion by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🧠 INSTANCIAMOS EL CEREBRO DE GEMINI
    // Usamos 'remember' para no crearlo cada vez que la pantalla parpadea
    val fishIdentifier = remember {
        FishIdentifier(context.applicationContext as Application)
    }

    // --- FUNCIONES AUXILIARES ---

    // Función para convertir la URI de galería a un Archivo real que Gemini pueda leer
    fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Función que realiza el análisis real
    fun analizarImagen(uri: Uri) {
        isAnalyzing = true
        resultadoIdentificacion = null // Limpiamos resultado anterior

        scope.launch {
            try {
                // 1. Convertimos la URI a un archivo físico
                val file = uriToFile(context, uri)

                if (file != null) {
                    // 2. 🔥 LLAMADA A LA IA (Aquí ocurre la magia)
                    val respuestaIA = fishIdentifier.identifyFish(file.absolutePath)

                    // 3. Guardamos la respuesta
                    resultadoIdentificacion = respuestaIA
                } else {
                    resultadoIdentificacion = "Error: No se pudo procesar el archivo de imagen."
                }
            } catch (e: Exception) {
                resultadoIdentificacion = "Error al conectar con el guía de pesca: ${e.localizedMessage}"
            } finally {
                isAnalyzing = false
            }
        }
    }

    // --- LAUNCHERS ---

    fun crearUriTemporal(): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = context.cacheDir
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
        } catch (e: Exception) {
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && imageUri != null) {
            // Si sacó la foto, analizamos inmediatamente
            analizarImagen(imageUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            imageUri = it
            // Si eligió de galería, analizamos inmediatamente
            analizarImagen(it)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val uri = crearUriTemporal()
            if (uri != null) {
                imageUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    fun abrirCamara() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = crearUriTemporal()
            if (uri != null) {
                imageUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // --- INTERFAZ DE USUARIO (UI) ---

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Identificar Pez",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Sube una foto y el guía virtual la analizará",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Si NO hay imagen seleccionada, mostramos botones grandes
        if (imageUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¿Qué pez pescaste?", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { abrirCamara() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Camera, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cámara")
                        }
                        OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Galería")
                        }
                    }
                }
            }
        }

        // Si HAY imagen, mostramos la foto y el resultado
        imageUri?.let { uri ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Captura",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Área de Estado / Resultado
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        if (isAnalyzing) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Consultando al experto...", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else if (resultadoIdentificacion != null) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reporte del Guía:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                // 🔥 AQUI MOSTRAMOS LA RESPUESTA DE GEMINI
                                Text(
                                    text = resultadoIdentificacion!!,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción final
            if (!isAnalyzing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            imageUri = null
                            resultadoIdentificacion = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Otra Foto")
                    }

                    if (resultadoIdentificacion != null) {
                        Button(
                            onClick = { analizarImagen(uri) }, // Reintentar
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}