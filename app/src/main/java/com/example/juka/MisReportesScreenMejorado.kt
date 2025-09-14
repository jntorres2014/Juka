// MisReportesScreen.kt - VERSIÓN MEJORADA CON FILTROS Y ESTADÍSTICAS
package com.example.juka

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisReportesScreenMejorado(
    viewModel: ChatViewModel = viewModel()
) {
    var reportes by remember { mutableStateOf<List<PartePesca>>(emptyList()) }
    var reportesFiltrados by remember { mutableStateOf<List<PartePesca>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtroSeleccionado by remember { mutableStateOf("todos") }
    var busqueda by remember { mutableStateOf("") }
    var mostrarEstadisticas by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cargar reportes
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            try {
                reportes = viewModel.obtenerMisPartes()
                reportesFiltrados = reportes
                error = null
            } catch (e: Exception) {
                error = "Error cargando reportes: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Aplicar filtros
    LaunchedEffect(filtroSeleccionado, busqueda, reportes) {
        reportesFiltrados = reportes.filter { reporte ->
            val cumpleFiltro = when (filtroSeleccionado) {
                "embarcado" -> reporte.tipo == "embarcado"
                "costa" -> reporte.tipo == "costa"
                "exitosos" -> reporte.cantidadTotal > 0
                "recientes" -> {
                    val hace7Dias = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -7)
                    }.time
                    try {
                        val fechaReporte = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .parse(reporte.fecha)
                        fechaReporte?.after(hace7Dias) ?: false
                    } catch (e: Exception) { false }
                }
                else -> true
            }

            val cumpleBusqueda = if (busqueda.isBlank()) true else {
                reporte.peces.any { pez ->
                    pez.especie.contains(busqueda, ignoreCase = true)
                } || reporte.transcripcionOriginal?.contains(busqueda, ignoreCase = true) == true
            }

            cumpleFiltro && cumpleBusqueda
        }.sortedByDescending { it.fecha }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando tus reportes...")
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudOff,
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
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        reportes = viewModel.obtenerMisPartes()
                                        error = null
                                    } catch (e: Exception) {
                                        error = "Error: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎣", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No tienes reportes aún",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ve al Chat y registra tu primera jornada",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { /* Navegar al chat */ }
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ir al Chat")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header con estadísticas
                    item {
                        HeaderConEstadisticas(
                            reportes = reportes,
                            reportesFiltrados = reportesFiltrados,
                            onToggleEstadisticas = { mostrarEstadisticas = !mostrarEstadisticas }
                        )
                    }

                    // Estadísticas expandidas
                    if (mostrarEstadisticas) {
                        item {
                            EstadisticasDetalladas(reportes = reportes)
                        }
                    }

                    // Barra de búsqueda
                    item {
                        OutlinedTextField(
                            value = busqueda,
                            onValueChange = { busqueda = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar por especie o contenido...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (busqueda.isNotEmpty()) {
                                    IconButton(onClick = { busqueda = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                    }

                    // Filtros
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                listOf(
                                    "todos" to "🎣 Todos",
                                    "recientes" to "📅 Recientes",
                                    "exitosos" to "🏆 Exitosos",
                                    "embarcado" to "🚤 Embarcado",
                                    "costa" to "🏖️ Costa"
                                )
                            ) { (filtro, texto) ->
                                FilterChip(
                                    onClick = { filtroSeleccionado = filtro },
                                    label = { Text(texto) },
                                    selected = filtroSeleccionado == filtro
                                )
                            }
                        }
                    }

                    // Lista de reportes
                    if (reportesFiltrados.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No hay reportes que coincidan",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Prueba cambiar los filtros o búsqueda",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        items(reportesFiltrados) { reporte ->
                            ReporteCardMejorado(
                                reporte = reporte,
                                onCompartir = { /* Implementar compartir */ },
                                onVerDetalle = { /* Implementar detalle */ }
                            )
                        }
                    }

                    // Espaciador final
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderConEstadisticas(
    reportes: List<PartePesca>,
    reportesFiltrados: List<PartePesca>,
    onToggleEstadisticas: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🎣 Mis Reportes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Mostrando ${reportesFiltrados.size} de ${reportes.size}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                IconButton(onClick = onToggleEstadisticas) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = "Ver estadísticas",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Estadísticas rápidas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatQuickCard("📊", "${reportes.size}", "Jornadas")
                StatQuickCard("🐟", "${reportes.sumOf { it.cantidadTotal }}", "Peces")
                StatQuickCard("🏆", "${reportes.count { it.cantidadTotal > 0 }}", "Exitosas")
                StatQuickCard("🎯", "${reportes.flatMap { it.peces }.map { it.especie }.distinct().size}", "Especies")
            }
        }
    }
}

@Composable
fun StatQuickCard(icono: String, valor: String, etiqueta: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(text = icono, fontSize = 20.sp)
        Text(
            text = valor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = etiqueta,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun EstadisticasDetalladas(reportes: List<PartePesca>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "📈 Estadísticas Detalladas",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Especies más capturadas
            val especiesFrecuentes = reportes
                .flatMap { it.peces }
                .groupBy { it.especie }
                .mapValues { it.value.sumOf { pez -> pez.cantidad } }
                .toList()
                .sortedByDescending { it.second }
                .take(3)

            if (especiesFrecuentes.isNotEmpty()) {
                Text(
                    text = "🥇 Especies más capturadas:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                especiesFrecuentes.forEachIndexed { index, (especie, cantidad) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${index + 1}. $especie")
                        Text("$cantidad peces", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Promedio por tipo
            val promedioEmbarcado = reportes.filter { it.tipo == "embarcado" }.takeIf { it.isNotEmpty() }?.let { lista ->
                lista.sumOf { it.cantidadTotal }.toDouble() / lista.size
            }
            val promedioCosta = reportes.filter { it.tipo == "costa" }.takeIf { it.isNotEmpty() }?.let { lista ->
                lista.sumOf { it.cantidadTotal }.toDouble() / lista.size
            }

            Text("📊 Promedio de capturas:")
            Spacer(modifier = Modifier.height(8.dp))
            promedioEmbarcado?.let {
                Text("🚤 Embarcado: ${String.format("%.1f", it)} peces/jornada")
            }
            promedioCosta?.let {
                Text("🏖️ Costa: ${String.format("%.1f", it)} peces/jornada")
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReporteCardMejorado(
    reporte: PartePesca,
    onCompartir: (PartePesca) -> Unit,
    onVerDetalle: (PartePesca) -> Unit
) {
    Card(
        onClick = { onVerDetalle(reporte) },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${reporte.horaInicio ?: "?"} - ${reporte.horaFin ?: "?"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Badge del resultado
                Surface(
                    color = when {
                        reporte.cantidadTotal == 0 -> Color(0xFFFF5722).copy(alpha = 0.15f)
                        reporte.cantidadTotal <= 2 -> Color(0xFFFF9800).copy(alpha = 0.15f)
                        reporte.cantidadTotal <= 5 -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else -> Color(0xFF2196F3).copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            reporte.cantidadTotal == 0 -> "Sin capturas"
                            reporte.cantidadTotal <= 2 -> "Tranquila"
                            reporte.cantidadTotal <= 5 -> "Buena"
                            else -> "Excelente"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            reporte.cantidadTotal == 0 -> Color(0xFFFF5722)
                            reporte.cantidadTotal <= 2 -> Color(0xFFFF9800)
                            reporte.cantidadTotal <= 5 -> Color(0xFF4CAF50)
                            else -> Color(0xFF2196F3)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // NUEVA SECCIÓN: Mostrar fotos si existen
            if (reporte.fotos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(reporte.fotos) { fotoUri ->
                        Card(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            coil.compose.AsyncImage(
                                model = fotoUri,
                                contentDescription = "Foto de pesca",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = android.R.drawable.ic_menu_gallery),
                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery)
                            )
                        }
                    }

                    // Indicador de cantidad de fotos si hay más de una
                    if (reporte.fotos.size > 1) {
                        item {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "+${reporte.fotos.size - 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Información principal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (reporte.tipo == "embarcado") Icons.Default.DirectionsBoat else Icons.Default.Landscape,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reporte.tipo?.replaceFirstChar { it.uppercase() } ?: "?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Pets,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${reporte.cantidadTotal} peces",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Indicador visual de foto
                    if (reporte.fotos.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Con foto",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Especies (si hay capturas)
            if (reporte.peces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reporte.peces.joinToString(" • ") { "${it.especie}: ${it.cantidad}" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mostrar transcripción original si existe (útil para debug)
                if (!reporte.transcripcionOriginal.isNullOrBlank() && reporte.transcripcionOriginal!!.length > 10) {
                    Text(
                        text = "\"${reporte.transcripcionOriginal!!.take(30)}...\"",
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                TextButton(onClick = { onCompartir(reporte) }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir")
                }
            }
        }
    }
}