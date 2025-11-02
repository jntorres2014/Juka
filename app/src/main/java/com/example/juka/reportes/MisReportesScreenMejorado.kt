// MisReportesScreen.kt - VERSIÓN ORIGINAL CON FIX SOLO PARA EL MAPA (ANTI-LAG)
package com.example.juka.reportes

import android.content.Intent
import android.net.Uri
import android.R
import android.content.Context
import android.util.Log

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.viewmodel.ChatViewModel
import com.example.juka.data.firebase.PartePesca
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisReportesScreenMejorado(
    viewModel: ChatViewModel = viewModel()
) {
    var reportes by remember { mutableStateOf<List<PartePesca>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtroSeleccionado by remember { mutableStateOf("todos") }
    var busqueda by remember { mutableStateOf("") }
    var mostrarEstadisticas by remember { mutableStateOf(false) }
    var selectedReporte by remember { mutableStateOf<PartePesca?>(null) }
    var mostrarMapaGeneral by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // OPTIMIZACIÓN: DerivedState para filtrado lazy
    val reportesFiltrados by remember(busqueda, filtroSeleccionado, reportes) {
        derivedStateOf {
            reportes.filter { reporte ->
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
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cargar reportes
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            try {
                val firebaseManager = FirebaseManager(context)
                reportes = firebaseManager.obtenerMisPartes()
                error = null
            } catch (e: Exception) {
                error = "Error cargando reportes: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // OPTIMIZACIÓN: Stats lazy
    val totalPeces by remember(reportes) {
        derivedStateOf { reportes.sumOf { it.cantidadTotal } }
    }
    val exitosas by remember(reportes) {
        derivedStateOf { reportes.count { it.cantidadTotal > 0 } }
    }
    val especiesUnicas by remember(reportes) {
        derivedStateOf {
            reportes.flatMap { it.peces }.map { it.especie }.distinct().size
        }
    }

    val especiesFrecuentes by remember(reportes) {
        derivedStateOf {
            reportes
                .flatMap { it.peces }
                .groupBy { it.especie }
                .mapValues { it.value.sumOf { pez -> pez.cantidad } }
                .toList()
                .sortedByDescending { it.second }
                .take(3)
        }
    }

    val promedioEmbarcado by remember(reportes) {
        derivedStateOf {
            reportes.filter { it.tipo == "embarcado" }.takeIf { it.isNotEmpty() }?.let { lista ->
                lista.sumOf { it.cantidadTotal }.toDouble() / lista.size
            }
        }
    }
    val promedioCosta by remember(reportes) {
        derivedStateOf {
            reportes.filter { it.tipo == "costa" }.takeIf { it.isNotEmpty() }?.let { lista ->
                lista.sumOf { it.cantidadTotal }.toDouble() / lista.size
            }
        }
    }

    // OPTIMIZACIÓN: ListState para smooth scroll
    val listState = rememberLazyListState()

    // FIX: Mapa fullscreen condicional (movido AL PRINCIPIO, antes del Column)
    if (mostrarMapaGeneral) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapaGeneralDeReportes(
                reportes = reportes,
                onCerrar = { mostrarMapaGeneral = false }
            )
        }
        return  // Salir temprano para no renderizar el resto
    }

    // Bottom Sheet para detalles
    if (selectedReporte != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedReporte = null },
            sheetState = bottomSheetState
        ) {
            selectedReporte?.let { reporte ->
                DetalleParteBottomSheet(
                    reporte = reporte,
                    onDismiss = { selectedReporte = null },
                    onCompartir = { /* Implementar compartir */ },
                    onEditar = { /* Navega al chat con este parte ID */ }
                )
            }
        }
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
                                        val firebaseManager = FirebaseManager(context)
                                        reportes = firebaseManager.obtenerMisPartes()
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
                if (!isLoading && error == null && !mostrarMapaGeneral) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Button(
                            onClick = { mostrarMapaGeneral = true },
                            modifier = Modifier
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = "Ver mapa")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver mapa general")
                        }
                    }
                }

                LazyColumn(
                    state = listState,  // OPTIMIZACIÓN: Smooth scroll
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HeaderConEstadisticas(
                            totalPeces = totalPeces,
                            exitosas = exitosas,
                            especiesUnicas = especiesUnicas,
                            reportesFiltrados = reportesFiltrados.size,
                            reportesTotal = reportes.size,
                            onToggleEstadisticas = { mostrarEstadisticas = !mostrarEstadisticas }
                        )
                    }

                    if (mostrarEstadisticas) {
                        item {
                            EstadisticasDetalladas(
                                especiesFrecuentes = especiesFrecuentes,
                                promedioEmbarcado = promedioEmbarcado,
                                promedioCosta = promedioCosta
                            )
                        }
                    }

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
                        items(
                            reportesFiltrados,
                            key = { it.id ?: it.fecha }  // OPTIMIZACIÓN: Stable keys
                        ) { reporte ->
                            ReporteCardMejorado(
                                reporte = reporte,
                                onCompartir = { /* Implementar */ },
                                onVerDetalle = { selectedReporte = reporte }
                            )
                        }
                    }

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
    totalPeces: Int,
    exitosas: Int,
    especiesUnicas: Int,
    reportesFiltrados: Int,
    reportesTotal: Int,
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
                        text = "Mostrando $reportesFiltrados de $reportesTotal",
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

            // Estadísticas rápidas OPTIMIZADAS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatQuickCard("📊", "$reportesTotal", "Jornadas")
                StatQuickCard("🐟", "$totalPeces", "Peces")
                StatQuickCard("🏆", "$exitosas", "Exitosas")
                StatQuickCard("🎯", "$especiesUnicas", "Especies")
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
fun EstadisticasDetalladas(
    especiesFrecuentes: List<Pair<String, Int>>,
    promedioEmbarcado: Double?,
    promedioCosta: Double?
) {
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

            // Foto única si existe OPTIMIZADA
            if (reporte.fotos.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    AsyncImage(
                        model = reporte.fotos.first(),
                        contentDescription = "Foto de pesca",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {  // OPTIMIZACIÓN: Scale para menos memoria
                                scaleX = 0.8f
                                scaleY = 0.8f
                            },
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.ic_menu_gallery),
                        placeholder = painterResource(id = R.drawable.ic_menu_gallery)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
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
                    Text(
                        text = "🐠",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                // Mostrar transcripción original si existe
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


// Bottom Sheet para detalles del parte
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleParteBottomSheet(
    reporte: PartePesca,
    onDismiss: () -> Unit,
    onCompartir: () -> Unit,
    onEditar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header con título y botón cerrar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Detalles de la jornada",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Foto única (full width) OPTIMIZADA
        reporte.fotos.firstOrNull()?.let { fotoUri ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = fotoUri,
                    contentDescription = "Foto de la jornada del ${formatearFecha(reporte.fecha)}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .graphicsLayer {
                            scaleX = 0.9f  // Ligero scale para perf
                            scaleY = 0.9f
                        },
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        } ?: Text("Sin foto", color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Detalles en cards
        InfoSection("📅 Fecha y Horarios", "${formatearFecha(reporte.fecha)} • ${reporte.horaInicio} - ${reporte.horaFin}")
        InfoSection("🎣 Tipo", reporte.tipo?.replaceFirstChar { it.uppercase() } ?: "No especificado")
        InfoSection("🐟 Capturas", "${reporte.cantidadTotal} peces: ${reporte.peces.joinToString(" • ") { "${it.especie} x${it.cantidad}" }}")

        // Observaciones o transcripción
        if (!reporte.transcripcionOriginal.isNullOrBlank()) {
            InfoSection("📝 Notas", reporte.transcripcionOriginal!!)
        }

        // Ubicación con visor de mapa
        UbicacionSection(reporte)

        Spacer(modifier = Modifier.height(16.dp))

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onEditar,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Editar en Chat")
            }
            Button(
                onClick = onCompartir,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compartir")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun InfoSection(titulo: String, contenido: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(contenido, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// Visor de ubicación basado en osmdroid
@Composable
fun UbicacionSection(reporte: PartePesca) {
    UbicacionViewer(
        lat = reporte.ubicacion?.latitud,
        lng = reporte?.ubicacion?.longitud,
        ubicacionName = reporte.ubicacion?.nombre,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun UbicacionViewer(
    lat: Double?,
    lng: Double?,
    ubicacionName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Configurar osmdroid
    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    if (lat == null || lng == null) {
        // Fallback si no hay coords
        Card(
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📍 Ubicación no disponible", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (!ubicacionName.isNullOrBlank()) {
                    Text("Descripción: $ubicacionName", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val gmmIntentUri = "geo:0,0?q=${Uri.encode(ubicacionName ?: "pesca")}"
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gmmIntentUri))
                        context.startActivity(mapIntent)
                    }
                ) {
                    Text("Abrir en Maps")
                }
            }
        }
        return
    }

    val geoPoint = remember { GeoPoint(lat, lng) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📍 Spot de pesca",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = {
                        val gmmIntentUri = "geo:$lat,$lng"
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gmmIntentUri))
                        context.startActivity(mapIntent)
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Abrir en app")
                }
            }

            // Mapa read-only
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(geoPoint)

                        // Agregar marcador
                        val marker = Marker(this)
                        marker.position = geoPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = ubicacionName ?: "Tu spot"
                        marker.snippet = "Aquí pescaste 🐟"  // Personalizá si querés
                        overlays.add(marker)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
fun MapaGeneralDeReportes(
    reportes: List<PartePesca>,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                org.osmdroid.config.Configuration.getInstance().load(
                    ctx,
                    ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                )

                MapView(ctx).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)

                    val mapController = controller
                    val puntosValidos = reportes.filter {
                        it.ubicacion?.latitud != null && it.ubicacion?.longitud != null
                    }

                    if (puntosValidos.isNotEmpty()) {
                        val startPoint = GeoPoint(
                            puntosValidos.first().ubicacion?.latitud!!,
                            puntosValidos.first().ubicacion?.longitud!!
                        )
                        mapController.setZoom(6.5)

                        mapController.setCenter(startPoint)

                        // Agregar marcadores
                        puntosValidos.forEach { parte ->
                            Log.d("MapaGeneralDeReportes", "parte: ${parte.peces}")  // Mantengo tu log para debug
                            val marker = Marker(this)
                            marker.position = GeoPoint(parte.ubicacion?.latitud!!, parte.ubicacion?.longitud!!)
                            marker.title = formatearFecha(parte.fecha)

                            // FIX: Formato bonito para el snippet
                            val capturasStr = if (parte.peces.isNotEmpty()) {
                                parte.peces.joinToString("\n") { "• \uD83D\uDC1F ${it.cantidad} ${it.especie}" }
                            } else {
                                "Sin capturas"
                            }

                            marker.snippet = "Capturas: $capturasStr" + "\n" + "Notas: ${parte.observaciones ?: "Sin notas"}"
                            marker.snippet = marker.snippet.replace("\n", "<br>")

                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            overlays.add(marker)
                        }

                        invalidate()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón para cerrar el mapa
        FloatingActionButton(
            onClick = onCerrar,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar mapa")
        }
    }
}