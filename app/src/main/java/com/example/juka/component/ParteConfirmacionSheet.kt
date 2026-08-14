// ParteConfirmacionSheet.kt
//
// Bottom sheet de confirmación que se muestra ANTES de enviar el parte.
// El usuario ve todo lo que se cargó (por voz, por chat o por el asistido) y
// puede corregir cualquier campo escribiendo o dictando. Recién al confirmar
// se dispara el envío real.
//
// Decisión de diseño: la edición de un campo puntual es LITERAL — lo que el
// usuario escribe o dicta es el valor, sin pasar por el extractor. El extractor
// solo interviene en la carga inicial del relato completo.
package com.example.juka.component

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.juka.FishDatabase
import com.example.juka.data.local.ImageHelper
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.ModalidadPesca
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.ParteValidacion
import com.example.juka.domain.model.Provincia
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParteConfirmacionSheet(
    parte: ParteEnProgreso,
    isSending: Boolean,
    firebaseStatus: String?,
    onConfirmar: (ParteEnProgreso) -> Unit,
    onGuardarBorrador: (ParteEnProgreso) -> Unit,
    onMarcarEnMapa: (ParteEnProgreso) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Copia editable local. Todo lo que toca el usuario vive acá hasta confirmar.
    var editable by remember { mutableStateOf(parte) }

    // Base de datos de peces para el autocompletado de especies (mismo origen
    // que el parte asistido).
    val fishDatabase = remember { FishDatabase(context) }
    LaunchedEffect(Unit) { fishDatabase.initialize() }

    // "Otra" modalidad: estado explícito (no se deriva del texto, así el chip
    // queda marcado aunque el usuario todavía no haya escrito nada).
    var otraActiva by remember {
        mutableStateOf(parte.modalidad == null && !parte.modalidadOtra.isNullOrBlank())
    }

    // Picker de imagen propio del sheet (mismo patrón que el wizard). La foto
    // se agrega directo a la copia editable, sin pasar por el viewmodel.
    val scope = rememberCoroutineScope()
    val imageHelper = remember { ImageHelper(context) }
    val abrirPickerFoto = rememberImagePickerWithCamera { uri ->
        scope.launch {
            val path = imageHelper.saveImageToInternalStorage(uri)
            if (path != null) editable = editable.copy(imagenes = editable.imagenes + path)
        }
    }

    // DatePicker: prellenado con la fecha actual del parte si es parseable.
    // maxDate = hoy para no permitir fechas futuras.
    fun abrirDatePicker() {
        val cal = Calendar.getInstance()
        parseFechaIso(editable.fecha)?.let { cal.time = it }
        val dlg = DatePickerDialog(
            context,
            { _, y, m, d -> editable = editable.copy(fecha = String.format("%04d-%02d-%02d", y, m + 1, d)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dlg.datePicker.maxDate = System.currentTimeMillis()
        dlg.show()
    }

    fun abrirTimePicker(esInicio: Boolean) {
        val actual = (if (esInicio) editable.horaInicio else editable.horaFin)
        val cal = Calendar.getInstance()
        parseHora(actual)?.let { (h, min) -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min) }
        TimePickerDialog(
            context,
            { _, h, min ->
                val t = String.format("%02d:%02d", h, min)
                editable = if (esInicio) editable.copy(horaInicio = t) else editable.copy(horaFin = t)
            },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSending) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Revisá tu parte",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Revisá y corregí los datos. La narración por voz queda en Observaciones. Confirmás vos lo que se guarda.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            // ---- Fecha (DatePicker) ----
            PickerField(
                label = "Fecha",
                icon = Icons.Default.DateRange,
                value = formatearFechaLinda(editable.fecha),
                placeholder = "Elegí la fecha",
                onClick = { abrirDatePicker() }
            )

            // ---- Lugar ----
            CampoTexto(
                label = "Lugar",
                icon = Icons.Default.LocationOn,
                value = editable.nombreLugar.orEmpty(),
                placeholder = "Ej: Río Mayo, Playa Unión",
                onValue = { editable = editable.copy(nombreLugar = it.ifBlank { null }) }
            )
            TextButton(onClick = { onMarcarEnMapa(editable) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (editable.ubicacion != null) "Cambiar punto en el mapa" else "Marcar en el mapa (obligatorio)",
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))

            // ---- Provincia ----
            ProvinciaDropdown(
                seleccionada = editable.provincia,
                onSeleccion = { editable = editable.copy(provincia = it) }
            )
            Spacer(Modifier.height(14.dp))

            // ---- Modalidad ----
            ModalidadChips(
                modalidad = editable.modalidad,
                esOtra = otraActiva,
                otraTexto = editable.modalidadOtra.orEmpty(),
                onModalidad = {
                    otraActiva = false
                    editable = editable.copy(modalidad = it, modalidadOtra = null)
                },
                onSelectOtra = {
                    otraActiva = true
                    editable = editable.copy(modalidad = null)
                },
                onOtraText = { editable = editable.copy(modalidadOtra = it.ifBlank { null }) }
            )
            Spacer(Modifier.height(14.dp))

            // ---- Horarios (TimePickers) ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PickerField(
                        label = "Hora inicio",
                        icon = Icons.Default.Schedule,
                        value = editable.horaInicio.orEmpty(),
                        placeholder = "07:00",
                        onClick = { abrirTimePicker(esInicio = true) }
                    )
                }
                Box(Modifier.weight(1f)) {
                    PickerField(
                        label = "Hora fin",
                        icon = Icons.Default.Schedule,
                        value = editable.horaFin.orEmpty(),
                        placeholder = "11:00",
                        onClick = { abrirTimePicker(esInicio = false) }
                    )
                }
            }

            // ---- Cañas ----
            CampoTexto(
                label = "Número de cañas",
                icon = Icons.Default.Phishing,
                value = editable.numeroCanas?.toString().orEmpty(),
                placeholder = "Ej: 2",
                soloNumeros = true,
                onValue = { editable = editable.copy(numeroCanas = it.toIntOrNull()) }
            )

            // ---- Especies ----
            EspeciesEditor(
                capturas = editable.especiesCapturadas,
                fishDatabase = fishDatabase,
                onCapturas = {
                    editable = editable.copy(
                        especiesCapturadas = it,
                        sinCapturas = it.isEmpty() && editable.sinCapturas
                    )
                }
            )
            Spacer(Modifier.height(14.dp))

            // ---- Observaciones ----
            CampoTexto(
                label = "Observaciones",
                icon = Icons.Default.Notes,
                value = editable.observaciones.orEmpty(),
                placeholder = "Notas: clima, agua, momento de pique...",
                multilinea = true,
                onValue = { editable = editable.copy(observaciones = it.ifBlank { null }) }
            )
            Spacer(Modifier.height(14.dp))

            // ---- Foto (OBLIGATORIA) ----
            FotoObligatoriaSection(
                imagenes = editable.imagenes,
                onAgregar = abrirPickerFoto,
                onQuitar = { path -> editable = editable.copy(imagenes = editable.imagenes - path) }
            )

            Spacer(Modifier.height(20.dp))

            // Validación unificada: mismos obligatorios que el wizard.
            val faltantes = ParteValidacion.camposFaltantes(editable)

            if (isSending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(firebaseStatus ?: "Enviando...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (faltantes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Falta para poder enviar:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text(faltantes.joinToString(", "), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onGuardarBorrador(editable) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Guardar borrador") }

                    Button(
                        onClick = { onConfirmar(editable) },
                        enabled = faltantes.isEmpty(),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Confirmar y enviar")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Campo de texto editable con etiqueta (sin voz: la narración del pescador
// queda en Observaciones).
// ---------------------------------------------------------------------------
@Composable
private fun CampoTexto(
    label: String,
    icon: ImageVector,
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    soloNumeros: Boolean = false,
    multilinea: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
            singleLine = !multilinea,
            keyboardOptions = if (soloNumeros) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Campo de solo lectura que abre un picker (fecha / hora) al tocarlo.
// ---------------------------------------------------------------------------
@Composable
private fun PickerField(
    label: String,
    icon: ImageVector,
    value: String,
    placeholder: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        // Overlay transparente para capturar el toque (un OutlinedTextField
        // readOnly no dispara onClick por sí solo).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
        )
    }
}

// ---------------------------------------------------------------------------
// Sección de foto obligatoria: miniaturas + botón agregar/cambiar.
// ---------------------------------------------------------------------------
@Composable
private fun FotoObligatoriaSection(
    imagenes: List<String>,
    onAgregar: () -> Unit,
    onQuitar: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Foto", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("(obligatoria)", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))

        if (imagenes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { onAgregar() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Tocar para agregar una foto", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                imagenes.forEach { path ->
                    val model = if (path.startsWith("http")) path else File(path)
                    Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))) {
                        Image(
                            painter = rememberAsyncImagePainter(model),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onQuitar(path) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(26.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.Close, "Quitar", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAgregar) {
                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar otra foto", fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dropdown de provincia
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvinciaDropdown(
    seleccionada: Provincia?,
    onSeleccion: (Provincia?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = seleccionada?.displayName ?: "Sin especificar",
            onValueChange = {},
            readOnly = true,
            label = { Text("Provincia") },
            leadingIcon = { Icon(Icons.Default.Map, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin especificar") },
                onClick = { onSeleccion(null); expanded = false }
            )
            Provincia.values().forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.displayName) },
                    onClick = { onSeleccion(p); expanded = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chips de modalidad (5 del enum + "Otra" con texto libre)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalidadChips(
    modalidad: ModalidadPesca?,
    esOtra: Boolean,
    otraTexto: String,
    onModalidad: (ModalidadPesca) -> Unit,
    onSelectOtra: () -> Unit,
    onOtraText: (String) -> Unit
) {
    Column {
        Text("Modalidad", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModalidadPesca.values().forEach { m ->
                FilterChip(
                    selected = modalidad == m && !esOtra,
                    onClick = { onModalidad(m) },
                    label = { Text(m.displayName, fontSize = 13.sp) }
                )
            }
            FilterChip(
                selected = esOtra,
                onClick = onSelectOtra,
                label = { Text("Otra", fontSize = 13.sp) }
            )
        }
        if (esOtra) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = otraTexto,
                onValueChange = onOtraText,
                label = { Text("¿Cuál?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Editor de especies: buscador con sugerencias del JSON + stepper por captura
// (mismo patrón que el parte asistido)
// ---------------------------------------------------------------------------
@Composable
private fun EspeciesEditor(
    capturas: List<EspecieCapturada>,
    fishDatabase: FishDatabase,
    onCapturas: (List<EspecieCapturada>) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var sugerencias by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(busqueda) {
        sugerencias = if (busqueda.length >= 2)
            fishDatabase.searchSpecies(busqueda).map { it.name }.take(5)
        else emptyList()
    }

    fun agregar(nombre: String) {
        val lista = capturas.toMutableList()
        val idx = lista.indexOfFirst { it.nombre.equals(nombre, ignoreCase = true) }
        if (idx != -1) lista[idx] = lista[idx].copy(numeroEjemplares = lista[idx].numeroEjemplares + 1)
        else lista.add(EspecieCapturada(nombre = nombre, numeroEjemplares = 1))
        onCapturas(lista)
        busqueda = ""
    }

    fun ajustar(nombre: String, delta: Int) {
        val lista = capturas.toMutableList()
        val idx = lista.indexOfFirst { it.nombre.equals(nombre, ignoreCase = true) }
        if (idx != -1) {
            val n = lista[idx].numeroEjemplares + delta
            if (n <= 0) lista.removeAt(idx)
            // Al bajar el total, los devueltos no pueden superarlo.
            else lista[idx] = lista[idx].copy(numeroEjemplares = n, numeroDevueltos = lista[idx].numeroDevueltos.coerceAtMost(n))
            onCapturas(lista)
        }
    }

    fun ajustarDevuelto(nombre: String, delta: Int) {
        val lista = capturas.toMutableList()
        val idx = lista.indexOfFirst { it.nombre.equals(nombre, ignoreCase = true) }
        if (idx != -1) {
            val e = lista[idx]
            lista[idx] = e.copy(numeroDevueltos = (e.numeroDevueltos + delta).coerceIn(0, e.numeroEjemplares))
            onCapturas(lista)
        }
    }

    Column {
        Text("Especies", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar especie") },
            placeholder = { Text("Ej: róbalo, pejerrey...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (sugerencias.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column {
                    sugerencias.forEachIndexed { i, especie ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { agregar(especie) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🐟", fontSize = 15.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(especie, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (i < sugerencias.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }

        if (capturas.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            capturas.forEach { especie ->
                val retenidos = especie.numeroEjemplares - especie.numeroDevueltos
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐟", fontSize = 17.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(especie.nombre, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { ajustar(especie.nombre, -1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Remove, "Restar", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            "${especie.numeroEjemplares}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.widthIn(min = 24.dp)
                        )
                        IconButton(onClick = { ajustar(especie.nombre, 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, "Sumar", modifier = Modifier.size(18.dp))
                        }
                    }
                    // Devolución al agua por especie.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Waves, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Devueltos al agua", fontSize = 12.5.sp)
                            Text("Te llevás $retenidos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { ajustarDevuelto(especie.nombre, -1) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Remove, "Menos devueltos", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "${especie.numeroDevueltos}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 22.dp)
                        )
                        IconButton(onClick = { ajustarDevuelto(especie.nombre, 1) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Add, "Más devueltos", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers de fecha / hora
// ---------------------------------------------------------------------------

/** Parsea "yyyy-MM-dd" a Date para prellenar el DatePicker. null si no aplica. */
private fun parseFechaIso(fecha: String?): java.util.Date? {
    if (fecha.isNullOrBlank()) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(fecha)
    } catch (e: Exception) {
        null
    }
}

/** Muestra la fecha en formato lindo "dd/MM/yyyy". Si no es ISO, la deja igual. */
private fun formatearFechaLinda(fecha: String?): String {
    if (fecha.isNullOrBlank()) return ""
    val d = parseFechaIso(fecha) ?: return fecha
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
}

/** Parsea "HH:mm" a (hora, minuto) para prellenar el TimePicker. */
private fun parseHora(hora: String?): Pair<Int, Int>? {
    if (hora.isNullOrBlank()) return null
    val partes = hora.split(":")
    if (partes.size != 2) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    return h to m
}
