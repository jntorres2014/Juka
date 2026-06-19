// PescadexScreen.kt - Pescadex como coleccionable + log personal de capturas.
// Reglas de UX que sigue esta pantalla:
//   - Las capturas se llenan SOLO desde el flujo automático del parte. No hay
//     botón "La Pesqué" en el detalle (antes existía y confundía).
//   - Foto y peso son récord personal manual: el usuario los edita explícitamente
//     desde el bloque "Mi Récord" del modal de detalle.
//   - "Mejor día" (cantidad récord en un solo parte + fecha) se trackea
//     automáticamente al guardar un parte y se muestra en verde en la card.
//   - El catálogo viene de FishDatabase, así que muchas especies no traen
//     descripcion/region/consejo: ocultamos esas InfoSections si están vacías.
package com.example.juka.ui.theme

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.juka.EspecieConEstado
import com.example.juka.EspecieDescubierta
import com.example.juka.EstadisticasPescadex
import com.example.juka.PescadexManager
import com.example.juka.RegistroResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Filtro de visibilidad para el grid. "Todas" muestra capturadas + por descubrir.
 */
private enum class PescadexFiltro(val label: String) {
    TODAS("Todas"),
    CAPTURADAS("Capturadas"),
    POR_DESCUBRIR("Por descubrir")
}

/**
 * Orden del grid. Por defecto va por rareza (mantiene la lógica original).
 */
private enum class PescadexOrden(val label: String) {
    RAREZA("Por rareza"),
    ALFABETICO("Alfabético"),
    RECIENTE("Más recientes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PescadexScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pescadexManager = remember { PescadexManager(context) }

    var especies by remember { mutableStateOf<List<EspecieConEstado>>(emptyList()) }
    var estadisticas by remember { mutableStateOf<EstadisticasPescadex?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var especieSeleccionada by remember { mutableStateOf<EspecieConEstado?>(null) }
    // Cuando no es null, se muestra el dialog "preview de récord para compartir"
    // estilo AchievementDetailDialog. El share efectivo no se dispara hasta que
    // el usuario confirme tocando el botón verde dentro del dialog.
    var especieParaCompartir by remember { mutableStateOf<EspecieConEstado?>(null) }

    // Filtros / orden
    var filtro by remember { mutableStateOf(PescadexFiltro.TODAS) }
    var orden by remember { mutableStateOf(PescadexOrden.RAREZA) }

    // Cargar datos al iniciar. Usamos directamente la coroutine del
    // LaunchedEffect (vinculada al lifecycle del composable) en vez de
    // lanzar un scope.launch interno — ese patrón causaba que el scope se
    // cancelara durante la primera composición del NavHost, devolviendo
    // emptyList() silenciosamente y dejando el grid vacío.
    LaunchedEffect(Unit) {
        try {
            especies = pescadexManager.obtenerEspeciesConEstado()
            estadisticas = pescadexManager.obtenerEstadisticasPescadex()
        } catch (e: CancellationException) {
            // El composable se desmontó mientras cargábamos. No es un error
            // real: lo re-lanzamos para no romper la cancelación cooperativa
            // y no logueamos ruido.
            throw e
        } catch (e: Exception) {
            Log.e("PESCADEX", "Error cargando: ${e.message}", e)
        } finally {
            isLoading = false
        }
    }

    // Función para recargar después de editar récord (acá sí necesitamos
    // scope.launch porque estamos en un callback, no en una coroutine).
    val recargar: () -> Unit = {
        scope.launch {
            try {
                especies = pescadexManager.obtenerEspeciesConEstado()
                estadisticas = pescadexManager.obtenerEstadisticasPescadex()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PESCADEX", "Error recargando: ${e.message}", e)
            }
        }
    }

    // Aplico filtro + orden a la lista. Lo hacemos en cada recomposición —
    // con 30-50 especies es barato y evita meter el estado derivado en un
    // remember complicado.
    val especiesVisibles = remember(especies, filtro, orden) {
        val filtradas = when (filtro) {
            PescadexFiltro.TODAS -> especies
            PescadexFiltro.CAPTURADAS -> especies.filter { it.esCapturada }
            PescadexFiltro.POR_DESCUBRIR -> especies.filter { !it.esCapturada }
        }
        when (orden) {
            PescadexOrden.RAREZA -> filtradas.sortedWith(
                compareBy({ it.orden }, { it.info.nombreComun })
            )
            PescadexOrden.ALFABETICO -> filtradas.sortedBy { it.info.nombreComun }
            PescadexOrden.RECIENTE -> filtradas.sortedByDescending {
                it.datosCaptura?.fechaDescubrimiento?.seconds ?: 0L
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando tu Pescadex...")
                }
            }
        } else {
            // Header con estadísticas
            estadisticas?.let { stats ->
                PescadexHeaderCard(estadisticas = stats)
            }

            // Chips de filtro + dropdown de orden
            FiltrosRow(
                filtro = filtro,
                orden = orden,
                onFiltroChange = { filtro = it },
                onOrdenChange = { orden = it }
            )

            // Grid de especies
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(especiesVisibles) { especieConEstado ->
                    EspecieCard(
                        especieConEstado = especieConEstado,
                        onClick = { especieSeleccionada = especieConEstado },
                        onShareClick = {
                            // Abrimos el preview-dialog en vez de compartir directo.
                            especieParaCompartir = especieConEstado
                        }
                    )
                }
            }
        }
    }

    // Modal de detalles de especie
    especieSeleccionada?.let { especie ->
        EspecieDetalleModal(
            especieConEstado = especie,
            onDismiss = { especieSeleccionada = null },
            onActualizarRecord = { especieId, peso, fotoPath ->
                scope.launch {
                    try {
                        val resultado = pescadexManager.actualizarRecordPersonal(
                            especieId = especieId,
                            peso = peso,
                            fotoLocalPath = fotoPath
                        )
                        when (resultado) {
                            is RegistroResult.Success -> {
                                recargar()
                                // Refresco también la especie seleccionada para
                                // que el modal muestre la foto/peso recién
                                // guardados sin cerrar y reabrir.
                                especieSeleccionada = pescadexManager
                                    .obtenerEspeciesConEstado()
                                    .firstOrNull { it.info.id == especieId }
                            }
                            is RegistroResult.Error -> {
                                Log.e("PESCADEX", "Error guardando récord: ${resultado.mensaje}")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("PESCADEX", "Error actualizando récord: ${e.message}", e)
                    }
                }
            },
            onCompartir = {
                // El botón "Compartir" del bloque "Mi Récord" también abre
                // el preview-dialog primero.
                especieParaCompartir = especie
            }
        )
    }

    // Dialog "preview" estilo AchievementDetailDialog: muestra el récord en
    // grande con fondo gradient oscuro y un botón verde "COMPARTIR RÉCORD".
    // Recién al tocar ese botón se dispara el share efectivo (que genera
    // el PNG y abre WhatsApp / chooser).
    especieParaCompartir?.let { especie ->
        RecordShareDialog(
            especieConEstado = especie,
            onDismiss = { especieParaCompartir = null },
            onConfirmarCompartir = {
                scope.launch {
                    compartirRecordPescadex(context, especie)
                }
                especieParaCompartir = null
            }
        )
    }
}

/**
 * Genera una "estampita" (1080x1350, ratio retrato 4:5 que se ve bien en
 * historias de Instagram y previews de WhatsApp) con el récord personal del
 * usuario y la comparte.
 *
 * Comportamiento del compartir: mismo patrón que `shareAchievement` en
 * `AchievementsViewModel`:
 *   1. Intenta abrir WhatsApp directamente (`setPackage("com.whatsapp")`).
 *   2. Si WhatsApp no está instalado o falla, cae al chooser nativo.
 *   3. Si el chooser también falla, último fallback: compartir solo texto.
 *
 * Composición del bitmap (top → bottom):
 *  1. Gradient de fondo según la rareza de la especie.
 *  2. Header con nombre común grande + nombre científico en itálica abajo.
 *  3. Badge de rareza si no es común.
 *  4. Foto del récord en marco redondeado con sombra interior.
 *  5. Panel inferior blanco-translúcido con stats: peso récord, mejor jornada,
 *     total capturas.
 *  6. Marca "HUKA · Bitácora de Pesca" al pie.
 */
private suspend fun compartirRecordPescadex(
    context: Context,
    especie: EspecieConEstado
) {
    val datos = especie.datosCaptura ?: return
    val info = especie.info

    // Texto que se manda como caption en cualquiera de los 3 paths (WhatsApp,
    // chooser, texto solo). Lo armamos una sola vez.
    val texto = buildString {
        append("🎣 Mi récord de ${info.nombreComun} en Huka\n\n")
        datos.pesoRecord?.let { append("💪 Peso récord: ${formatearPeso(it)} kg\n") }
        if (datos.mejorDiaCantidad > 0) {
            append("🌟 Mejor jornada: ${datos.mejorDiaCantidad} ejemplares")
            datos.mejorDiaFecha?.let { append(" ($it)") }
            append("\n")
        }
        append("📈 Total capturas: ${datos.totalCapturas}\n\n")
        append("¿Podés superarlo? 🐟")
    }

    try {
        // 1. Descargar la foto del récord vía Coil (reusa cache si está).
        //    Si no hay foto cargada, vamos directo al fallback de texto.
        val bitmapPez = if (datos.primeraFoto != null) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(datos.primeraFoto)
                .allowHardware(false) // necesario para poder leerlo como Bitmap
                .build()
            val result = withContext(Dispatchers.IO) { loader.execute(request) }
            if (result is SuccessResult) (result.drawable as BitmapDrawable).bitmap else null
        } else null

        if (bitmapPez == null) {
            compartirRecordSoloTexto(context, texto)
            return
        }

        // 2. Renderizar la estampita en un hilo Default (CPU-bound).
        val bitmap = withContext(Dispatchers.Default) {
            renderEstampitaRecord(info, datos, bitmapPez)
        }

        // 3. Persistir el PNG en cache y obtener el URI vía FileProvider.
        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(cachePath, "record_${info.id}.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        // 4. Primer intento: WhatsApp directo (mismo patrón que logros).
        try {
            val whatsappIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, texto)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }
            context.startActivity(whatsappIntent)
        } catch (e: Exception) {
            Log.w("PESCADEX", "WhatsApp no disponible, abriendo chooser: ${e.message}")

            // 5. Fallback: chooser nativo con la imagen.
            try {
                val chooserIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, texto)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(chooserIntent, "Compartir récord")
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooser)
            } catch (e2: Exception) {
                Log.e("PESCADEX", "Chooser también falló, fallback a texto: ${e2.message}")
                compartirRecordSoloTexto(context, texto)
            }
        }

    } catch (e: Exception) {
        Log.e("PESCADEX", "Error compartiendo récord: ${e.message}", e)
        // Cualquier error inesperado (Coil, FileProvider, Canvas): caemos al
        // texto solo así al menos el usuario puede compartir algo.
        compartirRecordSoloTexto(context, texto)
    }
}

/**
 * Último fallback: comparte solo el texto del récord. Intenta WhatsApp directo
 * primero y, si no está, abre el chooser nativo. Espejo de `shareTextOnly`
 * de `AchievementsViewModel`.
 */
private fun compartirRecordSoloTexto(context: Context, texto: String) {
    val baseIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    try {
        context.startActivity(Intent(baseIntent).apply { setPackage("com.whatsapp") })
    } catch (e: Exception) {
        try {
            val chooser = Intent.createChooser(baseIntent, "Compartir récord vía")
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        } catch (e2: Exception) {
            Log.e("PESCADEX", "No se pudo compartir ni siquiera el texto: ${e2.message}")
        }
    }
}

/**
 * Renderiza la "estampita" coleccionable del récord (1080x1350).
 *
 * Layout vertical (top → bottom):
 *  1. Banner superior con tira de rareza (★ EVENTO ESPECIAL si épico/legendario)
 *  2. Nombre común grande + nombre científico cursiva
 *  3. Foto del récord en CÍRCULO con halo de resplandor (estilo medalla)
 *  4. Card flotante elevada con peso récord en gigante + divisor + secundarias
 *  5. Footer con marca HUKA y tagline
 *
 * Decoraciones:
 *  - Marco doble decorativo en el borde de la estampita
 *  - Halo radial detrás de la foto
 *  - Sombras reales (BlurMaskFilter) en la card y la foto
 *  - Símbolos Unicode antiguos (★ ◆ ●) que SÍ renderizan en Canvas
 *    (a diferencia de los emojis modernos que salen como □)
 */
private fun renderEstampitaRecord(
    info: com.example.juka.EspecieInfo,
    datos: EspecieDescubierta,
    bitmapPez: Bitmap?
): Bitmap {
    val width = 1080
    val height = 1350
    val padding = 50f

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val (colorTop, colorBottom) = coloresGradientRareza(info.rareza)

    // ─── Fondo: gradient diagonal ───
    paint.shader = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        colorTop,
        oscurecerColor(colorBottom, 0.7f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    // Capa de oscurecimiento sutil arriba y abajo para dar profundidad
    paint.shader = LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        intArrayOf(
            android.graphics.Color.argb(60, 0, 0, 0),
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.argb(80, 0, 0, 0)
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    // ─── Marco doble decorativo (borde de la estampita) ───
    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.argb(180, 255, 255, 255)
    paint.strokeWidth = 4f
    canvas.drawRoundRect(
        padding, padding,
        width - padding, height - padding,
        24f, 24f, paint
    )
    paint.color = android.graphics.Color.argb(80, 255, 255, 255)
    paint.strokeWidth = 2f
    canvas.drawRoundRect(
        padding + 14f, padding + 14f,
        width - padding - 14f, height - padding - 14f,
        18f, 18f, paint
    )
    paint.style = Paint.Style.FILL

    // ─── Banner de rareza para épicos/legendarios ───
    if (info.rareza == "epico" || info.rareza == "legendario") {
        val bannerText = "★  ${obtenerTextoRareza(info.rareza).uppercase()}  ★"
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = 32f
        paint.color = android.graphics.Color.argb(255, 255, 215, 0) // dorado
        paint.letterSpacing = 0.15f
        canvas.drawText(bannerText, width / 2f, padding + 80f, paint)
        paint.letterSpacing = 0f
    }

    // ─── Nombre común grande ───
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    paint.textSize = 76f
    paint.color = android.graphics.Color.WHITE
    val nombreY = if (info.rareza == "epico" || info.rareza == "legendario") {
        padding + 160f
    } else {
        padding + 130f
    }
    canvas.drawText(info.nombreComun.uppercase(), width / 2f, nombreY, paint)

    // ─── Nombre científico (cursiva) ───
    if (info.nombreCientifico.isNotBlank()) {
        paint.isFakeBoldText = false
        paint.textSize = 32f
        paint.color = android.graphics.Color.argb(200, 255, 255, 255)
        paint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.ITALIC
        )
        canvas.drawText(info.nombreCientifico, width / 2f, nombreY + 52f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    // ─── Foto del récord en CÍRCULO con halo ───
    val photoCenterX = width / 2f
    val photoCenterY = 660f
    val photoRadius = 240f
    val haloRadius = photoRadius + 40f

    // Halo radial suave detrás de la foto
    paint.shader = android.graphics.RadialGradient(
        photoCenterX, photoCenterY, haloRadius,
        android.graphics.Color.argb(180, 255, 255, 255),
        android.graphics.Color.argb(0, 255, 255, 255),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(photoCenterX, photoCenterY, haloRadius, paint)
    paint.shader = null

    // Sombra del marco circular (BlurMaskFilter para sombra real)
    paint.color = android.graphics.Color.argb(140, 0, 0, 0)
    paint.maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    canvas.drawCircle(photoCenterX, photoCenterY + 10f, photoRadius + 8f, paint)
    paint.maskFilter = null

    // Marco circular blanco (passe-partout)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(photoCenterX, photoCenterY, photoRadius + 8f, paint)

    // Foto con clip circular
    if (bitmapPez != null) {
        val saved = canvas.save()
        val clipPath = android.graphics.Path().apply {
            addCircle(photoCenterX, photoCenterY, photoRadius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Center-crop: calcular srcRect preservando aspect ratio
        val srcSize = minOf(bitmapPez.width, bitmapPez.height)
        val srcOffsetX = (bitmapPez.width - srcSize) / 2
        val srcOffsetY = (bitmapPez.height - srcSize) / 2
        val srcRect = Rect(srcOffsetX, srcOffsetY, srcOffsetX + srcSize, srcOffsetY + srcSize)
        val dstRect = RectF(
            photoCenterX - photoRadius, photoCenterY - photoRadius,
            photoCenterX + photoRadius, photoCenterY + photoRadius
        )
        canvas.drawBitmap(bitmapPez, srcRect, dstRect, paint)
        canvas.restoreToCount(saved)
    } else {
        // Fallback: círculo gris con símbolo del pez
        paint.color = android.graphics.Color.argb(255, 230, 230, 235)
        canvas.drawCircle(photoCenterX, photoCenterY, photoRadius, paint)
        paint.color = android.graphics.Color.argb(140, 0, 0, 0)
        paint.textSize = 100f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("?", photoCenterX, photoCenterY + 36f, paint)
    }

    // Anillo dorado/coloreado alrededor de la foto (borde de la "medalla")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 6f
    paint.color = android.graphics.Color.argb(180, 255, 255, 255)
    canvas.drawCircle(photoCenterX, photoCenterY, photoRadius + 4f, paint)
    paint.style = Paint.Style.FILL

    // ─── Card flotante de stats ───
    val cardTop = 970f
    val cardBottom = height - padding - 130f
    val cardLeft = padding + 30f
    val cardRight = width - padding - 30f
    val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
    val cardRadius = 28f

    // Sombra debajo de la card
    paint.color = android.graphics.Color.argb(100, 0, 0, 0)
    paint.maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    canvas.drawRoundRect(
        RectF(cardLeft + 4f, cardTop + 10f, cardRight + 4f, cardBottom + 10f),
        cardRadius, cardRadius, paint
    )
    paint.maskFilter = null

    // Card blanca
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(cardRect, cardRadius, cardRadius, paint)

    paint.textAlign = Paint.Align.CENTER
    var cursorY = cardTop + 60f

    // Label "RÉCORD PERSONAL"
    datos.pesoRecord?.let { peso ->
        paint.isFakeBoldText = true
        paint.textSize = 24f
        paint.color = android.graphics.Color.argb(255, 120, 120, 130)
        paint.letterSpacing = 0.2f
        canvas.drawText("RÉCORD PERSONAL", width / 2f, cursorY, paint)
        paint.letterSpacing = 0f
        cursorY += 80f

        // Peso GIGANTE en color de rareza
        paint.textSize = 110f
        paint.color = colorTop
        canvas.drawText("${formatearPeso(peso)} kg", width / 2f, cursorY, paint)
        cursorY += 50f
    }

    // Divisor decorativo (línea con puntos)
    paint.color = android.graphics.Color.argb(50, 0, 0, 0)
    paint.strokeWidth = 2f
    paint.style = Paint.Style.STROKE
    val divStart = cardLeft + 100f
    val divEnd = cardRight - 100f
    canvas.drawLine(divStart, cursorY, divEnd, cursorY, paint)
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.argb(120, 0, 0, 0)
    canvas.drawCircle((divStart + divEnd) / 2f, cursorY, 4f, paint)

    cursorY += 50f

    // Mejor jornada con símbolo ◆
    if (datos.mejorDiaCantidad > 0) {
        paint.isFakeBoldText = true
        paint.textSize = 30f
        paint.color = android.graphics.Color.argb(255, 46, 125, 50) // verde Huka
        val mejorTxt = buildString {
            append("◆ Mejor jornada: ${datos.mejorDiaCantidad} ejemplares")
            datos.mejorDiaFecha?.let { append("  ·  $it") }
        }
        canvas.drawText(mejorTxt, width / 2f, cursorY, paint)
        cursorY += 50f
    }

    // Total capturas con símbolo ●
    paint.isFakeBoldText = false
    paint.textSize = 28f
    paint.color = android.graphics.Color.argb(255, 80, 80, 90)
    canvas.drawText("●  Total de capturas: ${datos.totalCapturas}", width / 2f, cursorY, paint)

    // ─── Footer: marca HUKA ───
    paint.color = android.graphics.Color.argb(200, 255, 255, 255)
    paint.isFakeBoldText = true
    paint.textSize = 38f
    paint.letterSpacing = 0.3f
    canvas.drawText("H U K A", width / 2f, height - padding - 60f, paint)
    paint.letterSpacing = 0f
    paint.isFakeBoldText = false
    paint.textSize = 22f
    paint.color = android.graphics.Color.argb(140, 255, 255, 255)
    canvas.drawText("Bitácora de pesca", width / 2f, height - padding - 28f, paint)

    return bitmap
}

/**
 * Oscurece un color (entero ARGB) multiplicando R/G/B por `factor` (0..1).
 * Usado para generar la versión "profunda" del color de rareza para el
 * gradient de fondo de la estampita.
 */
private fun oscurecerColor(color: Int, factor: Float): Int {
    val a = (color shr 24) and 0xFF
    val r = ((color shr 16) and 0xFF) * factor
    val g = ((color shr 8) and 0xFF) * factor
    val b = (color and 0xFF) * factor
    return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}

/**
 * Devuelve un par (top, bottom) de colores en formato ARGB para hacer el
 * gradient de fondo de la estampita según la rareza de la especie. El top es
 * el color "vivo" y el bottom una versión más profunda del mismo tono.
 */
private fun coloresGradientRareza(rareza: String): Pair<Int, Int> {
    return when (rareza) {
        "poco_comun" -> Pair(0xFF1976D2.toInt(), 0xFF0D47A1.toInt())
        "raro" -> Pair(0xFFAB47BC.toInt(), 0xFF4A148C.toInt())
        "epico" -> Pair(0xFFFF8F00.toInt(), 0xFFBF360C.toInt())
        "legendario" -> Pair(0xFFFFD54F.toInt(), 0xFFF57F17.toInt())
        else -> Pair(0xFF1E88E5.toInt(), 0xFF0D47A1.toInt()) // común → azul Huka
    }
}

@Composable
private fun FiltrosRow(
    filtro: PescadexFiltro,
    orden: PescadexOrden,
    onFiltroChange: (PescadexFiltro) -> Unit,
    onOrdenChange: (PescadexOrden) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(PescadexFiltro.values().toList()) { f ->
                FilterChip(
                    selected = f == filtro,
                    onClick = { onFiltroChange(f) },
                    label = { Text(f.label) }
                )
            }
        }

        // Dropdown de orden a la derecha
        var ordenMenuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { ordenMenuOpen = true }) {
                Icon(Icons.Default.Sort, contentDescription = "Ordenar")
            }
            DropdownMenu(
                expanded = ordenMenuOpen,
                onDismissRequest = { ordenMenuOpen = false }
            ) {
                PescadexOrden.values().forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o.label) },
                        onClick = {
                            onOrdenChange(o)
                            ordenMenuOpen = false
                        },
                        leadingIcon = {
                            if (o == orden) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PescadexHeaderCard(estadisticas: EstadisticasPescadex) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🐟 Mi Pescadex",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Colección personal de especies",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                // Progreso circular
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = estadisticas.porcentajeCompletado / 100f,
                        modifier = Modifier.size(70.dp),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${estadisticas.porcentajeCompletado.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMiniCard("🎣", estadisticas.progreso, "Especies")
                StatMiniCard("📈", "${estadisticas.totalCapturas}", "Capturas")
                StatMiniCard("🏆", "${estadisticas.logrosDesbloqueados}", "Logros")
                StatMiniCard("📅", "${estadisticas.diasPescando}", "Días")
            }
        }
    }
}

@Composable
fun StatMiniCard(icon: String, valor: String, etiqueta: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(text = icon, fontSize = 24.sp)
        Text(
            text = valor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspecieCard(
    especieConEstado: EspecieConEstado,
    onClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val especie = especieConEstado.info
    val esCapturada = especieConEstado.esCapturada
    val datosCaptura = especieConEstado.datosCaptura
    val puedeCompartir = esCapturada && datosCaptura?.primeraFoto != null && datosCaptura?.pesoRecord != null

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (esCapturada) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (esCapturada)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = 2.dp,
            color = obtenerColorRareza(especie.rareza)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Imagen/Icono del pez. Si la especie está capturada y tiene foto
            // del récord cargada, la mostramos; si no, mostramos el emoji.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (esCapturada)
                            obtenerColorRareza(especie.rareza).copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    esCapturada && datosCaptura?.primeraFoto != null -> {
                        AsyncImage(
                            model = datosCaptura.primeraFoto,
                            contentDescription = especie.nombreComun,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    esCapturada -> {
                        Text(text = obtenerEmojiPez(especie.id), fontSize = 40.sp)
                    }
                    else -> {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }

                // Botón de compartir superpuesto (solo si puede compartir)
                if (puedeCompartir) {
                    IconButton(
                        onClick = { onShareClick() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (esCapturada) especie.nombreComun else "???",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (esCapturada)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // Badge de rareza solo si no es común
            if (esCapturada && especie.rareza != "comun") {
                Surface(
                    color = obtenerColorRareza(especie.rareza).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = obtenerTextoRareza(especie.rareza),
                        style = MaterialTheme.typography.labelSmall,
                        color = obtenerColorRareza(especie.rareza),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Estadísticas personales
            if (esCapturada && datosCaptura != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Peso del récord personal (si lo cargó vía "Mi Récord")
                    datosCaptura.pesoRecord?.let { pr ->
                        Text(
                            text = "💪 ${formatearPeso(pr)} kg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Mejor día (cantidad récord en un solo parte). En verde
                    // para destacarlo como "marca personal" trackeada por
                    // el sistema (no editable por el usuario).
                    if (datosCaptura.mejorDiaCantidad > 0) {
                        Text(
                            text = "📅 ${datosCaptura.mejorDiaCantidad}" +
                                (datosCaptura.mejorDiaFecha?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "🎣 ${datosCaptura.totalCapturas}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (esCapturada) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CAPTURADO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspecieDetalleModal(
    especieConEstado: EspecieConEstado,
    onDismiss: () -> Unit,
    onActualizarRecord: (especieId: String, peso: Double?, fotoLocalPath: String?) -> Unit,
    onCompartir: () -> Unit
) {
    val especie = especieConEstado.info
    val esCapturada = especieConEstado.esCapturada
    val datosCaptura = especieConEstado.datosCaptura

    var editorAbierto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = obtenerEmojiPez(especie.id), fontSize = 32.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Text(
                    text = especie.nombreComun,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = obtenerColorRareza(especie.rareza)
                )

                if (especie.nombreCientifico.isNotBlank()) {
                    Text(
                        text = especie.nombreCientifico,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Badge de rareza + región (si la rareza es algo distinto de común
                // o si hay región — evitamos un badge vacío y feo)
                val mostrarBadge = especie.rareza != "comun" || especie.region.isNotBlank()
                if (mostrarBadge) {
                    val textoBadge = listOfNotNull(
                        if (especie.rareza != "comun") obtenerTextoRareza(especie.rareza) else null,
                        especie.region.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    Surface(
                        color = obtenerColorRareza(especie.rareza).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = textoBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = obtenerColorRareza(especie.rareza),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Información de pesca — ocultamos cada InfoSection si no tiene
                // contenido, así no quedan labels colgando.
                InfoSection("📍 Hábitat", especie.habitat)
                InfoSection("🎣 Carnadas", especie.mejoresCarnadas.joinToString(", "))
                InfoSection("⏰ Mejor horario", especie.mejorHorario)
                InfoSection("🎯 Técnica", especie.tecnica)
                InfoSection("📏 Tamaño", especie.tamaño)
                InfoSection("📅 Temporada", especie.temporada)
                InfoSection("💡 Consejo", especie.consejoEspecial)

                // Bloque "Mi Récord" (solo si capturada)
                if (esCapturada && datosCaptura != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MiRecordSection(
                        datosCaptura = datosCaptura,
                        onEditar = { editorAbierto = true },
                        onCompartir = onCompartir
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    EstadisticasPersonalesSection(datosCaptura)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Editor de récord (foto + peso). Solo aparece si capturada y abierto.
    if (editorAbierto && esCapturada) {
        EditorRecordDialog(
            datosActuales = datosCaptura,
            onCancel = { editorAbierto = false },
            onGuardar = { peso, fotoPath ->
                onActualizarRecord(especie.id, peso, fotoPath)
                editorAbierto = false
            }
        )
    }
}

/**
 * Bloque "Mi Récord" con foto + peso. Si todavía no cargó ninguno, invita
 * a hacerlo con un botón grande "Editar récord". Si ya tiene algo, muestra
 * lo que tiene y un botón más chico para editar.
 */
@Composable
private fun MiRecordSection(
    datosCaptura: EspecieDescubierta,
    onEditar: () -> Unit,
    onCompartir: () -> Unit
) {
    val tieneAlgo = datosCaptura.primeraFoto != null || datosCaptura.pesoRecord != null
    // Se habilita compartir si tiene tanto foto como peso (requisito del usuario)
    val puedeCompartir = datosCaptura.primeraFoto != null && datosCaptura.pesoRecord != null

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📷 Mi Récord",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                Row {
                    if (puedeCompartir) {
                        IconButton(onClick = onCompartir) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartir Récord",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (tieneAlgo) "Editar" else "Agregar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Foto (o placeholder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (datosCaptura.primeraFoto != null) {
                    AsyncImage(
                        model = datosCaptura.primeraFoto,
                        contentDescription = "Foto del récord",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            "Sin foto cargada",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Peso
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💪 Peso récord: ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = datosCaptura.pesoRecord?.let { "${formatearPeso(it)} kg" }
                        ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (datosCaptura.pesoRecord != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Estadísticas que vienen del sistema (no editables): total capturas, mejor
 * día, locaciones. Separadas del bloque editable para que quede claro qué
 * es récord manual del usuario y qué calcula la app.
 */
@Composable
private fun EstadisticasPersonalesSection(datosCaptura: EspecieDescubierta) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🏆 Tus Estadísticas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Capturas totales: ${datosCaptura.totalCapturas}")

            if (datosCaptura.mejorDiaCantidad > 0) {
                Text(
                    text = "🟢 Mejor jornada: ${datosCaptura.mejorDiaCantidad} ejemplares" +
                        (datosCaptura.mejorDiaFecha?.let { " ($it)" } ?: ""),
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }

            if (datosCaptura.locaciones.isNotEmpty()) {
                Text("Lugares: ${datosCaptura.locaciones.joinToString(", ")}")
            }
        }
    }
}

/**
 * Dialog para editar el récord personal. Permite elegir una foto de la
 * galería y/o cargar el peso. Si el usuario cancela, no se guarda nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorRecordDialog(
    datosActuales: EspecieDescubierta?,
    onCancel: () -> Unit,
    onGuardar: (peso: Double?, fotoLocalPath: String?) -> Unit
) {
    val context = LocalContext.current
    var pesoTexto by remember { mutableStateOf(datosActuales?.pesoRecord?.toString() ?: "") }
    var fotoUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> fotoUri = uri }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Editar mi récord") },
        text = {
            Column {
                // Preview de la foto nueva o la actual
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val modelo = fotoUri ?: datosActuales?.primeraFoto
                    if (modelo != null) {
                        AsyncImage(
                            model = modelo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (fotoUri == null) "Elegir foto" else "Cambiar foto")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pesoTexto,
                    onValueChange = { pesoTexto = it.replace(",", ".") },
                    label = { Text("Peso (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val peso = pesoTexto.toDoubleOrNull()
                val path = fotoUri?.let { uriAPath(context, it) }
                onGuardar(peso, path)
            }) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )
}

/**
 * Convierte un Uri (típico de un picker de galería) a un path local que
 * `StorageService.subirImagen(localPath)` pueda leer con `File(localPath)`.
 * Copiamos a un archivo temporal en cache porque content://... no es un
 * file path real.
 */
private fun uriAPath(context: android.content.Context, uri: Uri): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = java.io.File(
            context.cacheDir,
            "pescadex_record_${System.currentTimeMillis()}.jpg"
        )
        tempFile.outputStream().use { out -> input.copyTo(out) }
        input.close()
        tempFile.absolutePath
    } catch (e: Exception) {
        Log.e("PESCADEX", "Error convirtiendo Uri a path: ${e.message}")
        null
    }
}

@Composable
fun InfoSection(titulo: String, contenido: String) {
    // No renderizamos labels colgando si no hay contenido — ocurre con muchas
    // especies del JSON que no traen descripción/región/consejo cargados.
    if (contenido.isBlank()) return

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = contenido,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun CelebracionNuevaEspecieModal(
    resultado: RegistroResult.Success,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎉", fontSize = 64.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "¡Nueva Especie!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = obtenerColorRareza(resultado.especieInfo.rareza)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = obtenerEmojiPez(resultado.especieInfo.id), fontSize = 48.sp)

                    Text(
                        text = resultado.especieInfo.nombreComun,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (resultado.especieInfo.nombreCientifico.isNotBlank()) {
                        Text(
                            text = resultado.especieInfo.nombreCientifico,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Progreso: ${resultado.totalEspecies} especies descubiertas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (resultado.logrosDesbloqueados.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "🏆 ¡Logros desbloqueados!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                        resultado.logrosDesbloqueados.forEach { logro ->
                            Text(
                                text = "${logro.icono} ${logro.nombre}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = obtenerColorRareza(resultado.especieInfo.rareza)
                        )
                    ) {
                        Text("¡Continuar Pescando!", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dialog "preview de récord para compartir" — el equivalente al
 * AchievementDetailDialog de la pantalla de logros, pero adaptado a una
 * especie capturada del Pescadex.
 *
 * Diseño:
 *  - Fondo gradient oscuro (color profundo de la rareza → negro), mismo
 *    espíritu que el dialog de logros que usa #2C3E50 → #000.
 *  - Foto del récord en círculo grande con halo (igual que la estampita).
 *  - Nombre del pez en grande + nombre científico abajo en itálica.
 *  - Card flotante con stats: peso récord destacado, mejor jornada, total.
 *  - Botón verde estilo WhatsApp (#25D366) "COMPARTIR RÉCORD" que ejecuta
 *    el share real.
 *  - Botón texto "Cerrar" abajo para descartar sin compartir.
 */
@Composable
private fun RecordShareDialog(
    especieConEstado: EspecieConEstado,
    onDismiss: () -> Unit,
    onConfirmarCompartir: () -> Unit
) {
    val info = especieConEstado.info
    val datos = especieConEstado.datosCaptura ?: return

    // Tono de acento según la rareza, así el dialog tiene identidad visual
    // según cuán especial es el pez. El gradient va del color al negro.
    val acento = when (info.rareza) {
        "poco_comun" -> Color(0xFF1976D2)
        "raro" -> Color(0xFF8E24AA)
        "epico" -> Color(0xFFFF8F00)
        "legendario" -> Color(0xFFFFB300)
        else -> Color(0xFF1E88E5)
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                acento.copy(alpha = 0.75f),
                                Color(0xFF0D1117)
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tira sutil arriba para rarezas especiales
                if (info.rareza == "epico" || info.rareza == "legendario") {
                    Text(
                        text = "★ ${obtenerTextoRareza(info.rareza).uppercase()} ★",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Foto del récord en círculo grande con halo (estilo logros)
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    )
                    if (datos.primeraFoto != null) {
                        AsyncImage(
                            model = datos.primeraFoto,
                            contentDescription = null,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = obtenerEmojiPez(info.id), fontSize = 64.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Nombre común grande
                Text(
                    text = info.nombreComun,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (info.nombreCientifico.isNotBlank()) {
                    Text(
                        text = info.nombreCientifico,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card de stats flotante
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Peso récord en grande
                        datos.pesoRecord?.let { peso ->
                            Text(
                                text = "RÉCORD PERSONAL",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${formatearPeso(peso)} kg",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Divisor sutil
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Mejor jornada
                        if (datos.mejorDiaCantidad > 0) {
                            Text(
                                text = buildString {
                                    append("◆ Mejor jornada: ${datos.mejorDiaCantidad} ejemplares")
                                    datos.mejorDiaFecha?.let { append(" · $it") }
                                },
                                color = Color(0xFF81C784),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Total capturas
                        Text(
                            text = "● Total de capturas: ${datos.totalCapturas}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón compartir verde WhatsApp (mismo estilo que logros)
                Button(
                    onClick = onConfirmarCompartir,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366)
                    ),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "COMPARTIR RÉCORD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Cerrar", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// --- Helpers ----------------------------------------------------------------

@Composable
fun obtenerColorRareza(rareza: String): Color {
    return when (rareza) {
        "comun" -> MaterialTheme.colorScheme.onSurface
        "poco_comun" -> Color(0xFF2196F3)
        "raro" -> Color(0xFF9C27B0)
        "epico" -> Color(0xFFFF9800)
        "legendario" -> Color(0xFFFFD700)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

fun obtenerTextoRareza(rareza: String): String {
    return when (rareza) {
        "comun" -> "Común"
        "poco_comun" -> "Poco Común"
        "raro" -> "Raro"
        "epico" -> "Épico"
        "legendario" -> "Legendario"
        else -> "Común"
    }
}

fun obtenerEmojiPez(especieId: String): String {
    return when (especieId) {
        "dorado" -> "🐅"
        "surubi" -> "🐆"
        "pejerrey" -> "🐟"
        "pacu" -> "🐡"
        "tararira" -> "🦈"
        "sabalo" -> "🐠"
        "boga" -> "🐟"
        "bagre" -> "🐈"
        "manguruyu", "manguruyú" -> "🐋"
        "pirana", "piraña" -> "🦷"
        else -> "🐟"
    }
}

/**
 * Formatea peso: muestra entero si es entero, decimal si tiene decimales.
 * "1.5" → "1.5", "5.0" → "5". Evita la fealdad de "5.0 kg".
 */
private fun formatearPeso(peso: Double): String {
    return if (peso == peso.toInt().toDouble()) peso.toInt().toString()
    else "%.1f".format(peso)
}
