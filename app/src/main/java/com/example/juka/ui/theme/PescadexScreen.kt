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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
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
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import com.example.juka.EspecieConEstado
import com.example.juka.EspecieDescubierta
import com.example.juka.EstadisticasPescadex
import com.example.juka.PescadexManager
import com.example.juka.RegistroResult
import kotlinx.coroutines.launch

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
                        onClick = { especieSeleccionada = especieConEstado }
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
            }
        )
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
    onClick: () -> Unit
) {
    val especie = especieConEstado.info
    val esCapturada = especieConEstado.esCapturada
    val datosCaptura = especieConEstado.datosCaptura

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
    onActualizarRecord: (especieId: String, peso: Double?, fotoLocalPath: String?) -> Unit
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
                        onEditar = { editorAbierto = true }
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
    onEditar: () -> Unit
) {
    val tieneAlgo = datosCaptura.primeraFoto != null || datosCaptura.pesoRecord != null

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
                TextButton(onClick = onEditar) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (tieneAlgo) "Editar" else "Agregar")
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
