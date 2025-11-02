package com.example.juka

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

// ================== SELECTOR DE MODALIDAD ==================
@Composable
fun ModalidadSelector(
    onModalidadSelected: (ModalidadPesca) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🎣 Seleccioná la modalidad",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opciones de modalidad
                val modalidades = listOf(
                    ModalidadPesca.CON_LINEA_COSTA to "🏖️ Desde Costa",
                    ModalidadPesca.CON_LINEA_EMBARCACION to "🚤 Embarcado",
                    ModalidadPesca.CON_RED to "🕸️ Con Red",
                    ModalidadPesca.PESCA_SUBMARINA_COSTA to "🤿 Submarina",
                    ModalidadPesca.PESCA_SUBMARINA_EMBARCACION to "🤿🚤 Submarina Embarcado",
                    //ModalidadPesca.OTRO to "❓ Otro"
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(modalidades) { (modalidad, display) ->
                        ModalidadOption(
                            modalidad = modalidad,
                            displayText = display,
                            onClick = {
                                onModalidadSelected(modalidad)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalidadOption(
    modalidad: ModalidadPesca,
    displayText: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                displayText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}

// ================== SELECTOR DE FECHA RÁPIDA ==================
@Composable
fun FechaRapidaSelector(
    onFechaSelected: (String) -> Unit,
    onCustomDate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "📅 ¿Cuándo pescaste?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val calendar = Calendar.getInstance()

                // Hoy
                FechaOption(
                    label = "Hoy",
                    fecha = dateFormat.format(calendar.time),
                    icon = "☀️",
                    onClick = {
                        onFechaSelected(dateFormat.format(calendar.time))
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Ayer
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                FechaOption(
                    label = "Ayer",
                    fecha = dateFormat.format(calendar.time),
                    icon = "🌙",
                    onClick = {
                        onFechaSelected(dateFormat.format(calendar.time))
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Antes de ayer
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                FechaOption(
                    label = "Antes de ayer",
                    fecha = dateFormat.format(calendar.time),
                    icon = "📆",
                    onClick = {
                        onFechaSelected(dateFormat.format(calendar.time))
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botón para fecha personalizada
                OutlinedButton(
                    onClick = {
                        onCustomDate()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otra fecha")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FechaOption(
    label: String,
    fecha: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    fecha,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ================== SELECTOR DE NÚMERO DE CAÑAS ==================
@Composable
fun NumeroCanasSelector(
    onNumeroSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🎯 ¿Cuántas cañas usaste?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Grid de números
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..9).toList()) { numero ->
                        NumeroButton(
                            numero = numero,
                            onClick = {
                                onNumeroSelected(numero)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumeroButton(
    numero: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                numero.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}