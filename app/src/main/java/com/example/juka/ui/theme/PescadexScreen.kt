// PescadexScreen.kt - Pantalla integrada con tu diseño
package com.example.juka.ui.theme

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.EspecieConEstado
import com.example.juka.EstadisticasPescadex
import com.example.juka.PescadexManager
import com.example.juka.RegistroResult
import com.example.juka.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PescadexScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pescadexManager = remember { PescadexManager(context) }

    var especies by remember { mutableStateOf<List<EspecieConEstado>>(emptyList()) }
    var estadisticas by remember { mutableStateOf<EstadisticasPescadex?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var especieSeleccionada by remember { mutableStateOf<EspecieConEstado?>(null) }
    var mostrarCelebracion by remember { mutableStateOf<RegistroResult.Success?>(null) }

    // Cargar datos al iniciar
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                especies = pescadexManager.obtenerEspeciesConEstado()
                estadisticas = pescadexManager.obtenerEstadisticasPescadex()
            } catch (e: Exception) {
                Log.e("PESCADEX", "Error cargando: ${e.message}")
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

            // Grid de especies
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(especies) { especieConEstado ->
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
            onRegistrarCaptura = { especieId, peso ->
                scope.launch {
                    val resultado = pescadexManager.registrarEspecieCapturada(
                        especieId = especieId,
                        peso = peso
                    )

                    when (resultado) {
                        is RegistroResult.Success -> {
                            if (resultado.esNuevaEspecie) {
                                mostrarCelebracion = resultado
                            }
                            // Recargar datos
                            especies = pescadexManager.obtenerEspeciesConEstado()
                            estadisticas = pescadexManager.obtenerEstadisticasPescadex()
                        }
                        is RegistroResult.Error -> {
                            // Mostrar error (podrías usar SnackBar)
                            Log.e("PESCADEX", "Error: ${resultado.mensaje}")
                        }
                    }
                    especieSeleccionada = null
                }
            }
        )
    }

    // Celebración de nueva especie
    mostrarCelebracion?.let { resultado ->
        CelebracionNuevaEspecieModal(
            resultado = resultado,
            onDismiss = { mostrarCelebracion = null }
        )
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

            // Estadísticas en fila
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMiniCard(
                    icon = "🎣",
                    valor = estadisticas.progreso,
                    etiqueta = "Especies"
                )
                StatMiniCard(
                    icon = "📈",
                    valor = "${estadisticas.totalCapturas}",
                    etiqueta = "Capturas"
                )
                StatMiniCard(
                    icon = "🏆",
                    valor = "${estadisticas.logrosDesbloqueados}",
                    etiqueta = "Logros"
                )
                StatMiniCard(
                    icon = "📅",
                    valor = "${estadisticas.diasPescando}",
                    etiqueta = "Días"
                )
            }
        }
    }
}

@Composable
fun StatMiniCard(
    icon: String,
    valor: String,
    etiqueta: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 24.sp
        )
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
            .aspectRatio(0.75f),
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
            // Imagen/Icono del pez
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
                if (esCapturada) {
                    Text(
                        text = obtenerEmojiPez(especie.id),
                        fontSize = 40.sp
                    )
                } else {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nombre del pez
            Text(
                text = if (esCapturada) especie.nombreComun else "???",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (esCapturada)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // Badge de rareza
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (datosCaptura.pesoRecord != null) {
                        Text(
                            text = "💪 ${datosCaptura.pesoRecord}kg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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

            // Estado de captura
            if (esCapturada) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
    onRegistrarCaptura: (String, Double?) -> Unit
) {
    val especie = especieConEstado.info
    val esCapturada = especieConEstado.esCapturada
    val datosCaptura = especieConEstado.datosCaptura

    var pesoIngresado by remember { mutableStateOf("") }

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
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = obtenerEmojiPez(especie.id),
                        fontSize = 32.sp
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                // Nombre y científico
                Text(
                    text = especie.nombreComun,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = obtenerColorRareza(especie.rareza)
                )

                Text(
                    text = especie.nombreCientifico,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Badge de rareza
                Surface(
                    color = obtenerColorRareza(especie.rareza).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "${obtenerTextoRareza(especie.rareza)} • ${especie.region}",
                        style = MaterialTheme.typography.labelMedium,
                        color = obtenerColorRareza(especie.rareza),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Información de pesca
                InfoSection("📍 Hábitat", especie.habitat)
                InfoSection("🎣 Carnadas", especie.mejoresCarnadas.joinToString(", "))
                InfoSection("⏰ Mejor horario", especie.mejorHorario)
                InfoSection("🎯 Técnica", especie.tecnica)
                InfoSection("📏 Tamaño", especie.tamaño)
                InfoSection("📅 Temporada", especie.temporada)
                InfoSection("💡 Consejo", especie.consejoEspecial)

                // Estadísticas personales si está capturada
                if (esCapturada && datosCaptura != null) {
                    Spacer(modifier = Modifier.height(12.dp))

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
                            datosCaptura.pesoRecord?.let {
                                Text("Peso récord: ${it}kg")
                            }
                            if (datosCaptura.locaciones.isNotEmpty()) {
                                Text("Lugares: ${datosCaptura.locaciones.joinToString(", ")}")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón de registro si no está capturada
                if (!esCapturada) {
                    OutlinedTextField(
                        value = pesoIngresado,
                        onValueChange = { pesoIngresado = it },
                        label = { Text("Peso (kg) - opcional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val peso = pesoIngresado.toDoubleOrNull()
                            onRegistrarCaptura(especie.id, peso)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = obtenerColorRareza(especie.rareza)
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("¡La Pesqué!", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSection(titulo: String, contenido: String) {
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
                    Text(
                        text = "🎉",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 64.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "¡Nueva Especie!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = obtenerColorRareza(resultado.especieInfo.rareza)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = obtenerEmojiPez(resultado.especieInfo.id),
                        fontSize = 48.sp
                    )

                    Text(
                        text = resultado.especieInfo.nombreComun,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = resultado.especieInfo.nombreCientifico,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Progreso: ${resultado.totalEspecies} especies descubiertas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Mostrar logros desbloqueados
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

// Funciones auxiliares
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
        "dorado" -> "🐅" // tigre del río
        "surubi" -> "🐆" // pintado
        "pejerrey" -> "🐟"
        "pacu" -> "🐡"
        "tararira" -> "🦈"
        "sabalo" -> "🐠"
        "boga" -> "🐟"
        "bagre" -> "🐈" // gato
        "manguruyú" -> "🐋" // gigante
        "piraña" -> "🦷" // dentuda
        else -> "🐟"
    }
}
