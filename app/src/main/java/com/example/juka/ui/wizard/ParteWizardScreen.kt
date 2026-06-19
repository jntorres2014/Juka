package com.example.juka.ui.wizard

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.juka.FishDatabase
import com.example.juka.data.local.ImageHelper
import com.example.juka.domain.model.*
import com.example.juka.ui.theme.MapPickerScreen
import com.example.juka.viewmodel.EnhancedChatViewModel
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private data class WizardData(
    val fecha: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val horaInicio: String = "",
    val horaFin: String = "",
    val modalidad: ModalidadPesca? = null,
    val esOtraModalidad: Boolean = false,
    // Texto libre cuando el usuario eligió "Otra" como modalidad.
    val otraModalidadTexto: String = "",
    val ubicacion: GeoPoint? = null,
    val nombreLugar: String = "",
    val especies: List<EspecieCapturada> = emptyList(),
    val numeroCanas: Int? = null,
    val observaciones: String = "",
    val imagenPath: String? = null
)

// Obligatorios: hora inicio+fin (paso 0), modalidad (1), cañas (4), observaciones (5)
// Opcionales:   ubicación (2), especies (3), foto (6)
private fun stepIsValid(step: Int, data: WizardData): Boolean = when (step) {
    0 -> data.horaInicio.isNotBlank() && data.horaFin.isNotBlank()
    1 -> data.modalidad != null ||
            (data.esOtraModalidad && data.otraModalidadTexto.isNotBlank())
    4 -> data.numeroCanas != null
    5 -> data.observaciones.isNotBlank()
    else -> true
}

private val STEP_TITLES = listOf(
    "¿Cuándo fuiste a pescar?",
    "¿Cómo pescaste?",
    "¿Dónde pescaste?",
    "¿Qué capturaste?",
    "¿Cuántas cañas usaste?",
    "¿Algo más para agregar?",
    "¿Querés agregar una foto?"
)

private val STEP_TAGS = listOf(
    "Fecha y hora", "Modalidad", "Ubicación",
    "Especies", "Cañas", "Notas", "Foto"
)

private val GREEN = Color(0xFF1D9E75)
private val GREEN_LIGHT = Color(0xFFE1F5EE)
private val GREEN_DARK = Color(0xFF0F6E56)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParteWizardScreen(
    viewModel: EnhancedChatViewModel,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageHelper = remember { ImageHelper(context) }

    var currentStep by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(WizardData()) }
    var showMapPicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showStepError by remember { mutableStateOf(false) }

    val totalSteps = STEP_TITLES.size
    val progress = (currentStep + 1).toFloat() / totalSteps

    // Picker unificado: muestra un BottomSheet con cámara o galería. La Uri
    // que devuelve va al mismo `imageHelper.saveImageToInternalStorage` que
    // antes — el helper acepta tanto content:// de galería como las file://
    // de FileProvider (cámara).
    val abrirPickerImagen = com.example.juka.component.rememberImagePickerWithCamera { uri ->
        scope.launch {
            val path = imageHelper.saveImageToInternalStorage(uri)
            if (path != null) data = data.copy(imagenPath = path)
        }
    }

    if (showMapPicker) {
        MapPickerScreen(
            onDismiss = { showMapPicker = false },
            onLocationSelected = { lat, lon, name ->
                data = data.copy(ubicacion = GeoPoint(lat, lon), nombreLugar = name ?: data.nombreLugar)
                showMapPicker = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Crear parte asistido", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep > 0) { currentStep--; showStepError = false }
                            else onFinished()
                        }) {
                            Icon(if (currentStep > 0) Icons.Default.ArrowBack else Icons.Default.Close, null)
                        }
                    }
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Paso ${currentStep + 1} de $totalSteps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(20.dp), color = GREEN_LIGHT) {
                            Text(STEP_TAGS[currentStep], fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GREEN_DARK, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = GREEN, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
            },
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            label = "wizard_step"
        ) { step ->
            Column(modifier = Modifier.fillMaxSize()) {
                Text(STEP_TITLES[step], fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    when (step) {
                        0 -> Step1_DateTime(data, showStepError) { data = it }
                        1 -> Step2_Modalidad(data, showStepError) { data = it }
                        2 -> Step3_Ubicacion(data, onOpenMap = { showMapPicker = true }) { data = it }
                        3 -> Step4_Especies(data) { data = it }
                        4 -> Step5_Canas(data, showStepError) { data = it }
                        5 -> Step6_Observaciones(data, showStepError) { data = it }
                        6 -> Step7_Foto(data, onPickImage = abrirPickerImagen) { data = it }
                    }
                }
                HorizontalDivider()
                Box(modifier = Modifier.padding(16.dp)) {
                    if (step == totalSteps - 1) {
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch { buildAndSave(data, viewModel); isSaving = false; onFinished() }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = GREEN),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Guardar parte", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (stepIsValid(currentStep, data)) { showStepError = false; currentStep++ }
                                else showStepError = true
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Siguiente", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, null)
                        }
                    }
                }
            }
        }
    }

    if (isSaving) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = GREEN)
                    Text("Guardando parte...", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// Paso 1 — Fecha y hora | OBLIGATORIO: horaInicio y horaFin
//
// Validaciones agregadas (feedback de correcciones):
//  - La fecha no puede ser futura. El DatePicker tiene maxDate seteado en
//    el "ahora" del sistema, así el calendario directamente no deja tocar
//    días futuros (UX nativa de Android).
//  - La hora no puede ser futura si la fecha del parte es HOY. Como el
//    TimePickerDialog estándar no soporta limitar horas, validamos en el
//    callback: si la hora elegida cae en el futuro, mostramos toast y no
//    actualizamos el state.
@Composable
private fun Step1_DateTime(data: WizardData, showError: Boolean, onUpdate: (WizardData) -> Unit) {
    val context = LocalContext.current

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(context, { _, y, m, d ->
            onUpdate(data.copy(fecha = String.format("%04d-%02d-%02d", y, m + 1, d)))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        // Bloquear selección de fechas futuras en el calendario nativo.
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    fun showTimePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        TimePickerDialog(context, { _, h, min ->
            // Si la fecha seleccionada es HOY, verificamos que la hora no
            // sea futura. Comparamos contra la hora actual del sistema.
            if (esFechaHoy(data.fecha) && esHoraFuturaDeHoy(h, min)) {
                android.widget.Toast.makeText(
                    context,
                    "No podés elegir una hora futura para una jornada de hoy",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@TimePickerDialog
            }
            val t = String.format("%02d:%02d", h, min)
            onUpdate(if (isStart) data.copy(horaInicio = t) else data.copy(horaFin = t))
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WizardRow(label = "Fecha", icon = Icons.Default.CalendarToday, value = data.fecha, onClick = ::showDatePicker)
        WizardRow(label = "Hora de inicio", icon = Icons.Default.Schedule, value = data.horaInicio.ifBlank { "Seleccionar hora" }, isError = showError && data.horaInicio.isBlank(), onClick = { showTimePicker(true) })
        if (showError && data.horaInicio.isBlank()) StepErrorText("Seleccioná la hora de inicio.")
        WizardRow(label = "Hora de fin", icon = Icons.Default.Schedule, value = data.horaFin.ifBlank { "Seleccionar hora" }, isError = showError && data.horaFin.isBlank(), onClick = { showTimePicker(false) })
        if (showError && data.horaFin.isBlank()) StepErrorText("Seleccioná la hora de fin.")
        HintText("El horario se usa para calcular la duración de la jornada.")
    }
}

/**
 * ¿El string `fecha` (formato "yyyy-MM-dd") corresponde a hoy?
 * Usado para decidir si validamos la hora contra el reloj actual.
 */
private fun esFechaHoy(fecha: String): Boolean {
    if (fecha.isBlank()) return false
    return try {
        val partes = fecha.split("-")
        if (partes.size != 3) return false
        val y = partes[0].toInt()
        val m = partes[1].toInt() - 1
        val d = partes[2].toInt()
        val hoy = Calendar.getInstance()
        d == hoy.get(Calendar.DAY_OF_MONTH) &&
            m == hoy.get(Calendar.MONTH) &&
            y == hoy.get(Calendar.YEAR)
    } catch (e: Exception) { false }
}

/** ¿La hora HH:mm cae más tarde que el reloj actual? */
private fun esHoraFuturaDeHoy(h: Int, min: Int): Boolean {
    val ahora = Calendar.getInstance()
    val horaAhora = ahora.get(Calendar.HOUR_OF_DAY)
    val minAhora = ahora.get(Calendar.MINUTE)
    return h > horaAhora || (h == horaAhora && min > minAhora)
}

// Paso 2 — Modalidad | OBLIGATORIO
@Composable
private fun Step2_Modalidad(data: WizardData, showError: Boolean, onUpdate: (WizardData) -> Unit) {
    data class Opcion(val emoji: String, val label: String, val modalidad: ModalidadPesca?, val isOtra: Boolean = false)
    // 5 modalidades del enum + "Otra" con texto libre.
    val opciones = listOf(
        Opcion("🏖️", "Pesca de costa", ModalidadPesca.CON_LINEA_COSTA),
        Opcion("🚤", "Pesca embarcado", ModalidadPesca.CON_LINEA_EMBARCACION),
        Opcion("🕸️", "Con red", ModalidadPesca.CON_RED),
        Opcion("🤿", "Submarina costa", ModalidadPesca.PESCA_SUBMARINA_COSTA),
        Opcion("🤿🚤", "Submarina embarcado", ModalidadPesca.PESCA_SUBMARINA_EMBARCACION),
        Opcion("🎣", "Otra", null, isOtra = true)
    )
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        opciones.chunked(2).forEach { fila ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { op ->
                    val selected = if (op.isOtra) data.esOtraModalidad else data.modalidad == op.modalidad
                    ModeCard(op.emoji, op.label, selected, Modifier.weight(1f)) {
                        // Al cambiar de selección limpiamos el texto de "otra"
                        // para que no quede colgando si después elige una estándar.
                        onUpdate(
                            data.copy(
                                modalidad = op.modalidad,
                                esOtraModalidad = op.isOtra,
                                otraModalidadTexto = if (op.isOtra) data.otraModalidadTexto else ""
                            )
                        )
                    }
                }
            }
        }

        // Si eligió "Otra", se habilita el campo de texto libre.
        if (data.esOtraModalidad) {
            OutlinedTextField(
                value = data.otraModalidadTexto,
                onValueChange = { onUpdate(data.copy(otraModalidadTexto = it)) },
                label = { Text("Decinos cómo pescaste") },
                placeholder = { Text("Ej: trampa, lanza, mosca...") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = showError && data.otraModalidadTexto.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            if (showError && data.otraModalidadTexto.isBlank()) {
                StepErrorText("Escribí qué tipo de pesca usaste.")
            }
        }

        if (showError && data.modalidad == null && !data.esOtraModalidad) StepErrorText("Seleccioná cómo pescaste.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeCard(emoji: String, label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(110.dp), border = BorderStroke(if (isSelected) 2.dp else 0.5.dp, if (isSelected) GREEN else MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = if (isSelected) GREEN_LIGHT else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) GREEN_DARK else MaterialTheme.colorScheme.onSurface)
        }
    }
}

// Paso 3 — Ubicación | OPCIONAL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step3_Ubicacion(data: WizardData, onOpenMap: () -> Unit, onUpdate: (WizardData) -> Unit) {
    val hasLocation = data.ubicacion != null
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionalBadge("Opcional — podés saltear este paso")
        OutlinedCard(onClick = onOpenMap, modifier = Modifier.fillMaxWidth().height(140.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(if (hasLocation) 2.dp else 0.5.dp, if (hasLocation) GREEN else MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.outlinedCardColors(containerColor = if (hasLocation) GREEN_LIGHT else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(if (hasLocation) Icons.Default.CheckCircle else Icons.Default.Map, null, modifier = Modifier.size(36.dp), tint = if (hasLocation) GREEN else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(if (hasLocation) "Ubicación seleccionada ✓" else "Tocar para abrir el mapa", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (hasLocation) GREEN_DARK else MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasLocation && data.ubicacion != null) Text("${String.format("%.4f", data.ubicacion.latitude)}, ${String.format("%.4f", data.ubicacion.longitude)}", fontSize = 11.sp, color = GREEN)
            }
        }
        OutlinedTextField(value = data.nombreLugar, onValueChange = { onUpdate(data.copy(nombreLugar = it)) }, label = { Text("Nombre del lugar") }, placeholder = { Text("Ej: Playa Unión, Puerto Rawson...") }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }, trailingIcon = { if (data.nombreLugar.isNotBlank()) IconButton(onClick = { onUpdate(data.copy(nombreLugar = "")) }) { Icon(Icons.Default.Clear, null) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        HintText("Podés escribir el nombre aunque no uses el mapa.")
    }
}

// Paso 4 — Especies | OPCIONAL
@Composable
private fun Step4_Especies(data: WizardData, onUpdate: (WizardData) -> Unit) {
    val context = LocalContext.current
    val fishDatabase = remember { FishDatabase(context) }
    var busqueda by remember { mutableStateOf("") }
    var sugerencias by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { fishDatabase.initialize() }
    LaunchedEffect(busqueda) { sugerencias = if (busqueda.length >= 2) fishDatabase.searchSpecies(busqueda).map { it.name }.take(5) else emptyList() }
    fun agregar(nombre: String) {
        val lista = data.especies.toMutableList()
        val idx = lista.indexOfFirst { it.nombre.equals(nombre, ignoreCase = true) }
        if (idx != -1) lista[idx] = lista[idx].copy(numeroEjemplares = lista[idx].numeroEjemplares + 1)
        else lista.add(EspecieCapturada(nombre = nombre, numeroEjemplares = 1))
        onUpdate(data.copy(especies = lista)); busqueda = ""
    }
    fun restar(nombre: String) {
        val lista = data.especies.toMutableList()
        val idx = lista.indexOfFirst { it.nombre.equals(nombre, ignoreCase = true) }
        if (idx != -1) { val n = lista[idx].numeroEjemplares - 1; if (n <= 0) lista.removeAt(idx) else lista[idx] = lista[idx].copy(numeroEjemplares = n); onUpdate(data.copy(especies = lista)) }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OptionalBadge("Opcional — si no pescaste nada podés continuar") }
        item { OutlinedTextField(value = busqueda, onValueChange = { busqueda = it }, label = { Text("Buscar especie") }, placeholder = { Text("Ej: Pejerrey, Róbalo...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (busqueda.isNotBlank()) IconButton(onClick = { busqueda = "" }) { Icon(Icons.Default.Clear, null) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true) }
        if (sugerencias.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        sugerencias.forEachIndexed { i, especie ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { agregar(especie) }.padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🐟", fontSize = 16.sp); Spacer(Modifier.width(10.dp)); Text(especie, fontSize = 14.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            if (i < sugerencias.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }
        if (data.especies.isNotEmpty()) item { Text("Capturas registradas", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(data.especies, key = { it.nombre }) { especie ->
            Row(modifier = Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🐟", fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                Text(especie.nombre, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = { restar(especie.nombre) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp)) }
                    Text("${especie.numeroEjemplares}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 24.dp))
                    IconButton(onClick = { agregar(especie.nombre) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        if (data.especies.isEmpty() && sugerencias.isEmpty()) item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { Text("Buscá una especie para agregarla", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) } }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// Paso 5 — Cañas | OBLIGATORIO
@Composable
private fun Step5_Canas(data: WizardData, showError: Boolean, onUpdate: (WizardData) -> Unit) {
    val opciones = (1..9).toList() + 10
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        opciones.chunked(5).forEach { fila ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { n ->
                    val isSelected = data.numeroCanas == n
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) GREEN else MaterialTheme.colorScheme.surface).border(if (isSelected) 2.dp else 0.5.dp, if (isSelected) GREEN else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).clickable { onUpdate(data.copy(numeroCanas = n)) }, contentAlignment = Alignment.Center) {
                        Text(if (n == 10) "+10" else "$n", fontSize = if (n == 10) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (showError && data.numeroCanas == null) StepErrorText("Seleccioná la cantidad de cañas.")
        HintText("Si fuiste con amigos, indicá el total de cañas entre todos.")
    }
}

// Paso 6 — Observaciones | OBLIGATORIO
@Composable
private fun Step6_Observaciones(data: WizardData, showError: Boolean, onUpdate: (WizardData) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = data.observaciones, onValueChange = { onUpdate(data.copy(observaciones = it)) }, placeholder = { Text("Ej: mucho viento del sur, usamos lombriz, el agua estaba turbia...") }, modifier = Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(12.dp), maxLines = 8, isError = showError && data.observaciones.isBlank(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        if (showError && data.observaciones.isBlank()) StepErrorText("Escribí al menos una observación de la jornada.")
        HintText("Describí las condiciones del agua, carnadas usadas, clima, o lo que quieras recordar.")
    }
}

// Paso 7 — Foto | OPCIONAL
@Composable
private fun Step7_Foto(data: WizardData, onPickImage: () -> Unit, onUpdate: (WizardData) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionalBadge("Opcional — podés guardar el parte sin foto")
        Box(modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).border(if (data.imagenPath != null) 2.dp else 1.dp, if (data.imagenPath != null) GREEN else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).background(if (data.imagenPath != null) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).clickable(enabled = data.imagenPath == null) { onPickImage() }, contentAlignment = Alignment.Center) {
            if (data.imagenPath != null) {
                Image(painter = rememberAsyncImagePainter(File(data.imagenPath)), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = { onUpdate(data.copy(imagenPath = null)) }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tocar para elegir una foto", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (data.imagenPath != null) {
            OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Cambiar foto")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Componentes auxiliares
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardRow(label: String, icon: ImageVector, value: String, isError: Boolean = false, onClick: () -> Unit) {
    val isEmpty = value.startsWith("Seleccionar")
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(if (isError) 1.5.dp else 0.5.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 15.sp, fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.Medium, color = if (isEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OptionalBadge(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun StepErrorText(text: String) {
    Text("⚠ $text", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun HintText(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
}

private fun buildAndSave(data: WizardData, viewModel: EnhancedChatViewModel) {
    val parte = ParteEnProgreso(
        fecha = data.fecha,
        horaInicio = data.horaInicio.ifBlank { null },
        horaFin = data.horaFin.ifBlank { null },
        modalidad = data.modalidad,
        // Si el usuario eligió "Otra" guardamos el texto libre. La precedencia
        // (modalidadOtra > modalidad.displayName) la maneja la persistencia.
        modalidadOtra = if (data.esOtraModalidad) data.otraModalidadTexto.ifBlank { null } else null,
        ubicacion = data.ubicacion,
        nombreLugar = data.nombreLugar.ifBlank { null },
        especiesCapturadas = data.especies,
        numeroCanas = data.numeroCanas,
        observaciones = data.observaciones.ifBlank { null },
        imagenes = listOfNotNull(data.imagenPath),
        porcentajeCompletado = 100
    )
    viewModel.guardarParteDesdeWizard(parte)
}