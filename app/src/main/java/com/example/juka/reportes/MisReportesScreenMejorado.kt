package com.example.juka.reportes

import android.content.Context
import android.content.Intent
import android.net.Uri

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juka.R
import com.example.juka.data.firebase.PartePesca
import com.example.juka.viewmodel.AppViewModelProvider
import com.example.juka.viewmodel.ReportesViewModel

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.juka.domain.model.ModalidadPesca

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisReportesScreenMejorado(
    viewModel: ReportesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var fotoParaExpandir by remember { mutableStateOf<String?>(null) }
    var filtroSeleccionado by remember { mutableStateOf("todos") }
    var busqueda by remember { mutableStateOf("") }
    var mostrarEstadisticas by remember { mutableStateOf(false) }
    var selectedReporte by remember { mutableStateOf<PartePesca?>(null) }
    var mostrarMapaGeneral by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current

    val reportes = uiState.reportes
    val isLoading = uiState.isLoading
    val error = uiState.error

    val reportesFiltrados by remember(busqueda, filtroSeleccionado, reportes) {
        derivedStateOf {
            reportes.filter { reporte ->
                val cumpleFiltro = when (filtroSeleccionado) {
                    "embarcado" -> reporte.tipo == "con línea embarcación"
                    "costa"     -> reporte.tipo == "con línea costa"
                    "otros"     -> reporte.tipo !in listOf("con línea embarcación", "con línea costa")
                    "exitosos"  -> reporte.cantidadTotal > 0
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
            }
        }
    }

    val totalPeces       by remember(reportes) { derivedStateOf { reportes.sumOf { it.cantidadTotal } } }
    val exitosas         by remember(reportes) { derivedStateOf { reportes.count { it.cantidadTotal > 0 } } }
    val especiesUnicas   by remember(reportes) { derivedStateOf { reportes.flatMap { it.peces }.map { it.especie }.distinct().size } }

    val especiesFrecuentes by remember(reportes) {
        derivedStateOf {
            reportes.flatMap { it.peces }
                .groupBy { it.especie }
                .mapValues { it.value.sumOf { pez -> pez.cantidad } }
                .toList().sortedByDescending { it.second }.take(3)
        }
    }

    val promedioEmbarcado by remember(reportes) {
        derivedStateOf {
            val filtrados = reportes.filter { reporte ->
                val tipoBD = reporte.tipo ?: ""
                tipoBD.equals(ModalidadPesca.CON_LINEA_EMBARCACION.toString(), ignoreCase = true) ||
                        tipoBD.equals(ModalidadPesca.CON_LINEA_EMBARCACION.displayName, ignoreCase = true)
            }
            if (filtrados.isNotEmpty()) filtrados.sumOf { it.cantidadTotal }.toDouble() / filtrados.size
            else null
        }
    }

    val promedioCosta by remember(reportes) {
        derivedStateOf {
            val filtrados = reportes.filter { reporte ->
                val tipoBD = reporte.tipo ?: ""
                tipoBD.equals(ModalidadPesca.CON_LINEA_COSTA.toString(), ignoreCase = true) ||
                        tipoBD.equals(ModalidadPesca.CON_LINEA_COSTA.displayName, ignoreCase = true)
            }
            if (filtrados.isNotEmpty()) filtrados.sumOf { it.cantidadTotal }.toDouble() / filtrados.size
            else null
        }
    }

    if (mostrarMapaGeneral) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapaGeneralDeReportes(
                reportes = reportes,
                onCerrar = { mostrarMapaGeneral = false }
            )
        }
        return
    }

    if (selectedReporte != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedReporte = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            DetalleParteBottomSheet(
                reporte = selectedReporte!!,
                onDismiss = { selectedReporte = null },
                onVerFotoFull = { path -> fotoParaExpandir = path }
            )
        }
    }

    if (fotoParaExpandir != null) {
        Dialog(
            onDismissRequest = { fotoParaExpandir = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fotoParaExpandir = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = if (fotoParaExpandir!!.startsWith("http")) fotoParaExpandir else File(fotoParaExpandir!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fotoParaExpandir = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }

    // CONTENIDO PRINCIPAL
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando tus reportes...")
                    }
                }
            }

            error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = error ?: "Error desconocido", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.cargarReportes() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar")
                        }
                    }
                }
            }

            reportes.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎣", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "No tienes reportes aún", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Ve al Chat y registra tu primera jornada", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(24.dp))
                        // ✅ Botón refresh también en estado vacío
                        OutlinedButton(onClick = { viewModel.cargarReportes() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Actualizar")
                        }
                    }
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 88.dp
                        ), // ✅ espacio para el FAB
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            // ✅ onRefresh agregado
                            HeaderConEstadisticas(
                                totalPeces = totalPeces,
                                exitosas = exitosas,
                                especiesUnicas = especiesUnicas,
                                reportesFiltrados = reportesFiltrados.size,
                                reportesTotal = reportes.size,
                                isRefreshing = isLoading,
                                onToggleEstadisticas = {
                                    mostrarEstadisticas = !mostrarEstadisticas
                                },
                                onRefresh = { viewModel.cargarReportes() }
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
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null
                                    )
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
                                        "costa" to "🏖️ Costa",
                                        "otros" to "🌍 Otros"
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
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                        Text(
                                            "No hay reportes que coincidan",
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                reportesFiltrados,
                                key = { it.id ?: it.fecha.toString() }) { reporte ->
                                ReporteCardMejorado(
                                    reporte = reporte,
                                    onCompartir = { },
                                    onVerDetalle = { selectedReporte = reporte }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }

                    }
                    FloatingActionButton(
                        onClick = { mostrarMapaGeneral = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Ver mapa general",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

// ================== COMPONENTES AUXILIARES ==================

@Composable
fun HeaderConEstadisticas(
    totalPeces: Int,
    exitosas: Int,
    especiesUnicas: Int,
    reportesFiltrados: Int,
    reportesTotal: Int,
    isRefreshing: Boolean,        // ✅ NUEVO
    onToggleEstadisticas: () -> Unit,
    onRefresh: () -> Unit          // ✅ NUEVO
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "🎣 Mis Reportes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Mostrando $reportesFiltrados de $reportesTotal",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                // ✅ Botones en fila: Refresh + Estadísticas
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Actualizar reportes",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    IconButton(onClick = onToggleEstadisticas) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = "Estadísticas",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatQuickCard("📊", "$reportesTotal", "Jornadas")
                StatQuickCard("🐟", "$totalPeces",    "Peces")
                StatQuickCard("🏆", "$exitosas",      "Exitosas")
                StatQuickCard("🎯", "$especiesUnicas","Especies")
            }
        }
    }
}

@Composable
fun StatQuickCard(icono: String, valor: String, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text(text = icono, fontSize = 20.sp)
        Text(text = valor, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text = etiqueta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
    }
}

@Composable
fun EstadisticasDetalladas(
    especiesFrecuentes: List<Pair<String, Int>>,
    promedioEmbarcado: Double?,
    promedioCosta: Double?
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("📈 Estadísticas Detalladas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            if (especiesFrecuentes.isNotEmpty()) {
                Text("🥇 Especies más capturadas:", fontWeight = FontWeight.Medium)
                especiesFrecuentes.forEachIndexed { index, (especie, cantidad) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. $especie")
                        Text("$cantidad peces", fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("📊 Promedio de capturas:")
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🚤 Embarcado", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = promedioEmbarcado?.let { String.format("%.1f", it) } ?: "---",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (promedioEmbarcado != null) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🏖️ Costa", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = promedioCosta?.let { String.format("%.1f", it) } ?: "---",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (promedioCosta != null) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(reporte.fecha.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${reporte.horaInicio ?: "?"} - ${reporte.horaFin ?: "?"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Surface(
                    color = when {
                        reporte.cantidadTotal == 0 -> Color(0xFFFF5722).copy(alpha = 0.15f)
                        reporte.cantidadTotal <= 5 -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else -> Color(0xFF2196F3).copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (reporte.cantidadTotal == 0) "Sin capturas" else "Exitoso",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (reporte.cantidadTotal == 0) Color(0xFFFF5722) else Color(0xFF2196F3)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (reporte.fotos.isNotEmpty()) {
                val fotoPath = reporte.fotos.first()
                Card(modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(8.dp)) {
                    AsyncImage(
                        model = if (fotoPath.startsWith("http")) fotoPath else File(fotoPath),
                        contentDescription = "Foto captura",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.ic_launcher_background),
                        error = painterResource(id = R.drawable.ic_launcher_background)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🐠", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("${reporte.cantidadTotal} peces ", fontWeight = FontWeight.Medium)
                Text(
                    text = reporte.peces.joinToString(separator = " - ") { "${it.cantidad} ${it.especie}" },
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetalleParteBottomSheet(
    reporte: PartePesca,
    onDismiss: () -> Unit,
    onVerFotoFull: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(40.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Resumen de Jornada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        reporte.fotos.firstOrNull()?.let { fotoPath ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onVerFotoFull(fotoPath) }
            ) {
                AsyncImage(
                    model = if (fotoPath.startsWith("http")) fotoPath else File(fotoPath),
                    contentDescription = "Foto de la captura",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Fullscreen, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("VER ENTERA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            InfoSectionMejorada(icon = Icons.Default.CalendarToday, titulo = "Fecha y Horario", contenido = "${reporte.fecha} • ${reporte.horaInicio ?: "--:--"} a ${reporte.horaFin ?: "--:--"}")
            InfoSectionMejorada(icon = Icons.Default.Phishing, titulo = "Modalidad", contenido = reporte.tipo?.replaceFirstChar { it.uppercase() } ?: "No especificada")

            Spacer(modifier = Modifier.height(16.dp))
            Text("Especies Capturadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            if (reporte.peces.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reporte.peces.forEach { pez ->
                        AssistChip(
                            onClick = { },
                            label = { Text("${pez.cantidad}x ${pez.especie}") },
                            leadingIcon = { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                            shape = RoundedCornerShape(12.dp),
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        )
                    }
                }
            } else {
                Text("No se registraron capturas en este reporte.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            if (!reporte.observaciones.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                InfoSectionMejorada(icon = Icons.Default.Notes, titulo = "Notas del pescador", contenido = reporte.observaciones)
            }

            Spacer(modifier = Modifier.height(20.dp))
            UbicacionSection(reporte)
        }
    }
}

@Composable
fun InfoSectionMejorada(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    contenido: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(contenido, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun InfoSection(titulo: String, contenido: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(contenido, fontSize = 16.sp)
        }
    }
}

@Composable
fun UbicacionSection(reporte: PartePesca) {
    UbicacionViewer(
        lat = reporte.ubicacion?.latitud,
        lng = reporte.ubicacion?.longitud,
        ubicacionName = reporte.ubicacion?.nombre,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun UbicacionViewer(lat: Double?, lng: Double?, ubicacionName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (lat == null || lng == null) return

    val geoPoint = remember { GeoPoint(lat, lng) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("📍 Ubicación", fontWeight = FontWeight.Medium)
                TextButton(onClick = {
                    val gmmIntentUri = "geo:$lat,$lng"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gmmIntentUri)))
                }) { Text("Abrir") }
            }
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(8.0)
                        controller.setCenter(geoPoint)
                        val marker = Marker(this)
                        marker.position = geoPoint
                        marker.title = ubicacionName ?: "Spot"
                        overlays.add(marker)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}

@Composable
fun MapaGeneralDeReportes(reportes: List<PartePesca>, onCerrar: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    val validos = reportes.filter { it.ubicacion?.latitud != null }
                    if (validos.isNotEmpty()) {
                        controller.setZoom(6.5)
                        controller.setCenter(GeoPoint(validos[0].ubicacion!!.latitud!!, validos[0].ubicacion!!.longitud!!))
                        validos.forEach { r ->
                            val m = Marker(this)
                            m.position = GeoPoint(r.ubicacion!!.latitud!!, r.ubicacion!!.longitud!!)
                            m.title = r.fecha
                            m.snippet = "Capturas: ${r.cantidadTotal}"
                            overlays.add(m)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        FloatingActionButton(
            onClick = onCerrar,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
    }
}