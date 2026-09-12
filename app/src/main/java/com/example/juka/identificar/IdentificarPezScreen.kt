package com.example.juka.identificar

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.juka.domain.usecase.FishIdentifier
import com.example.juka.domain.usecase.ModoIdentificacion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import com.example.juka.HukaApplication
import androidx.navigation.NavController
import com.example.juka.ui.theme.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificarPezScreen(navController: NavController) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var resultadoIdentificacion by remember { mutableStateOf<String?>(null) }
    // Peces detectados en la foto (nombre → cantidad). Gemini (premium) puede
    // devolver varias especies; el estándar, una sola.
    var pecesDetectados by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    // Estándar por default: es el ilimitado, así nadie gasta sin querer el
    // único uso premium del día apenas entra a la pantalla.
    var modoSeleccionado by remember { mutableStateOf(ModoIdentificacion.ESTANDAR) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ✅ MOVER ESTAS DECLARACIONES AQUÍ ARRIBA (ANTES DE USARLAS)
    val application = context.applicationContext as HukaApplication
    val quotaManager = remember { application.chatQuotaManager }
    val quotaState by quotaManager.quotaState.collectAsState()
    val networkMonitor = remember { application.networkMonitor }
    val hayConexion by networkMonitor.isOnline.collectAsState()

    // Premium (Gemini) necesita internet. Si el usuario se queda sin señal con
    // premium seleccionado, volvemos a estándar para no intentar una llamada
    // que va a fallar.
    LaunchedEffect(hayConexion) {
        if (!hayConexion && modoSeleccionado == ModoIdentificacion.PREMIUM) {
            modoSeleccionado = ModoIdentificacion.ESTANDAR
        }
    }

    val fishIdentifier = remember {
        FishIdentifier(context.applicationContext as Application)
    }

    // Funciones auxiliares (mantener las existentes)
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

    fun analizarImagen(uri: Uri) {
        isAnalyzing = true
        resultadoIdentificacion = null
        pecesDetectados = emptyList()
        errorMessage = null

        scope.launch {
            try {
                val file = uriToFile(context, uri)
                if (file == null) {
                    errorMessage = "Error: No se pudo procesar el archivo de imagen."
                    return@launch
                }

                when (modoSeleccionado) {
                    ModoIdentificacion.PREMIUM -> {
                        // Chequeo de cuota ANTES de gastar la foto — el aviso
                        // "ya usaste tu premium de hoy" ya se muestra antes de
                        // llegar acá (ver gate en ImageSelectorCard), esto es
                        // el resguardo final por si igual se llega a intentar.
                        if (!quotaManager.canMakePhotoQuery()) {
                            showQuotaDialog = true
                            return@launch
                        }
                        val respuestaIA = fishIdentifier.identifyPremium(file.absolutePath)
                        val consumed = quotaManager.consumePhotoQuery()
                        // Parseamos la lista de peces detectados y sacamos el tag
                        // del texto que se muestra al usuario.
                        val respuestaLimpia = limpiarTagPeces(respuestaIA)
                        pecesDetectados = extraerPecesDetectados(respuestaIA).ifEmpty {
                            // Fallback: si Gemini no listó, usamos la especie del
                            // bloque de identificación con cantidad 1.
                            if (esResultadoValido(respuestaLimpia))
                                extraerNombreEspecie(respuestaLimpia)?.let { listOf(it to 1) } ?: emptyList()
                            else emptyList()
                        }
                        resultadoIdentificacion = if (consumed) {
                            respuestaLimpia + "\n\n_${quotaManager.getPhotoQuotaMessage()}_"
                        } else {
                            respuestaLimpia
                        }
                    }
                    ModoIdentificacion.ESTANDAR -> {
                        val res = fishIdentifier.identifyEstandar(
                            file.absolutePath,
                            hayConexion = networkMonitor.isOnlineNow()
                        )
                        resultadoIdentificacion = res
                        // El estándar identifica una sola especie: si es válida,
                        // la lista tiene un ítem con cantidad 1.
                        pecesDetectados = if (esResultadoValido(res)) {
                            extraerNombreEspecie(res)?.let { listOf(it to 1) } ?: emptyList()
                        } else emptyList()
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Error al conectar con el guía de pesca: ${e.localizedMessage}"
            } finally {
                isAnalyzing = false
            }
        }
    }


    // ── "Crear parte con esta captura" ──────────────────────────────────────
    // Best-effort: Fishial y Modelo Propio son consistentes (siempre
    // arrancan con "🐟 **Nombre**"), pero Gemini (premium) devuelve texto
    // libre — ahí buscamos la línea "Nombre común" del bloque de
    // Identificación. Si no encontramos nada confiable, el campo especie
    // del wizard queda vacío y el usuario lo completa a mano.
    // (extraerNombreEspecie, esResultadoValido, extraerPecesDetectados y
    // limpiarTagPeces se movieron a funciones top-level al final del archivo,
    // así analizarImagen puede llamarlas sin problema de orden de declaración.)

    // Total de ejemplares detectados (suma de todas las especies).
    fun totalDetectado(): Int = pecesDetectados.sumOf { it.second }

    fun crearParteConCaptura(uri: Uri) {
        if (pecesDetectados.isEmpty()) return
        // Codificamos la lista como "nombre:cantidad|nombre:cantidad".
        val especiesEncoded = pecesDetectados.joinToString("|") { "${it.first}:${it.second}" }
        navController.navigate(
            Screen.Wizard.buildRoute(
                fotoUri = uri.toString(),
                especies = especiesEncoded
            )
        )
    }

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

    // Launchers
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && imageUri != null) {
            analizarImagen(imageUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            imageUri = it
            analizarImagen(it)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val uri = crearUriTemporal()
            if (uri != null) {
                imageUri = uri
                cameraLauncher.launch(uri)
            } else {
                errorMessage = "Error al crear archivo temporal para la cámara."
            }
        } else {
            errorMessage = "Permiso de cámara denegado. Por favor, habilítalo en ajustes."
        }
    }

    fun abrirCamara() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = crearUriTemporal()
            if (uri != null) {
                imageUri = uri
                cameraLauncher.launch(uri)
            } else {
                errorMessage = "Error al crear archivo temporal para la cámara."
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Phishing,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Identificar Captura",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header con descripción
                AnimatedVisibility(
                    visible = imageUri == null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "🎣 Descubre tu captura",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "El guía virtual analizará tu foto y te dará información detallada",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                // Selector de modo: estándar (ilimitado) vs premium (Gemini, 1/día)
                ModoSelector(
                    modo = modoSeleccionado,
                    // Premium disponible = queda cuota Y hay conexión.
                    premiumDisponible = quotaState.photosRemaining > 0 && hayConexion,
                    sinConexion = !hayConexion,
                    onSelect = { modoSeleccionado = it }
                )

                // Aviso de fallback offline: si no hay conexión, el modo
                // estándar va a usar el modelo local automáticamente. Mejor
                // que el usuario lo sepa antes de sacar la foto y no se
                // sorprenda con un resultado distinto al esperado. Premium
                // (Gemini) sí necesita conexión sí o sí, así que ahí no
                // aplica el fallback.
                if (!hayConexion) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Sin conexión: la estándar va a usar el modelo local (reconoce menos especies)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selector de imagen cuando no hay imagen
                AnimatedVisibility(
                    visible = imageUri == null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    // Si eligió premium y ya no le queda cuota, avisamos ACÁ
                    // — antes de que saque o elija una foto — en vez de
                    // dejarlo enterarse recién después de analizar.
                    if (modoSeleccionado == ModoIdentificacion.PREMIUM && quotaState.photosRemaining <= 0) {
                        PremiumAgotadoCard(
                            onUsarEstandar = { modoSeleccionado = ModoIdentificacion.ESTANDAR }
                        )
                    } else {
                        ImageSelectorCard(
                            onCameraClick = { abrirCamara() },
                            onGalleryClick = { galleryLauncher.launch("image/*") }
                        )
                    }
                }

                // Mostrar imagen y resultado cuando hay imagen
                AnimatedVisibility(
                    visible = imageUri != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        imageUri?.let { uri ->
                            ImageAnalysisCard(
                                imageUri = uri,
                                isAnalyzing = isAnalyzing,
                                resultado = resultadoIdentificacion,
                                error = errorMessage,
                                onImageClick = { showImageDialog = true }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // CTA para arrancar un parte de pesca a partir de
                            // la captura ya identificada — precarga la foto
                            // y (si se pudo reconocer) la especie en el
                            // wizard, en vez de que el usuario tenga que
                            // cargar todo desde cero.
                            AnimatedVisibility(
                                // Solo ofrecemos "crear parte" si detectamos al
                                // menos un pez. Si no es un pez, la lista queda
                                // vacía → no aparece el botón.
                                visible = !isAnalyzing && pecesDetectados.isNotEmpty(),
                                enter = fadeIn() + slideInVertically()
                            ) {
                                Column {
                                    Button(
                                        onClick = { crearParteConCaptura(uri) },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (totalDetectado() > 1)
                                                "Crear parte con estas ${totalDetectado()} capturas"
                                            else
                                                "Crear parte con esta captura",
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Botones de acción
                            AnimatedVisibility(
                                visible = !isAnalyzing,
                                enter = fadeIn() + slideInVertically()
                            ) {
                                ActionButtons(
                                    onNewPhoto = {
                                        imageUri = null
                                        resultadoIdentificacion = null
                                        errorMessage = null
                                    },
                                    onRetry = if (resultadoIdentificacion != null || errorMessage != null) {
                                        { analizarImagen(uri) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo para ver imagen completa con zoom
    if (showImageDialog && imageUri != null) {
        FullImageDialog(
            imageUri = imageUri!!,
            onDismiss = { showImageDialog = false }
        )
    }

    // Diálogo de cuota premium agotada. Antes showQuotaDialog se ponía en
    // true pero no había ningún diálogo que lo leyera — se "activaba" un
    // estado que no tenía ninguna UI atada, así que no pasaba nada visible.
    if (showQuotaDialog) {
        AlertDialog(
            onDismissRequest = { showQuotaDialog = false },
            icon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Identificación premium agotada") },
            text = { Text(quotaManager.getPhotoQuotaMessage()) },
            confirmButton = {
                TextButton(onClick = {
                    modoSeleccionado = ModoIdentificacion.ESTANDAR
                    showQuotaDialog = false
                }) {
                    Text("Usar estándar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuotaDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Diálogo de error
    errorMessage?.let {
        if (imageUri == null) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                icon = {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Error") },
                text = { Text(it) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("Entendido")
                    }
                }
            )
        }
    }
}

@Composable
fun ImageSelectorCard(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Ícono animado
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Captura tu pez",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OptionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PhotoCamera,
                        text = "Cámara",
                        isPrimary = true,
                        onClick = onCameraClick
                    )

                    OptionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.PhotoLibrary,
                        text = "Galería",
                        isPrimary = false,
                        onClick = onGalleryClick
                    )
                }
            }
        }
    }
}

@Composable
fun OptionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val colors = if (isPrimary) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = colors,
        border = if (!isPrimary) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ImageAnalysisCard(
    imageUri: Uri,
    isAnalyzing: Boolean,
    resultado: String?,
    error: String?,
    onImageClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Imagen con indicador de que es clickeable. Mientras se analiza,
            // mostramos el indicador de progreso COMO OVERLAY encima de la
            // imagen (no debajo) para que la foto siempre quede visible.
            // Antes el "Consultando a Huka..." iba en un AnimatedContent
            // debajo que hacía crecer el card y empujaba la imagen fuera de
            // la zona visible del scroll → el usuario percibía "se perdió".
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clickable(enabled = !isAnalyzing) { onImageClick() }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "Foto del pez",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Overlay semi-transparente + spinner mientras se analiza.
                // No oculta la imagen, solo la atenúa.
                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Consultando a Huka...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                } else {
                    // Badge "ampliar" solo si NO está analizando
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 4.dp
                    ) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = "Ampliar imagen",
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Contenido del análisis: solo se muestra cuando hay resultado o
            // error. Durante "analyzing" no renderizamos nada acá (el spinner
            // está sobre la imagen). En "idle" tampoco — no hay nada que mostrar.
            AnimatedContent(
                targetState = when {
                    error != null -> "error"
                    resultado != null -> "result"
                    else -> "idle"
                },
                transitionSpec = {
                    fadeIn() with fadeOut()
                }
            ) { state ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (state == "idle") 0.dp else 20.dp)
                ) {
                    when (state) {
                        "error" -> ErrorContent(error ?: "")
                        "result" -> ResultContent(resultado ?: "")
                        // "idle" → no renderizamos nada (Box vacío sin padding)
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyzingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Consultando a Huka...",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Analizando características del pez",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun ErrorContent(error: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Error en el análisis",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    error,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ResultContent(resultado: String) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(6.dp)
                        .fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Análisis del Guía",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = parseMarkdown(resultado),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun ActionButtons(
    onNewPhoto: () -> Unit,
    onRetry: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = onNewPhoto,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nueva Foto", fontWeight = FontWeight.Medium)
        }

        onRetry?.let {
            Button(
                onClick = it,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reintentar", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun FullImageDialog(
    imageUri: Uri,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() }
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "Imagen ampliada",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        }
                    },
                contentScale = ContentScale.Fit
            )

            // Botón cerrar
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White
                )
            }

            // Indicador de zoom
            if (scale != 1f) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Reemplaza al viejo selector de 3 modelos (Gemini/Fishial/Local) sin
 * ninguna explicación de qué significaba cada uno. Ahora son 2 tarjetas con
 * copy claro: qué hace cada modo y por qué elegir uno u otro.
 */
@Composable
fun ModoSelector(
    modo: ModoIdentificacion,
    premiumDisponible: Boolean,
    sinConexion: Boolean = false,
    onSelect: (ModoIdentificacion) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ModoCard(
            titulo = "Identificación estándar",
            subtitulo = "Reconoce cualquier especie. Sin límite de usos.",
            icon = Icons.Default.Phishing,
            seleccionado = modo == ModoIdentificacion.ESTANDAR,
            onClick = { onSelect(ModoIdentificacion.ESTANDAR) }
        )
        ModoCard(
            titulo = "Identificación premium",
            subtitulo = when {
                sinConexion -> "Necesita conexión a internet"
                premiumDisponible -> "Más precisa, con IA Gemini · 1 uso gratis hoy"
                else -> "Ya usaste tu análisis de hoy · se renueva a medianoche"
            },
            icon = Icons.Default.AutoAwesome,
            destacado = true,
            atenuado = !premiumDisponible,
            seleccionado = modo == ModoIdentificacion.PREMIUM,
            // No seleccionable si no está disponible (sin cuota o sin señal).
            onClick = { if (premiumDisponible) onSelect(ModoIdentificacion.PREMIUM) }
        )
    }
}

@Composable
private fun ModoCard(
    titulo: String,
    subtitulo: String,
    icon: ImageVector,
    seleccionado: Boolean,
    destacado: Boolean = false,
    atenuado: Boolean = false,
    onClick: () -> Unit
) {
    val colorAcento = if (destacado) Color(0xFFEF9F27) else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (seleccionado) colorAcento.copy(alpha = if (atenuado) 0.12f else 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (seleccionado) BorderStroke(1.5.dp, colorAcento.copy(alpha = if (atenuado) 0.4f else 1f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (atenuado) 0.6f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colorAcento,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitulo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Indicador compacto en vez de RadioButton: el RadioButton de
            // M3 fuerza un área táctil mínima de ~48dp que hacía la fila
            // mucho más alta de lo que el contenido necesitaba.
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (seleccionado)
                            Modifier.background(colorAcento, CircleShape)
                        else
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
            )
        }
    }
}

// ── Helpers de parseo del resultado del identificador (top-level: puros y
// reutilizables, sin depender del estado del composable) ────────────────────

/** Extrae el nombre común de la especie del texto del identificador. */
private fun extraerNombreEspecie(resultado: String): String? {
    val comunRegex = Regex("(?i)nombre com[uú]n[:\\s]*\\**\\s*([^\\n*]+)")
    comunRegex.find(resultado)?.let { match ->
        val nombre = match.groupValues[1].trim().trimEnd('.', ':').trim()
        if (nombre.isNotBlank() && nombre.length < 60) return nombre
    }
    val pezRegex = Regex("🐟\\s*\\*\\*([^*]+)\\*\\*")
    pezRegex.findAll(resultado).forEach { m ->
        val candidato = m.groupValues[1].trim()
        if (!candidato.equals("Especie identificada", ignoreCase = true)) return candidato
    }
    val idx = resultado.indexOf("Especie identificada")
    if (idx >= 0) {
        Regex("\\*\\*([^*]+)\\*\\*").find(resultado.substring(idx + "Especie identificada".length))?.let {
            return it.groupValues[1].trim()
        }
    }
    return null
}

/** ¿El resultado es un pez válido? (no error, no "no es un pez", no incierto) */
private fun esResultadoValido(resultado: String): Boolean {
    return !resultado.startsWith("❌") &&
            !resultado.contains("🤔") &&
            !resultado.contains("no se pesca", ignoreCase = true) &&
            !resultado.contains("no parece un pez", ignoreCase = true) &&
            !resultado.contains("no estoy seguro", ignoreCase = true)
}

/**
 * Parsea "PECES_DETECTADOS: 2 Pejerrey, 1 Pacú, Tiburón" → lista (nombre →
 * cantidad), AGRUPANDO y SUMANDO duplicados. Así "3 Pejerrey" y "Pejerrey,
 * Pejerrey, Pejerrey" dan lo mismo (Pejerrey x3). Vacío si no hay peces.
 */
private fun extraerPecesDetectados(resultado: String): List<Pair<String, Int>> {
    val linea = Regex("""PECES_DETECTADOS:\s*(.+)""", RegexOption.IGNORE_CASE)
        .find(resultado)?.groupValues?.get(1)?.trim() ?: return emptyList()
    if (linea.equals("ninguno", ignoreCase = true) || linea.isBlank()) return emptyList()

    val acumulado = LinkedHashMap<String, Int>()
    for (item in linea.split(",")) {
        val texto = item.trim()
        if (texto.isBlank()) continue
        val m = Regex("""^(\d+)?\s*(.+)$""").find(texto) ?: continue
        val cantidad = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val nombre = m.groupValues[2].trim().trimEnd('.').replaceFirstChar { it.uppercase() }
        if (nombre.isBlank()) continue
        val clave = nombre.lowercase()
        val nombreLindo = acumulado.keys.firstOrNull { it.lowercase() == clave } ?: nombre
        acumulado[nombreLindo] = (acumulado[nombreLindo] ?: 0) + cantidad
    }
    return acumulado.map { it.key to it.value }
}

/** Saca la línea del tag PECES_DETECTADOS para no mostrarla al usuario. */
private fun limpiarTagPeces(resultado: String): String {
    return resultado
        .replace(Regex("""(?im)^\s*PECES_DETECTADOS:.*$"""), "")
        .trim()
}

/**
 * Reemplaza a ImageSelectorCard cuando el usuario tiene "premium"
 * seleccionado pero ya no le queda cuota hoy — se muestra ANTES de sacar o
 * elegir una foto, así no pierde el paso completo para enterarse recién al
 * final (el problema que tenía el diálogo de cuota original).
 */
@Composable
fun PremiumAgotadoCard(onUsarEstandar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Ya usaste tu identificación premium de hoy",
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Se renueva a medianoche. Mientras tanto, la estándar reconoce igual cualquier especie.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onUsarEstandar, shape = RoundedCornerShape(12.dp)) {
                Text("Usar identificación estándar")
            }
        }
    }
}

@Composable
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEach { line ->
            var currentLine = line

            // Procesar negritas y cursivas
            val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
            val italicPattern = Regex("\\*(.*?)\\*")

            var lastIndex = 0

            // Primero procesamos negritas
            boldPattern.findAll(currentLine).forEach { matchResult ->
                append(currentLine.substring(lastIndex, matchResult.range.first))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(matchResult.groupValues[1])
                }
                lastIndex = matchResult.range.last + 1
            }

            // Agregamos el resto de la línea
            if (lastIndex < currentLine.length) {
                append(currentLine.substring(lastIndex))
            }

            append("\n")
        }
    }
}