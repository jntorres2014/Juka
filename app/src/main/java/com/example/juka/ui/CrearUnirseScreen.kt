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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import com.example.juka.FishDatabase
import com.example.juka.HukaApplication
import com.example.juka.domain.model.ReglasPuntaje
import com.example.juka.domain.model.TipoPuntaje
import com.example.juka.viewmodel.TorneosViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Estado de UI de una regla "tipo especie" en la tabla editable. Se mantiene
 * como nombre visible + id normalizado para que el cálculo después matchee.
 */
private data class FilaEspecieRegla(
    val nombreVisible: String,
    val idNormalizado: String,
    val puntos: String
)

/** Normalización idéntica a la del ViewModel para que las keys coincidan. */
private fun normalizarParaIdEspecie(nombre: String): String =
    nombre.lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

// ── Crear Torneo ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearTorneoScreen(
    viewModel: TorneosViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var fechaInicio by remember { mutableStateOf<Date?>(null) }
    var fechaFin by remember { mutableStateOf<Date?>(null) }
    var tipoPuntaje by remember { mutableStateOf(TipoPuntaje.CANTIDAD_PECES) }
    var reglasPersonalizadas by remember { mutableStateOf("") }

    // ─── Estado de las reglas estructuradas (solo aplica si PERSONALIZADO) ──
    var bonusPrimerParteOn by remember { mutableStateOf(false) }
    var bonusPrimerParteValor by remember { mutableStateOf("20") }
    var puntosPorPezOn by remember { mutableStateOf(false) }
    var puntosPorPezValor by remember { mutableStateOf("1") }
    var puntosPorEspecieOn by remember { mutableStateOf(false) }
    var filasEspecie by remember { mutableStateOf<List<FilaEspecieRegla>>(emptyList()) }
    var puntosOtros by remember { mutableStateOf("0") }

    // Catálogo de FishDatabase para el dropdown de especies. Carga perezosa
    // — la primera vez que el usuario abre el selector se inicializa.
    val fishDatabase = remember { (context.applicationContext as HukaApplication).fishDatabase }
    var catalogoEspecies by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (!fishDatabase.isInitialized()) fishDatabase.initialize()
        catalogoEspecies = fishDatabase.getAllSpecies()
            .map { it.name }
            .distinct()
            .sorted()
    }

    // Validaciones derivadas
    val fechasValidas = fechaInicio != null && fechaFin != null &&
            fechaFin!!.after(fechaInicio)
    val fechasError = fechaInicio != null && fechaFin != null && !fechasValidas

    // Para PERSONALIZADO con reglas estructuradas pedimos AL MENOS una regla
    // activa con valor válido. Si ninguna está activa, no se puede crear.
    val reglasPersonalizadasOk: Boolean = run {
        if (tipoPuntaje != TipoPuntaje.PERSONALIZADO) return@run true
        // Aceptamos el modo legacy (texto libre solamente) como antes.
        val hayTextoLibre = reglasPersonalizadas.isNotBlank()
        val hayReglaEstructurada =
            (bonusPrimerParteOn && bonusPrimerParteValor.toIntOrNull() != null) ||
            (puntosPorPezOn && puntosPorPezValor.toIntOrNull() != null) ||
            (puntosPorEspecieOn && filasEspecie.any { it.puntos.toIntOrNull() != null })
        hayTextoLibre || hayReglaEstructurada
    }

    val puedeCrear = nombre.isNotBlank() && fechasValidas && reglasPersonalizadasOk

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
                        val dialog = DatePickerDialog(context, { _, y, m, d ->
                            cal.set(y, m, d, 0, 0, 0)
                            fechaInicio = cal.time
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        // Torneos no pueden empezar en el pasado. minDate = hoy 00:00
                        val hoyCero = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        dialog.datePicker.minDate = hoyCero.timeInMillis
                        dialog.show()
                    }
                )
                DatePickerButton(
                    label = "Fin",
                    fecha = fechaFin?.let { fmt.format(it) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cal = Calendar.getInstance()
                        val dialog = DatePickerDialog(context, { _, y, m, d ->
                            cal.set(y, m, d, 23, 59, 59)
                            fechaFin = cal.time
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        // minDate: si ya eligió fechaInicio, no puede elegir
                        // una fecha de fin anterior. Si no, mínimo hoy.
                        val minCal = (fechaInicio ?: Calendar.getInstance().time).let { f ->
                            Calendar.getInstance().apply {
                                time = f
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        }
                        dialog.datePicker.minDate = minCal.timeInMillis
                        dialog.show()
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

            // ───────── Panel de reglas estructuradas (solo si PERSONALIZADO) ─────────
            if (tipoPuntaje == TipoPuntaje.PERSONALIZADO) {
                ReglasPersonalizadasPanel(
                    bonusOn = bonusPrimerParteOn,
                    bonusValor = bonusPrimerParteValor,
                    onBonusToggle = { bonusPrimerParteOn = !bonusPrimerParteOn },
                    onBonusValorChange = { bonusPrimerParteValor = it.filter { c -> c.isDigit() } },
                    porPezOn = puntosPorPezOn,
                    porPezValor = puntosPorPezValor,
                    onPorPezToggle = { puntosPorPezOn = !puntosPorPezOn },
                    onPorPezValorChange = { puntosPorPezValor = it.filter { c -> c.isDigit() } },
                    porEspecieOn = puntosPorEspecieOn,
                    filas = filasEspecie,
                    catalogo = catalogoEspecies,
                    onPorEspecieToggle = { puntosPorEspecieOn = !puntosPorEspecieOn },
                    onAgregarEspecie = { nombreEsp ->
                        val id = normalizarParaIdEspecie(nombreEsp)
                        if (filasEspecie.none { it.idNormalizado == id }) {
                            filasEspecie = filasEspecie + FilaEspecieRegla(
                                nombreVisible = nombreEsp,
                                idNormalizado = id,
                                puntos = "5"
                            )
                        }
                    },
                    onCambiarPuntos = { idx, valor ->
                        filasEspecie = filasEspecie.mapIndexed { i, fila ->
                            if (i == idx) fila.copy(puntos = valor.filter { c -> c.isDigit() })
                            else fila
                        }
                    },
                    onQuitarEspecie = { idx ->
                        filasEspecie = filasEspecie.filterIndexed { i, _ -> i != idx }
                    },
                    otrosValor = puntosOtros,
                    onOtrosValorChange = { puntosOtros = it.filter { c -> c.isDigit() } }
                )

                // Texto libre legacy — opcional, queda como nota adicional
                // para los participantes (ej. aclaraciones sobre tamaños o
                // zonas que no se pueden codificar como reglas).
                OutlinedTextField(
                    value = reglasPersonalizadas,
                    onValueChange = { reglasPersonalizadas = it },
                    label = { Text("Notas adicionales (opcional)") },
                    placeholder = { Text("Ej: Solo se cuentan piezas mayores a 30 cm") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )
            }

            // Mensaje de error de fechas (validación local)
            if (fechasError) {
                Text(
                    "La fecha de fin debe ser posterior a la de inicio.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            // Error de Firebase / ViewModel
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Botón crear
            Button(
                onClick = {
                    // Armamos ReglasPuntaje SOLO si el torneo es PERSONALIZADO
                    // y hay al menos una regla estructurada activa.
                    val reglasFinales: ReglasPuntaje? = if (tipoPuntaje == TipoPuntaje.PERSONALIZADO) {
                        val tabla = if (puntosPorEspecieOn) {
                            filasEspecie
                                .mapNotNull { fila ->
                                    val pts = fila.puntos.toIntOrNull() ?: return@mapNotNull null
                                    fila.idNormalizado to pts
                                }
                                .toMap()
                                .takeIf { it.isNotEmpty() }
                        } else null

                        val reglas = ReglasPuntaje(
                            bonusPrimerParte = if (bonusPrimerParteOn) bonusPrimerParteValor.toIntOrNull() else null,
                            puntosPorPez = if (puntosPorPezOn) puntosPorPezValor.toIntOrNull() else null,
                            puntosPorEspecie = tabla,
                            puntosOtrosPeces = puntosOtros.toIntOrNull() ?: 0
                        )
                        reglas.takeIf { it.tieneAlgunaRegla }
                    } else null

                    viewModel.crearTorneo(
                        nombre = nombre,
                        descripcion = descripcion,
                        fechaInicio = fechaInicio!!,
                        fechaFin = fechaFin!!,
                        tipoPuntaje = tipoPuntaje,
                        reglasPersonalizadas = reglasPersonalizadas,
                        reglas = reglasFinales
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

// ── Panel de reglas personalizadas ───────────────────────────────────────────

/**
 * Panel componible que muestra las 3 reglas estructuradas de un torneo
 * PERSONALIZADO con su estado activo/inactivo, valor configurable y un
 * resumen del ejemplo al pie. Equivalente al mockup que validamos antes
 * de implementar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReglasPersonalizadasPanel(
    bonusOn: Boolean,
    bonusValor: String,
    onBonusToggle: () -> Unit,
    onBonusValorChange: (String) -> Unit,
    porPezOn: Boolean,
    porPezValor: String,
    onPorPezToggle: () -> Unit,
    onPorPezValorChange: (String) -> Unit,
    porEspecieOn: Boolean,
    filas: List<FilaEspecieRegla>,
    catalogo: List<String>,
    onPorEspecieToggle: () -> Unit,
    onAgregarEspecie: (String) -> Unit,
    onCambiarPuntos: (Int, String) -> Unit,
    onQuitarEspecie: (Int) -> Unit,
    otrosValor: String,
    onOtrosValorChange: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Reglas del puntaje",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Regla 1: Bonus al primer parte ────────────────────────────
            ReglaCard(
                titulo = "Bonus al primer parte del torneo",
                subtitulo = "Lo gana el primer participante que cargue un parte. Una sola vez en todo el torneo.",
                activa = bonusOn,
                onToggle = onBonusToggle
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PuntosMiniInput(
                        valor = bonusValor,
                        onValorChange = onBonusValorChange,
                        ancho = 64.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("puntos extra", fontSize = 13.sp)
                }
            }

            // ── Regla 2: Por cantidad de peces ────────────────────────────
            ReglaCard(
                titulo = "Por cantidad de peces",
                subtitulo = "Se suma por cada ejemplar registrado, sin importar la especie.",
                activa = porPezOn,
                onToggle = onPorPezToggle
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PuntosMiniInput(
                        valor = porPezValor,
                        onValorChange = onPorPezValorChange,
                        ancho = 64.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("punto por cada pez", fontSize = 13.sp)
                }
            }

            // ── Regla 3: Puntos por especie + Otros ───────────────────────
            ReglaCard(
                titulo = "Puntos por especie",
                subtitulo = "Valor específico para las especies que elijas. Las que no estén en la tabla usan \"Otros\".",
                activa = porEspecieOn,
                onToggle = onPorEspecieToggle
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (filas.isEmpty()) {
                        Text(
                            "Todavía no agregaste especies.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filas.forEachIndexed { idx, fila ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    fila.nombreVisible,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                PuntosMiniInput(
                                    valor = fila.puntos,
                                    onValorChange = { onCambiarPuntos(idx, it) },
                                    ancho = 56.dp
                                )
                                IconButton(onClick = { onQuitarEspecie(idx) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Selector "Agregar especie" con dropdown del catálogo
                    AgregarEspecieSelector(
                        catalogo = catalogo,
                        yaUsadas = filas.map { it.idNormalizado }.toSet(),
                        onElegir = onAgregarEspecie
                    )

                    // Campo "Otros" — catch-all para peces no listados
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Otros", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Cualquier pez no listado",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PuntosMiniInput(
                            valor = otrosValor,
                            onValorChange = onOtrosValorChange,
                            ancho = 56.dp
                        )
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReglaCard(
    titulo: String,
    subtitulo: String,
    activa: Boolean,
    onToggle: () -> Unit,
    contenido: @Composable () -> Unit
) {
    val alpha = if (activa) 1f else 0.5f
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = activa, onCheckedChange = { onToggle() })
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    titulo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            if (activa) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    subtitulo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.padding(start = 36.dp)) { contenido() }
            }
        }
    }
}

/** Input numérico pequeño para los puntos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PuntosMiniInput(
    valor: String,
    onValorChange: (String) -> Unit,
    ancho: Dp
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        modifier = Modifier.width(ancho).height(48.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(8.dp)
    )
}

/**
 * Selector para agregar una especie nueva a la tabla. Tiene un buscador
 * interno para que el admin pueda escribir parte del nombre y filtrar el
 * catálogo de 92 especies sin scroll infinito. Excluye las que ya están
 * en la tabla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgregarEspecieSelector(
    catalogo: List<String>,
    yaUsadas: Set<String>,
    onElegir: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    OutlinedButton(
        onClick = {
            query = ""
            abierto = true
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        enabled = catalogo.isNotEmpty()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Agregar especie", fontSize = 13.sp)
    }

    if (abierto) {
        // Filtramos catálogo: por query + excluir las ya usadas
        val disponibles = remember(catalogo, query, yaUsadas) {
            catalogo.filter { nombre ->
                val id = normalizarParaIdEspecie(nombre)
                id !in yaUsadas && (query.isBlank() || nombre.contains(query, ignoreCase = true))
            }
        }

        AlertDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { abierto = false }) { Text("Cancelar") }
            },
            title = { Text("Elegir especie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    if (disponibles.isEmpty()) {
                        Text(
                            "Sin resultados",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            disponibles.forEach { nombre ->
                                Surface(
                                    onClick = {
                                        onElegir(nombre)
                                        abierto = false
                                    },
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        nombre,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
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
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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