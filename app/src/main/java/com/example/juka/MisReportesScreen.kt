package com.example.juka

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// Función para formatear fecha (fuera de cualquier composable)
fun formatearFecha(fecha: String): String {
    return try {
        val partes = fecha.split("-")
        if (partes.size == 3) {
            val meses = arrayOf(
                "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
            )
            val dia = partes[2].toInt()
            val mes = partes[1].toInt()
            val año = partes[0].toInt()

            "$dia de ${meses.getOrNull(mes) ?: mes} $año"
        } else {
            fecha
        }
    } catch (e: Exception) {
        fecha
    }
}

// Función para generar imagen compartible del reporte (fuera de cualquier composable)
fun generarImagenReporte(reporte: PartePesca, context: android.content.Context): Bitmap {
    val width = 800
    val height = 1000

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Fondo degradado
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Fondo azul marino
    paint.color = Color(0xFF1E3A8A).toArgb()
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // Fondo decorativo (ondas)
    paint.color = Color(0xFF3B82F6).copy(alpha = 0.3f).toArgb()
    for (i in 0..5) {
        canvas.drawCircle(
            width * 0.8f,
            height * (0.1f + i * 0.15f),
            (100 + i * 20).toFloat(),
            paint
        )
    }

    // Texto del reporte (simplificado)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 48f
    paint.textAlign = Paint.Align.CENTER

    canvas.drawText("JORNADA DE PESCA", width / 2f, 120f, paint)

    paint.textSize = 36f
    canvas.drawText(formatearFecha(reporte.fecha), width / 2f, 200f, paint)

    paint.textSize = 32f
    canvas.drawText("${reporte.horaInicio ?: "?"} - ${reporte.horaFin ?: "?"}", width / 2f, 260f, paint)

    // Estadísticas principales
    paint.textSize = 72f
    canvas.drawText("${reporte.cantidadTotal}", width / 2f, 400f, paint)

    paint.textSize = 28f
    canvas.drawText("PECES CAPTURADOS", width / 2f, 450f, paint)

    paint.textSize = 32f
    canvas.drawText("Tipo: ${reporte.tipo?.replaceFirstChar { it.uppercase() } ?: "?"}", width / 2f, 520f, paint)
    canvas.drawText("Cañas: ${reporte.canas ?: 0}", width / 2f, 570f, paint)

    // Especies
    if (reporte.peces.isNotEmpty()) {
        paint.textSize = 24f
        canvas.drawText("ESPECIES:", width / 2f, 650f, paint)

        var yPos = 690f
        reporte.peces.forEach { pez ->
            canvas.drawText("${pez.especie}: ${pez.cantidad}", width / 2f, yPos, paint)
            yPos += 35f
        }
    }

    // Footer
    paint.textSize = 20f
    paint.color = Color(0xFF94A3B8).toArgb()
    canvas.drawText("Registrado con Juka - App de Pesca", width / 2f, height - 80f, paint)

    return bitmap
}

// Composable para las tarjetas de estadísticas
@Composable
fun StatMiniCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    valor: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = valor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = titulo,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// Composable para cada tarjeta de reporte
@Composable
fun ReporteCard(
    reporte: PartePesca,
    onCompartir: (PartePesca) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header del reporte
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatearFecha(reporte.fecha),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "${reporte.horaInicio ?: "?"} - ${reporte.horaFin ?: "?"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Badge del tipo
                Surface(
                    color = if (reporte.tipo == "embarcado")
                        Color(0xFF2196F3).copy(alpha = 0.15f)
                    else
                        Color(0xFF4CAF50).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (reporte.tipo == "embarcado") Icons.Default.DirectionsBoat else Icons.Default.Landscape,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (reporte.tipo == "embarcado") Color(0xFF2196F3) else Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = reporte.tipo?.replaceFirstChar { it.uppercase() } ?: "?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (reporte.tipo == "embarcado") Color(0xFF2196F3) else Color(0xFF4CAF50)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Estadísticas principales en tarjetas
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total de peces
                StatMiniCard(
                    icon = Icons.Default.Pets,
                    titulo = "Peces",
                    valor = "${reporte.cantidadTotal}",
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f)
                )

                // Cañas
                StatMiniCard(
                    icon = Icons.Default.SportsHockey,
                    titulo = "Cañas",
                    valor = "${reporte.canas ?: 0}",
                    color = Color(0xFF8E44AD),
                    modifier = Modifier.weight(1f)
                )

                // Duración
                StatMiniCard(
                    icon = Icons.Default.Schedule,
                    titulo = "Tiempo",
                    valor = reporte.duracionHoras ?: "?",
                    color = Color(0xFF3498DB),
                    modifier = Modifier.weight(1f)
                )
            }

            // Especies capturadas
            if (reporte.peces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Especies capturadas:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reporte.peces.forEach { pez ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pez.especie}: ${pez.cantidad}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón de compartir
            Button(
                onClick = { onCompartir(reporte) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Compartir en Redes Sociales",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Composable principal de la pantalla
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisReportesScreen(
    viewModel: ChatViewModel = viewModel()
) {
    var reportes by remember { mutableStateOf<List<PartePesca>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reporteSeleccionado by remember { mutableStateOf<PartePesca?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cargar reportes al iniciar
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            try {
                reportes = viewModel.obtenerMisPartes()
                error = null
            } catch (e: Exception) {
                error = "Error cargando reportes: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Función para refrescar
    fun refrescar() {
        scope.launch {
            isLoading = true
            try {
                reportes = viewModel.obtenerMisPartes()
                error = null
            } catch (e: Exception) {
                error = "Error cargando reportes: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Surface(
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mis Reportes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (reportes.isNotEmpty()) {
                        Text(
                            text = "${reportes.size} jornadas registradas",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = { refrescar() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refrescar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Contenido principal
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando reportes...")
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { refrescar() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            reportes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No tienes reportes aún",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ve al Chat y crea tu primer reporte de pesca",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(reportes) { reporte ->
                        ReporteCard(
                            reporte = reporte,
                            onCompartir = {
                                // Generar imagen y compartir
                                reporteSeleccionado = it
                            }
                        )
                    }

                    // Espaciador al final
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}