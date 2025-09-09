package com.example.juka

import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificarPezScreen() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var resultadoIdentificacion by remember { mutableStateOf<String?>(null) }
    var mostrarDetalles by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🔥 FUNCIÓN SIMPLIFICADA QUE FUNCIONA INMEDIATAMENTE
    fun crearUriTemporal(): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"

            // Usar cache dir en lugar de external files
            val storageDir = context.cacheDir

            val imageFile = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            )

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    // 🎯 LAUNCHER MEJORADO CON MANEJO DE ERRORES
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && imageUri != null) {
            // Iniciar análisis
            isAnalyzing = true
            scope.launch {
                try {
                    // Simular análisis de IA (en tu caso usarías FishIdentifier)
                    kotlinx.coroutines.delay(3000)
                    resultadoIdentificacion = "Pejerrey"
                    mostrarDetalles = true
                } catch (e: Exception) {
                    resultadoIdentificacion = "Error identificando pez"
                } finally {
                    isAnalyzing = false
                }
            }
        } else {
            // Si falló la captura, limpiar URI
            imageUri = null
        }
    }

    // 📱 LAUNCHER PARA PERMISOS
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permiso otorgado, abrir cámara
            val uri = crearUriTemporal()
            if (uri != null) {
                imageUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    // Lanzador para seleccionar de galería
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            isAnalyzing = true
            scope.launch {
                try {
                    kotlinx.coroutines.delay(3000)
                    resultadoIdentificacion = "Dorado"
                    mostrarDetalles = true
                } catch (e: Exception) {
                    resultadoIdentificacion = "Error identificando pez"
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    // 📸 FUNCIÓN PARA ABRIR CÁMARA CON VERIFICACIÓN DE PERMISOS
    fun abrirCamara() {
        // Verificar permiso primero
        when {
            context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya otorgado, crear URI y abrir cámara
                val uri = crearUriTemporal()
                if (uri != null) {
                    imageUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    // Manejar error de creación de URI
                    println("Error: No se pudo crear URI temporal")
                }
            }
            else -> {
                // Solicitar permiso
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

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
            text = "Sube una foto y te digo qué especie es",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botones de captura
        if (imageUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "¿Qué pez pescaste?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Toma una foto y te ayudo a identificar la especie",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botones
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { abrirCamara() }, // 🔥 FUNCIÓN CORREGIDA
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tomar Foto")
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Galería")
                        }
                    }
                }
            }
        }

        // Imagen seleccionada
        imageUri?.let { uri ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Pez a identificar",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Estado del análisis
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isAnalyzing) MaterialTheme.colorScheme.secondaryContainer
                                else if (resultadoIdentificacion != null) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isAnalyzing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Text(
                                        "Analizando imagen con IA...",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            resultadoIdentificacion != null -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        "Identificado: $resultadoIdentificacion",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            else -> {
                                Text(
                                    "Presiona 'Identificar' para analizar",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        imageUri = null
                        resultadoIdentificacion = null
                        mostrarDetalles = false
                        isAnalyzing = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nueva Foto")
                }

                if (resultadoIdentificacion != null && !isAnalyzing) {
                    Button(
                        onClick = {
                            // Reiniciar análisis con la misma imagen
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    kotlinx.coroutines.delay(3000)
                                    resultadoIdentificacion = "Trucha arcoíris"
                                    mostrarDetalles = true
                                } catch (e: Exception) {
                                    resultadoIdentificacion = "Error identificando pez"
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-identificar")
                    }
                }
            }
        }

        // Detalles del pez identificado
        if (mostrarDetalles && resultadoIdentificacion != null && !isAnalyzing) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = resultadoIdentificacion!!,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Odontesthes bonariensis",
                        fontSize = 14.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Información del pez
                    InfoSection1("Hábitat", "Lagunas y embalses de agua dulce")
                    InfoSection1("Carnadas", "Lombriz, cascarudos, artificiales pequeños")
                    InfoSection1("Mejor horario", "Todo el día, especialmente mañana")
                    InfoSection1("Técnica", "Pesca con boya o spinning liviano")
                    InfoSection1("Tamaño promedio", "200g - 1kg")

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botón para agregar al reporte
                    Button(
                        onClick = { /* Agregar al reporte actual */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agregar a Mi Reporte")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSection1(titulo: String, contenido: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = titulo,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = contenido,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}