package com.example.juka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.juka.domain.model.EstadoParte
import com.example.juka.domain.model.EstadoParteTorneo
import com.example.juka.domain.model.ParticipanteTorneo
import com.example.juka.domain.model.ParteTorneo
import com.example.juka.domain.model.TorneoConParticipantes
import com.example.juka.viewmodel.TorneosViewModel

// ── Modelo auxiliar para agrupar ─────────────────────────────────────────────

private data class GrupoParticipante(
    val participante: ParticipanteTorneo,
    val partes: List<ParteTorneo>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartesTorneoScreen(
    torneoConP: TorneoConParticipantes,
    viewModel: TorneosViewModel,
    onBack: () -> Unit
) {
    val esAdmin = torneoConP.soyCreador
    var parteArechazar by remember { mutableStateOf<ParteTorneo?>(null) }
    var motivoRechazo by remember { mutableStateOf("") }
    var fotoExpandida by remember { mutableStateOf<String?>(null) }

    // Agrupar partes por participante, ordenados por puntaje desc
    val grupos: List<GrupoParticipante> = remember(torneoConP) {
        val aceptados = torneoConP.participantes
            .filter { it.puntaje >= 0 }
            .sortedByDescending { it.puntaje }

        aceptados.map { participante ->
            val partesDeEste = torneoConP.partes
                .filter { it.userId == participante.userId }
                .sortedByDescending { it.creadoEn.seconds }  // más recientes primero
            GrupoParticipante(participante, partesDeEste)
        }
    }

    // Diálogo de rechazo
    parteArechazar?.let { parte ->
        AlertDialog(
            onDismissRequest = { parteArechazar = null; motivoRechazo = "" },
            icon = { Text("⚠️", fontSize = 28.sp) },
            title = { Text("Rechazar parte") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Este parte dejará de contar. Se restarán ${parte.puntaje} pts a ${parte.userName}.",
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = motivoRechazo,
                        onValueChange = { motivoRechazo = it },
                        label = { Text("Motivo (opcional)") },
                        placeholder = { Text("Ej: Sin foto, especie no válida...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rechazarParte(torneoConP.torneo.id, parte.parteId, motivoRechazo)
                        parteArechazar = null
                        motivoRechazo = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Rechazar") }
            },
            dismissButton = {
                TextButton(onClick = { parteArechazar = null; motivoRechazo = "" }) { Text("Cancelar") }
            }
        )
    }

    // Foto expandida
    fotoExpandida?.let { url ->
        Dialog(
            onDismissRequest = { fotoExpandida = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fotoExpandida = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Partes del torneo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(torneoConP.torneo.nombre, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { viewModel.cargarTorneos() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        }
    ) { paddingValues ->

        if (grupos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎣", fontSize = 48.sp)
                    Text("Todavía no hay partes", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Los partes aparecen cuando los participantes los guardan durante el torneo.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "${grupos.size} participantes · ${torneoConP.partes.size} partes en total",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(grupos, key = { it.participante.userId }) { grupo ->
                GrupoParticipanteCard(
                    grupo = grupo,
                    esAdmin = esAdmin,
                    onRechazar = { parte -> parteArechazar = parte },
                    onVerFoto = { url -> fotoExpandida = url }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ── Tarjeta colapsable por participante ──────────────────────────────────────

@Composable
private fun GrupoParticipanteCard(
    grupo: GrupoParticipante,
    esAdmin: Boolean,
    onRechazar: (ParteTorneo) -> Unit,
    onVerFoto: (String) -> Unit
) {
    var expandido by remember { mutableStateOf(true) }  // Expandido por defecto
    val participante = grupo.participante
    val partesActivos = grupo.partes.filter { false }
    val partesRechazados = grupo.partes.filter { false }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // ── Header del grupo (siempre visible, toca para colapsar) ────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandido = !expandido }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar con inicial
                if (participante.userPhoto.isNotBlank()) {
                    AsyncImage(
                        model = participante.userPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                participante.userName.first().uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(participante.userName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${grupo.partes.size} ${if (grupo.partes.size == 1) "parte" else "partes"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (partesRechazados.isNotEmpty()) {
                            Text(
                                "${partesRechazados.size} rechazado${if (partesRechazados.size > 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Puntaje total
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "${participante.puntaje} pts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chevron
                Icon(
                    if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ── Lista de partes (colapsable) ──────────────────────────────────
            AnimatedVisibility(
                visible = expandido,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()

                    if (grupo.partes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Todavía no cargó partes",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else {
                        // Partes activos
                        partesActivos.forEachIndexed { index, parte ->
                            ParteRow(
                                parte = parte,
                                esAdmin = esAdmin,
                                onRechazar = { onRechazar(parte) },
                                onVerFoto = onVerFoto
                            )
                            if (index < partesActivos.lastIndex || partesRechazados.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                            }
                        }

                        // Partes rechazados (al final del grupo)
                        partesRechazados.forEachIndexed { index, parte ->
                            ParteRow(
                                parte = parte,
                                esAdmin = esAdmin,
                                onRechazar = null,
                                onVerFoto = onVerFoto
                            )
                            if (index < partesRechazados.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Fila individual de parte ─────────────────────────────────────────────────

@Composable
private fun ParteRow(
    parte: ParteTorneo,
    esAdmin: Boolean,
    onRechazar: (() -> Unit)?,
    onVerFoto: (String) -> Unit
) {
    val rechazado = false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Foto thumbnail o ícono
        Box(modifier = Modifier.size(56.dp)) {
            if (parte.fotos.isNotEmpty()) {
                Card(
                    onClick = { onVerFoto(parte.fotos.first()) },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AsyncImage(
                        model = parte.fotos.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                // Sin foto — advertencia visible para admin
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (esAdmin) Color(0xFFFFF3CD) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ImageNotSupported,
                            contentDescription = null,
                            tint = if (esAdmin) Color(0xFF856404) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Badge rechazado
            if (rechazado) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp).padding(2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Info del parte
        Column(modifier = Modifier.weight(1f)) {
            // Especies
            if (parte.especies.isNotEmpty()) {
                Text(
                    parte.especies.joinToString(" · ") { "${it.cantidad}x ${it.nombre}" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (rechazado) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text("Sin capturas", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Fecha y puntaje
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (parte.fecha.isNotBlank()) {
                    Text(parte.fecha, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!rechazado) {
                    Text("+${parte.puntaje} pts", fontSize = 11.sp, color = Color(0xFF1D9E75), fontWeight = FontWeight.Medium)
                }
            }

            // Motivo de rechazo
            if (rechazado) {
                Text(
                    if (parte.motivoRechazo.isNotBlank()) "❌ ${parte.motivoRechazo}" else "❌ Rechazado",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Aviso sin foto (solo para admin)
            if (esAdmin && parte.fotos.isEmpty() && !rechazado) {
                Text("⚠️ Sin foto", fontSize = 11.sp, color = Color(0xFF856404))
            }
        }

        // Botón rechazar (solo admin, solo activos)
        if (esAdmin && !rechazado && onRechazar != null) {
            IconButton(
                onClick = onRechazar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = "Rechazar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}