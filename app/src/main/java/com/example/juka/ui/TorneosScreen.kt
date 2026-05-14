package com.example.juka.ui.torneos

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.example.juka.domain.model.EstadoParticipante
import com.example.juka.domain.model.EstadoTorneo
import com.example.juka.domain.model.ParticipanteTorneo
import com.example.juka.domain.model.TorneoConParticipantes
import com.example.juka.viewmodel.TorneosViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorneosScreen(
    viewModel: TorneosViewModel,
    onCrearTorneo: () -> Unit,
    onUnirse: () -> Unit,
    onVerPartes: (torneoId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar solicitud enviada
    LaunchedEffect(uiState.solicitudEnviada) {
        if (uiState.solicitudEnviada) {
            snackbarHostState.showSnackbar("✅ Solicitud enviada. Esperá que el organizador te acepte.")
            viewModel.limpiarSolicitudEnviada()
        }
    }

    // ✅ Diálogo de código — aparece al crear Y cuando el admin lo quiere re-ver
    uiState.torneoCreado?.let { codigo ->
        CodigoTorneoDialog(
            codigo = codigo,
            onDismiss = { viewModel.limpiarTorneoCreado() },
            onCompartir = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "¡Unite a mi torneo de pesca en Huka! 🎣\nUsá este código: $codigo")
                    setPackage("com.whatsapp")
                }
                try { context.startActivity(intent) }
                catch (e: Exception) { context.startActivity(Intent.createChooser(intent, "Compartir código")) }
                viewModel.limpiarTorneoCreado()
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onUnirse, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.Search, contentDescription = "Unirme a torneo")
                }
                FloatingActionButton(onClick = onCrearTorneo) {
                    Icon(Icons.Default.Add, contentDescription = "Crear torneo")
                }
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.torneos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(120.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🏆", fontSize = 64.sp)
                            }
                        }
                        Text("Sumate a la competencia", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Creá un torneo entre amigos o unite con el código que te compartieron.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = onUnirse,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unirme")
                            }
                            Button(
                                onClick = onCrearTorneo,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75))
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Crear torneo")
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Column {
                            Text("🏆 Mis Torneos", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${uiState.torneos.size} torneo${if (uiState.torneos.size == 1) "" else "s"} en tu cuenta",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(uiState.torneos, key = { it.torneo.id }) { torneoConP ->
                        TorneoCard(
                            torneoConP = torneoConP,
                            onAceptar = { participanteId -> viewModel.responderSolicitud(torneoConP.torneo.id, participanteId, true) },
                            onRechazar = { participanteId -> viewModel.responderSolicitud(torneoConP.torneo.id, participanteId, false) },
                            onVerCodigo = { viewModel.mostrarCodigoTorneo(torneoConP.torneo.codigoInvitacion) },
                            onVerPartes = { onVerPartes(torneoConP.torneo.id) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorneoCard(
    torneoConP: TorneoConParticipantes,
    onAceptar: (String) -> Unit,
    onRechazar: (String) -> Unit,
    onVerCodigo: () -> Unit,
    onVerPartes: () -> Unit
) {
    val torneo = torneoConP.torneo
    var expandido by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val estadoColor = when (torneo.estado) {
        EstadoTorneo.PROXIMO    -> Color(0xFF378ADD)
        EstadoTorneo.ACTIVO     -> Color(0xFF1D9E75)
        EstadoTorneo.FINALIZADO -> Color(0xFF888780)
    }
    val estadoLabel = when (torneo.estado) {
        EstadoTorneo.PROXIMO    -> "Próximo"
        EstadoTorneo.ACTIVO     -> "En curso"
        EstadoTorneo.FINALIZADO -> "Finalizado"
    }

    Card(
        onClick = { expandido = !expandido },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(torneo.nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${fmt.format(torneo.fechaInicio.toDate())} → ${fmt.format(torneo.fechaFin.toDate())}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = estadoColor.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                    Text(estadoLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = estadoColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info rápida
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip("🎯", torneo.tipoPuntajeEnum.displayName)
                InfoChip("👥", "${torneoConP.aceptados.size} participantes")
            }

            // ✅ CÓDIGO SIEMPRE VISIBLE PARA EL ADMIN
            if (torneoConP.soyCreador) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onVerPartes,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver partes (${torneoConP.partes.size})", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("👑 Organizador · Código de invitación:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(torneo.codigoInvitacion, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onVerCodigo) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir código", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Mi estado (participante)
            torneoConP.miEstado?.let { estado ->
                Spacer(modifier = Modifier.height(8.dp))
                val (color, label) = when (estado) {
                    EstadoParticipante.PENDIENTE  -> Pair(Color(0xFFEF9F27), "⏳ Solicitud pendiente")
                    EstadoParticipante.ACEPTADO   -> Pair(Color(0xFF1D9E75), "✅ Participando — Puesto #${torneoConP.miPosicion}")
                    EstadoParticipante.RECHAZADO  -> Pair(Color(0xFFE24B4A), "❌ Solicitud rechazada")
                }
                Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
            }

            // Solicitudes pendientes (solo creador)
            if (torneoConP.soyCreador && torneoConP.pendientes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Solicitudes pendientes (${torneoConP.pendientes.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF9F27))
                Spacer(modifier = Modifier.height(6.dp))
                torneoConP.pendientes.forEach { p ->
                    SolicitudRow(nombre = p.userName, onAceptar = { onAceptar(p.userId) }, onRechazar = { onRechazar(p.userId) })
                }
            }

            // Leaderboard expandible
            if (torneoConP.aceptados.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { expandido = !expandido }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expandido) "Ocultar leaderboard ▲" else "Ver leaderboard ▼", fontSize = 13.sp)
                }

                if (expandido) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    torneoConP.aceptados.forEachIndexed { index, p ->
                        LeaderboardRow(posicion = index + 1, participante = p)
                        if (index < torneoConP.aceptados.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CodigoTorneoDialog(codigo: String, onDismiss: () -> Unit, onCompartir: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🏆", fontSize = 32.sp) },
        title = { Text("Código de invitación") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Compartí este código con tus compañeros:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(codigo, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = onCompartir) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Compartir por WhatsApp")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun SolicitudRow(nombre: String, onAceptar: () -> Unit, onRechazar: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(nombre.first().uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(nombre, modifier = Modifier.weight(1f), fontSize = 14.sp)
        IconButton(onClick = onRechazar, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Rechazar", tint = Color(0xFFE24B4A))
        }
        IconButton(onClick = onAceptar, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Check, contentDescription = "Aceptar", tint = Color(0xFF1D9E75))
        }
    }
}

@Composable
private fun LeaderboardRow(posicion: Int, participante: ParticipanteTorneo) {
    // El top 3 se destaca con fondo dorado/plateado/bronce y texto más grande
    val (bgColor, badgeColor) = when (posicion) {
        1 -> Color(0xFFFFF8E1) to Color(0xFFFFB300)
        2 -> Color(0xFFF5F5F5) to Color(0xFF9E9E9E)
        3 -> Color(0xFFFBE9E7) to Color(0xFFCD7F32)
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val esPodio = posicion <= 3

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = if (esPodio) 8.dp else 0.dp, vertical = if (esPodio) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge de posición
        Surface(
            modifier = Modifier.size(if (esPodio) 36.dp else 28.dp),
            shape = CircleShape,
            color = if (esPodio) badgeColor else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = when (posicion) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$posicion" },
                    fontSize = if (esPodio) 18.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (esPodio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Avatar
        if (participante.userPhoto.isNotBlank()) {
            AsyncImage(
                model = participante.userPhoto,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        participante.userName.first().uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        Text(
            participante.userName,
            modifier = Modifier.weight(1f),
            fontSize = if (esPodio) 15.sp else 14.sp,
            fontWeight = if (esPodio) FontWeight.SemiBold else FontWeight.Medium
        )

        // Puntaje en chip
        Surface(
            color = if (esPodio) badgeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "${participante.puntaje} pts",
                fontSize = if (esPodio) 14.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (esPodio) badgeColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun InfoChip(icon: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}