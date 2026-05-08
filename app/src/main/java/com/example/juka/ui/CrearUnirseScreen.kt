package com.example.juka.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.domain.model.TipoPuntaje
import com.example.juka.viewmodel.TorneosViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Crear Torneo ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearTorneoScreen(
    viewModel: TorneosViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var fechaInicio by remember { mutableStateOf<Date?>(null) }
    var fechaFin by remember { mutableStateOf<Date?>(null) }
    var tipoPuntaje by remember { mutableStateOf(TipoPuntaje.CANTIDAD_PECES) }
    var reglasPersonalizadas by remember { mutableStateOf("") }

    val puedeCrear = nombre.isNotBlank() && fechaInicio != null && fechaFin != null &&
            (tipoPuntaje != TipoPuntaje.PERSONALIZADO || reglasPersonalizadas.isNotBlank())

    // Volver automáticamente cuando se crea
    LaunchedEffect(uiState.torneoCreado) {
        if (uiState.torneoCreado != null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear torneo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nombre
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del torneo") },
                placeholder = { Text("Ej: Torneo del Río Paraná") },
                leadingIcon = { Icon(Icons.Default.EmojiEvents, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Descripción
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Contá de qué trata el torneo...") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            // Fechas
            Text("Período del torneo", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerButton(
                    label = "Inicio",
                    fecha = fechaInicio?.let { fmt.format(it) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            cal.set(y, m, d, 0, 0, 0)
                            fechaInicio = cal.time
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                )
                DatePickerButton(
                    label = "Fin",
                    fecha = fechaFin?.let { fmt.format(it) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            cal.set(y, m, d, 23, 59, 59)
                            fechaFin = cal.time
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                )
            }

            // Tipo de puntaje
            Text("¿Qué se puntea?", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TipoPuntaje.values().forEach { tipo ->
                    val selected = tipoPuntaje == tipo
                    OutlinedCard(
                        onClick = { tipoPuntaje = tipo },
                        border = CardDefaults.outlinedCardBorder().let {
                            if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else it
                        },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { tipoPuntaje = tipo })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(tipo.displayName, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (tipo == TipoPuntaje.PERSONALIZADO) {
                                    Text("Definí tus propias reglas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Reglas personalizadas
            if (tipoPuntaje == TipoPuntaje.PERSONALIZADO) {
                OutlinedTextField(
                    value = reglasPersonalizadas,
                    onValueChange = { reglasPersonalizadas = it },
                    label = { Text("Describí las reglas") },
                    placeholder = { Text("Ej: Gana quien pesque el dorado más grande") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5
                )
            }

            // Error
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Botón crear
            Button(
                onClick = {
                    viewModel.crearTorneo(
                        nombre = nombre,
                        descripcion = descripcion,
                        fechaInicio = fechaInicio!!,
                        fechaFin = fechaFin!!,
                        tipoPuntaje = tipoPuntaje,
                        reglasPersonalizadas = reglasPersonalizadas
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = puedeCrear && !uiState.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.EmojiEvents, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear torneo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerButton(label: String, fecha: String?, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(fecha ?: "Seleccionar", fontSize = 14.sp, fontWeight = if (fecha != null) FontWeight.SemiBold else FontWeight.Normal, color = if (fecha != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Unirse a torneo ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnirseATorneoScreen(
    viewModel: TorneosViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var codigo by remember { mutableStateOf("") }

    LaunchedEffect(uiState.solicitudEnviada) {
        if (uiState.solicitudEnviada) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unirme a torneo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Ingresá el código que te compartieron:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = codigo,
                onValueChange = { codigo = it.uppercase() },
                label = { Text("Código de torneo") },
                placeholder = { Text("HUKA-XXXXXX") },
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { viewModel.buscarTorneoPorCodigo(codigo) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = codigo.length >= 6 && !uiState.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Buscar torneo")
                }
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Vista previa del torneo encontrado
            uiState.torneoEncontrado?.let { torneoConP ->
                val torneo = torneoConP.torneo
                val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(torneo.nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Organiza: ${torneo.creatorName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${fmt.format(torneo.fechaInicio.toDate())} → ${fmt.format(torneo.fechaFin.toDate())}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Puntaje: ${torneo.tipoPuntajeEnum.displayName}", fontSize = 13.sp)
                        if (torneo.reglasPersonalizadas.isNotBlank()) {
                            Text("Reglas: ${torneo.reglasPersonalizadas}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { viewModel.solicitarUnirse(torneo.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Text("Solicitar unirme")
                        }

                        Text(
                            "El organizador deberá aceptar tu solicitud para que puedas participar.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}