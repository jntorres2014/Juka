// FishCounterScreen.kt - Versión mejorada con scroll para fuentes grandes
package com.example.juka.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.FishDatabase
import com.example.juka.viewmodel.EnhancedChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishCounterScreen(
    viewModel: EnhancedChatViewModel,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val fishDatabase = FishDatabase(context)
    val contador by viewModel.contadorPeces.collectAsState()
    val allSpecies = fishDatabase.getAllSpecies() // Conectar con tu lista real

    // Estados locales
    var busqueda by remember { mutableStateOf("") }
    var cantidadInput by remember { mutableStateOf(1) }
    var expanded by remember { mutableStateOf(false) }
    var mostrarSugerenciasRapidas by remember { mutableStateOf(true) }

    // Para el snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Total de peces capturados
    val totalPeces = remember(contador) {
        contador.sumOf { it.numeroEjemplares }
    }

    // Especies más comunes (para acceso rápido)
    val especiesPopulares = remember(allSpecies) {
        listOf("Pejerrey", "Dorado", "Surubí", "Tararira", "Bagre amarillo")
            .filter { nombre -> allSpecies.any { it.name == nombre } }
    }

    // Filtrar especies según búsqueda
    val especiesFiltradas = remember(busqueda, allSpecies) {
        if (busqueda.isBlank()) emptyList()
        else allSpecies
            .filter {
                it.name.contains(busqueda, ignoreCase = true) ||
                        it.scientificName?.contains(busqueda, ignoreCase = true) == true
            }
            .take(10) // Limitar a 10 resultados
            .map { it.name }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Espacio entre secciones
        ) {
            // === HEADER ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "🎣 Contador de Capturas",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Total: $totalPeces ${if (totalPeces == 1) "pez" else "peces"}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botón de estadísticas
                    if (contador.isNotEmpty()) {
                        AssistChip(
                            onClick = { /* TODO: Mostrar estadísticas */ },
                            label = { Text("📊") },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // === SUGERENCIAS RÁPIDAS ===
            if (mostrarSugerenciasRapidas && especiesPopulares.isNotEmpty()) {
                item {
                    Text(
                        "Acceso rápido:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        items(especiesPopulares) { especie ->
                            SuggestionChip(
                                onClick = {
                                    busqueda = especie
                                    expanded = false
                                },
                                label = { Text(especie, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // === SECCIÓN DE CARGA ===
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Buscador mejorado
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = busqueda,
                                onValueChange = {
                                    busqueda = it
                                    expanded = it.length >= 2 // Solo mostrar después de 2 caracteres
                                },
                                label = { Text("Buscar especie") },
                                placeholder = { Text("Ej: Dorado, Surubí...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, null)
                                },
                                trailingIcon = {
                                    if (busqueda.isNotEmpty()) {
                                        IconButton(onClick = { busqueda = "" }) {
                                            Icon(Icons.Default.Clear, null)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = expanded && especiesFiltradas.isNotEmpty(),
                                onDismissRequest = { expanded = false }
                            ) {
                                especiesFiltradas.forEach { especie ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Icons.Default.Phishing,
                                                    null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(especie)
                                            }
                                        },
                                        onClick = {
                                            busqueda = especie
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Selector de cantidad mejorado
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    "Cantidad",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (cantidadInput > 1) cantidadInput-- },
                                        enabled = cantidadInput > 1
                                    ) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            null,
                                            tint = if (cantidadInput > 1)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                    }

                                    // Campo de entrada directa
                                    OutlinedTextField(
                                        value = cantidadInput.toString(),
                                        onValueChange = { value ->
                                            value.toIntOrNull()?.let {
                                                if (it in 1..99) cantidadInput = it
                                            }
                                        },
                                        modifier = Modifier.width(80.dp),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )

                                    IconButton(onClick = { if (cantidadInput < 99) cantidadInput++ }) {
                                        Icon(
                                            Icons.Default.AddCircleOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (busqueda.isNotBlank()) {
                                        viewModel.agregarPezAlContador(busqueda, cantidadInput)

                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "✅ ${cantidadInput}x $busqueda agregado",
                                                duration = SnackbarDuration.Short
                                            )
                                        }

                                        busqueda = ""
                                        cantidadInput = 1
                                    }
                                },
                                enabled = busqueda.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar")
                            }
                        }
                    }
                }
            }

            // === LISTA DE CAPTURAS ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tus Capturas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    if (contador.isNotEmpty()) {
                        TextButton(onClick = { viewModel.limpiarContador() }) {
                            Text("Limpiar todo", color = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (contador.isEmpty()) {
                // Estado vacío
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),  // Altura fija para centrar en scroll
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Phishing,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No hay capturas registradas",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                "Agregá tu primera captura arriba",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                items(contador, key = { it.nombre }) { item ->
                    CapturaItemMejorado(
                        item = item,
                        onDelete = { viewModel.eliminarPezDelContador(item.nombre) },
                        onEdit = { nuevaCantidad ->
                            viewModel.actualizarCantidadPez(item.nombre, nuevaCantidad)
                        }
                    )
                }
            }

            // === BOTÓN FINAL ===
            item {
                Button(
                    onClick = {
                        viewModel.iniciarParteDesdeContador()
                        onNavigateToChat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(bottom = 16.dp),  // Padding extra para visibilidad en scroll
                    enabled = contador.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.Description, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CREAR PARTE CON ${contador.size} ${if (contador.size == 1) "ESPECIE" else "ESPECIES"}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,  // Permite wrap en 2 líneas si fuente grande
                        overflow = TextOverflow.Ellipsis  // Corta con ... si no cabe
                    )
                }
            }
        }
    }
}

// El resto (CapturaItemMejorado) queda igual, no necesita cambios para este issue.
@Composable
fun CapturaItemMejorado(
    item: com.example.juka.domain.model.EspecieCapturada,
    onDelete: () -> Unit,
    onEdit: (Int) -> Unit
) {
    var editando by remember { mutableStateOf(false) }
    var cantidadTemporal by remember(item.numeroEjemplares) {
        mutableStateOf(item.numeroEjemplares.toString())
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Badge de cantidad editable
                if (editando) {
                    OutlinedTextField(
                        value = cantidadTemporal,
                        onValueChange = { cantidadTemporal = it },
                        modifier = Modifier.width(70.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { editando = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.numeroEjemplares}x",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                Icons.Default.Edit,
                                null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.nombre,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    // Si tienes peso o talla promedio, mostrarlos aquí
                    item.pesoEstimado?.let { peso ->
                        Text(
                            "~${peso}kg promedio",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row {
                if (editando) {
                    IconButton(onClick = {
                        cantidadTemporal.toIntOrNull()?.let { nuevaCantidad ->
                            if (nuevaCantidad > 0) {
                                onEdit(nuevaCantidad)
                            }
                        }
                        editando = false
                    }) {
                        Icon(Icons.Default.Check, null, tint = Color.Green)
                    }
                    IconButton(onClick = {
                        editando = false
                        cantidadTemporal = item.numeroEjemplares.toString()
                    }) {
                        Icon(Icons.Default.Close, null, tint = Color.Red)
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}