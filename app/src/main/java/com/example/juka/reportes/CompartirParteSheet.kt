package com.example.juka.reportes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.juka.data.firebase.PartePesca
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Qué campos del parte se incluyen al compartir. Por defecto todo prendido
 * menos observaciones (suele ser texto largo/personal) — el usuario decide
 * qué mostrar, en particular puede sacar la ubicación si no quiere revelar
 * dónde pescó.
 */
data class OpcionesCompartir(
    val fecha: Boolean = true,
    val especies: Boolean = true,
    val ubicacion: Boolean = false,
    val fotos: Boolean = true,
    val observaciones: Boolean = false
)

/**
 * Bottom sheet para compartir un parte. Deja elegir qué mostrar y comparte
 * SIEMPRE una imagen (con la foto de la captura si está disponible, o una
 * tarjeta de stats generada si no) — un texto plano se ve mucho peor en
 * WhatsApp/Instagram que una imagen, así que la imagen es la vía principal
 * y el texto queda como caption/fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompartirParteSheet(
    reporte: PartePesca,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var opciones by remember {
        mutableStateOf(OpcionesCompartir(fotos = reporte.fotos.isNotEmpty()))
    }
    var compartiendo by remember { mutableStateOf(false) }

    // Foto original cargada UNA sola vez (evita re-descargarla cada vez que
    // se togglea algún checkbox — el toggle de "Fotos" solo decide si se usa
    // o no en el render, no si se vuelve a pedir).
    var bitmapFotoCargado by remember { mutableStateOf<Bitmap?>(null) }
    var fotoLista by remember { mutableStateOf(reporte.fotos.isEmpty()) }
    LaunchedEffect(reporte.fotos) {
        if (reporte.fotos.isNotEmpty()) {
            bitmapFotoCargado = withContext(Dispatchers.IO) { cargarBitmap(context, reporte.fotos.first()) }
        }
        fotoLista = true
    }

    val lineasPreview = remember(reporte, opciones) { construirLineasPreview(reporte, opciones) }

    // La imagen que se ve acá es EXACTAMENTE la que se va a compartir — no
    // un resumen de texto aparte. Se regenera cada vez que cambian las
    // opciones elegidas.
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generandoPreview by remember { mutableStateOf(true) }
    LaunchedEffect(opciones, bitmapFotoCargado, fotoLista) {
        if (!fotoLista || lineasPreview.isEmpty()) {
            generandoPreview = false
            return@LaunchedEffect
        }
        generandoPreview = true
        previewBitmap = withContext(Dispatchers.Default) {
            renderEstampitaParte(reporte, opciones, if (opciones.fotos) bitmapFotoCargado else null)
        }
        generandoPreview = false
    }

    // Scroll vertical: sin esto, la vista previa (alta, formato 1080x1350)
    // más los 5 checkboxes se pasan del alto visible del bottom sheet en la
    // mayoría de las pantallas y el botón "Compartir" queda inalcanzable,
    // cortado debajo del borde inferior — quedaba armado el contenido pero
    // sin forma de tocarlo.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Compartir parte", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Elegí qué mostrar. Vas a compartir una imagen con estos datos.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OpcionCheckbox(
                label = "Fecha y horario",
                checked = opciones.fecha,
                onCheckedChange = { opciones = opciones.copy(fecha = it) }
            )
            OpcionCheckbox(
                label = "Especies y cantidades",
                checked = opciones.especies,
                onCheckedChange = { opciones = opciones.copy(especies = it) }
            )
            OpcionCheckbox(
                label = "Ubicación exacta",
                checked = opciones.ubicacion,
                enabled = reporte.ubicacion?.nombre != null || reporte.ubicacion?.zona != null,
                onCheckedChange = { opciones = opciones.copy(ubicacion = it) }
            )
            OpcionCheckbox(
                label = "Fotos",
                checked = opciones.fotos,
                enabled = reporte.fotos.isNotEmpty(),
                onCheckedChange = { opciones = opciones.copy(fotos = it) }
            )
            OpcionCheckbox(
                label = "Observaciones",
                checked = opciones.observaciones,
                enabled = !reporte.observaciones.isNullOrBlank(),
                onCheckedChange = { opciones = opciones.copy(observaciones = it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Vista previa — así se va a compartir", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1080f / 1350f)
                    .heightIn(max = 320.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    lineasPreview.isEmpty() -> Text(
                        "Elegí al menos un dato para compartir",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                    previewBitmap != null -> Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Vista previa de la imagen a compartir",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    else -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                compartiendo = true
                scope.launch {
                    try {
                        compartirParte(context, reporte, opciones, previewBitmap)
                    } finally {
                        compartiendo = false
                        onDismiss()
                    }
                }
            },
            enabled = lineasPreview.isNotEmpty() && !compartiendo && !generandoPreview,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (compartiendo) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generando imagen...")
            } else {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compartir", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OpcionCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked && enabled, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            label,
            fontSize = 14.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

private fun construirLineasPreview(reporte: PartePesca, opciones: OpcionesCompartir): List<String> {
    val lineas = mutableListOf<String>()

    if (opciones.fecha) {
        lineas.add("📅 ${formatearFecha(reporte.fecha)} · ${reporte.horaInicio ?: "?"} a ${reporte.horaFin ?: "?"}")
    }
    if (opciones.especies && reporte.peces.isNotEmpty()) {
        val detalle = reporte.peces.joinToString(", ") { "${it.cantidad} ${it.especie}" }
        lineas.add("🐟 ${reporte.cantidadTotal} peces · $detalle")
        if (reporte.cantidadDevuelta > 0) {
            lineas.add("🔄 ${reporte.cantidadDevuelta} devueltos al agua")
        }
    }
    if (opciones.ubicacion) {
        val lugar = reporte.ubicacion?.nombre ?: reporte.ubicacion?.zona
        if (!lugar.isNullOrBlank()) lineas.add("📍 $lugar")
    }
    if (opciones.fotos && reporte.fotos.isNotEmpty()) {
        lineas.add("📷 Foto incluida")
    }
    if (opciones.observaciones && !reporte.observaciones.isNullOrBlank()) {
        lineas.add("📝 ${reporte.observaciones}")
    }
    return lineas
}

private suspend fun compartirParte(
    context: Context,
    reporte: PartePesca,
    opciones: OpcionesCompartir,
    bitmapPrerenderizado: Bitmap?
) {
    val lineas = construirLineasPreview(reporte, opciones)
    val texto = buildString {
        append("🎣 Mi parte de pesca en Huka\n\n")
        lineas.forEach { append(it); append("\n") }
    }

    if (lineas.isEmpty()) return

    try {
        val bitmap = bitmapPrerenderizado ?: run {
            val bitmapFoto = if (opciones.fotos && reporte.fotos.isNotEmpty()) {
                withContext(Dispatchers.IO) { cargarBitmap(context, reporte.fotos.first()) }
            } else null
            withContext(Dispatchers.Default) { renderEstampitaParte(reporte, opciones, bitmapFoto) }
        }

        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(cachePath, "parte_${reporte.id ?: System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)

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
            try {
                val chooserIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, texto)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(chooserIntent, "Compartir parte")
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooser)
            } catch (e2: Exception) {
                Log.e("COMPARTIR_PARTE", "Chooser también falló, fallback a texto: ${e2.message}")
                compartirSoloTexto(context, texto)
            }
        }
    } catch (e: Exception) {
        Log.e("COMPARTIR_PARTE", "Error generando imagen para compartir: ${e.message}", e)
        compartirSoloTexto(context, texto)
    }
}

private fun compartirSoloTexto(context: Context, texto: String) {
    val baseIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    try {
        context.startActivity(Intent(baseIntent).apply { setPackage("com.whatsapp") })
    } catch (e: Exception) {
        try {
            val chooser = Intent.createChooser(baseIntent, "Compartir parte vía")
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        } catch (e2: Exception) {
            Log.e("COMPARTIR_PARTE", "No se pudo compartir ni siquiera el texto: ${e2.message}")
        }
    }
}

private suspend fun cargarBitmap(context: Context, path: String): Bitmap? {
    return try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(if (path.startsWith("http")) path else File(path))
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) (result.drawable as? BitmapDrawable)?.bitmap else null
    } catch (e: Exception) {
        Log.w("COMPARTIR_PARTE", "No se pudo cargar la foto: ${e.message}")
        null
    }
}

private fun renderEstampitaParte(
    reporte: PartePesca,
    opciones: OpcionesCompartir,
    bitmapFoto: Bitmap?
): Bitmap {
    val width = 1080
    val height = 1350
    val padding = 50f

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val colorTop = Color.rgb(0x1D, 0x9E, 0x75)
    val colorBottom = Color.rgb(0x08, 0x50, 0x41)

    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colorTop, colorBottom, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    paint.style = Paint.Style.STROKE
    paint.color = Color.argb(180, 255, 255, 255)
    paint.strokeWidth = 4f
    canvas.drawRoundRect(padding, padding, width - padding, height - padding, 24f, 24f, paint)
    paint.color = Color.argb(80, 255, 255, 255)
    paint.strokeWidth = 2f
    canvas.drawRoundRect(padding + 14f, padding + 14f, width - padding - 14f, height - padding - 14f, 18f, 18f, paint)
    paint.style = Paint.Style.FILL

    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    paint.textSize = 30f
    paint.color = Color.argb(210, 255, 255, 255)
    paint.letterSpacing = 0.2f
    canvas.drawText("HUKA · BITÁCORA DE PESCA", width / 2f, padding + 70f, paint)
    paint.letterSpacing = 0f

    paint.textSize = 58f
    paint.color = Color.WHITE
    val tituloTop = if (opciones.fecha) formatearFecha(reporte.fecha).uppercase() else "PARTE DE PESCA"
    canvas.drawText(tituloTop, width / 2f, padding + 145f, paint)

    if (opciones.fecha) {
        paint.isFakeBoldText = false
        paint.textSize = 30f
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawText("${reporte.horaInicio ?: "?"} a ${reporte.horaFin ?: "?"}", width / 2f, padding + 190f, paint)
    }

    var cursorY = padding + 240f
    val fotoAlto = 560f
    if (bitmapFoto != null) {
        val fotoLeft = padding + 30f
        val fotoRight = width - padding - 30f
        val fotoTop = cursorY
        val fotoBottom = fotoTop + fotoAlto
        val fotoRect = RectF(fotoLeft, fotoTop, fotoRight, fotoBottom)

        paint.color = Color.argb(120, 0, 0, 0)
        paint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(RectF(fotoLeft + 4f, fotoTop + 10f, fotoRight + 4f, fotoBottom + 10f), 28f, 28f, paint)
        paint.maskFilter = null

        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(fotoLeft - 8f, fotoTop - 8f, fotoRight + 8f, fotoBottom + 8f), 32f, 32f, paint)

        val saved = canvas.save()
        val clipPath = Path().apply { addRoundRect(fotoRect, 24f, 24f, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        val srcW = bitmapFoto.width
        val srcH = bitmapFoto.height
        val dstRatio = fotoRect.width() / fotoRect.height()
        val srcRatio = srcW.toFloat() / srcH.toFloat()
        val srcRect = if (srcRatio > dstRatio) {
            val cropW = (srcH * dstRatio).toInt()
            val offsetX = (srcW - cropW) / 2
            Rect(offsetX, 0, offsetX + cropW, srcH)
        } else {
            val cropH = (srcW / dstRatio).toInt()
            val offsetY = (srcH - cropH) / 2
            Rect(0, offsetY, srcW, offsetY + cropH)
        }
        canvas.drawBitmap(bitmapFoto, srcRect, fotoRect, paint)
        canvas.restoreToCount(saved)

        cursorY = fotoBottom + 40f
    } else {
        cursorY += 20f
    }

    val cardTop = cursorY
    val cardBottom = height - padding - 90f
    val cardLeft = padding + 30f
    val cardRight = width - padding - 30f
    val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

    paint.color = Color.argb(90, 0, 0, 0)
    paint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    canvas.drawRoundRect(RectF(cardLeft + 4f, cardTop + 8f, cardRight + 4f, cardBottom + 8f), 28f, 28f, paint)
    paint.maskFilter = null

    paint.color = Color.WHITE
    canvas.drawRoundRect(cardRect, 28f, 28f, paint)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 40, 40, 40)
        textSize = 30f
    }
    var textCursorY = cardTop + 50f
    val textWidth = (cardRect.width() - 60f).toInt()

    if (opciones.especies && reporte.peces.isNotEmpty()) {
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = 66f
        paint.color = colorTop
        canvas.drawText("${reporte.cantidadTotal} peces", width / 2f, textCursorY + 50f, paint)
        textCursorY += 100f

        val detalle = reporte.peces.joinToString(" · ") { "${it.cantidad} ${it.especie}" }
        textCursorY = dibujarTextoCentrado(canvas, detalle, textWidth, width / 2f, textCursorY, textPaint.apply {
            color = Color.argb(255, 70, 70, 70); textSize = 28f
        })

        if (reporte.cantidadDevuelta > 0) {
            textCursorY += 12f
            textCursorY = dibujarTextoCentrado(
                canvas, "🔄 ${reporte.cantidadDevuelta} devueltos al agua", textWidth, width / 2f, textCursorY,
                textPaint.apply { color = Color.argb(255, 15, 110, 86); textSize = 26f }
            )
        }
        textCursorY += 24f
    }

    if (opciones.ubicacion) {
        val lugar = reporte.ubicacion?.nombre ?: reporte.ubicacion?.zona
        if (!lugar.isNullOrBlank()) {
            textCursorY = dibujarTextoCentrado(
                canvas, "📍 $lugar", textWidth, width / 2f, textCursorY,
                textPaint.apply { color = Color.argb(255, 60, 60, 60); textSize = 28f }
            )
            textCursorY += 20f
        }
    }

    if (opciones.observaciones && !reporte.observaciones.isNullOrBlank()) {
        textCursorY = dibujarTextoCentrado(
            canvas, reporte.observaciones, textWidth, width / 2f, textCursorY,
            textPaint.apply { color = Color.argb(255, 90, 90, 90); textSize = 26f }
        )
    }

    paint.isFakeBoldText = false
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 24f
    paint.color = Color.argb(190, 255, 255, 255)
    canvas.drawText("huka · bitácora de pesca", width / 2f, height - padding - 35f, paint)

    return bitmap
}

private fun dibujarTextoCentrado(
    canvas: Canvas,
    texto: String,
    anchoMax: Int,
    centroX: Float,
    startY: Float,
    paint: TextPaint
): Float {
    paint.textAlign = Paint.Align.LEFT
    val layout = StaticLayout.Builder
        .obtain(texto, 0, texto.length, paint, anchoMax)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(4f, 1f)
        .build()

    canvas.save()
    canvas.translate(centroX - anchoMax / 2f, startY)
    layout.draw(canvas)
    canvas.restore()

    return startY + layout.height + 6f
}
