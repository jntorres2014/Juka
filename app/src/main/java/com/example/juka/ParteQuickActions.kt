package com.example.juka
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.ParteEnProgreso

// Enum para los diferentes campos del parte
enum class CampoParte(
    val displayName: String,
    val icon: ImageVector,
    val pregunta: String,
    val obligatorio: Boolean = false
) {
    FECHA(
        "📅 Fecha",
        Icons.Default.DateRange,
        "¿Qué día pescaste? Podés decir 'hoy', 'ayer' o una fecha específica",
        true
    ),
    HORARIOS(
        "⏰  Horarios",
        Icons.Default.Schedule,
        "¿A qué hora empezaste y terminaste de pescar?"
    ),
    UBICACION(
        "📍 Ubicación",
        Icons.Default.LocationOn,
        "¿Dónde pescaste? Tocá para seleccionar en el mapa o escribí el nombre del lugar",
        true
    ),
    ESPECIES(
        "🐟 Peces",
        Icons.Default.Phishing,
        "¿Qué pescaste y cuántos? Por ejemplo: '2 pejerreyes y 1 róbalo'",
        true
    ),
    MODALIDAD(
        "🎣 Modalidad",
        Icons.Default.Sailing,
        "¿Cómo pescaste? Desde costa, embarcado, kayak, etc.",
        true
    ),
    FOTOS(
        "📸 Fotos",
        Icons.Default.CameraAlt,
        "¿Tenés fotos de tu jornada? Agregá las que quieras"
    ),
    CANAS(
        "🎯 Cañas",
        Icons.Default.SportsMartialArts,
        "¿Con cuántas cañas pescaste?"
    ),
    OBSERVACIONES(
        "📝 Notas",
        Icons.Default.Notes,
        "¿Alguna observación adicional sobre tu jornada?"
    )
}

@Composable
fun ParteQuickActions(
    parteData: ParteEnProgreso,
    onCampoSelected: (CampoParte) -> Unit,
    currentFieldInProgress: CampoParte? = null,
    onGuardarBorrador: () -> Unit,  // NUEVO
    onCompletarParte: () -> Unit,   // NUEVO
    firebaseStatus: String?,        // NUEVO
    modifier: Modifier = Modifier
) {
    // Determinar qué campos ya están completos
    val camposCompletos = remember(parteData) {
        mutableSetOf<CampoParte>().apply {
            if (parteData.fecha != null) add(CampoParte.FECHA)
            if (parteData.horaInicio != null || parteData.horaFin != null) add(CampoParte.HORARIOS)
            if (parteData.ubicacion != null || parteData.nombreLugar != null) add(CampoParte.UBICACION)
            if (parteData.especiesCapturadas.isNotEmpty()) add(CampoParte.ESPECIES)
            if (parteData.modalidad != null) add(CampoParte.MODALIDAD)
            if (parteData.imagenes.isNotEmpty()) add(CampoParte.FOTOS)
            if (parteData.numeroCanas != null) add(CampoParte.CANAS)
            if (parteData.observaciones != null) add(CampoParte.OBSERVACIONES)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Título con progreso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Completá tu parte",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Indicador de progreso visual
                Text(
                    "${camposCompletos.size}/8 campos",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid de botones
            /*LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CampoParte.values().toList()) { campo ->
                    ParteActionButton(
                        campo = campo,
                        isCompleted = camposCompletos.contains(campo),
                        isInProgress = currentFieldInProgress == campo,
                        onClick = { onCampoSelected(campo) }
                    )
                }
            }*/
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Primera fila - 4 campos principales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        CampoParte.FECHA,
                        CampoParte.HORARIOS,
                        CampoParte.UBICACION,
                        CampoParte.ESPECIES
                    ).forEach { campo ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactParteButton(
                                campo = campo,
                                isCompleted = camposCompletos.contains(campo),
                                isInProgress = currentFieldInProgress == campo,
                                onClick = { onCampoSelected(campo) },
                                // Para especies, mostrar el total si hay
                                additionalInfo = if (campo == CampoParte.ESPECIES && parteData.especiesCapturadas.isNotEmpty()) {
                                    "${parteData.especiesCapturadas.sumOf { it.numeroEjemplares }}"
                                } else null
                            )
                        }
                    }
                }

                // Segunda fila - 4 campos secundarios
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        CampoParte.MODALIDAD,
                        CampoParte.FOTOS,
                        CampoParte.CANAS,
                        CampoParte.OBSERVACIONES
                    ).forEach { campo ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactParteButton(
                                campo = campo,
                                isCompleted = camposCompletos.contains(campo),
                                isInProgress = currentFieldInProgress == campo,
                                onClick = { onCampoSelected(campo) },
                                additionalInfo = null
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = parteData.porcentajeCompletado >= 30, // Mostrar cuando hay algo de progreso
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Título de sección
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📊 Acciones del parte",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Indicador de estado de Firebase
                        firebaseStatus?.let { status ->
                            Surface(
                                color = when {
                                    status.contains("Guardado") || status.contains("exitosamente") -> Color(0xFF4CAF50)
                                    status.contains("Error") -> Color(0xFFFF5722)
                                    else -> Color(0xFFFF9800)
                                }.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = when {
                                        status.contains("Guardando") -> "⏳ Guardando..."
                                        status.contains("Guardado") -> "✅ Guardado"
                                        status.contains("Completando") -> "📤 Enviando..."
                                        status.contains("completado") -> "✅ Enviado"
                                        status.contains("Error") -> "❌ Error"
                                        else -> "⏳"
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botones de acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón guardar borrador
                        OutlinedButton(
                            onClick = onGuardarBorrador,
                            modifier = Modifier.weight(1f),
                            enabled = parteData.porcentajeCompletado >= 30 &&
                                    firebaseStatus?.contains("Guardando") != true,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Guardar Borrador",
                                fontSize = 13.sp
                            )
                        }

                        // Botón completar y enviar
                        Button(
                            onClick = onCompletarParte,
                            modifier = Modifier.weight(1f),
                            enabled = parteData.porcentajeCompletado >= 70 &&
                                    firebaseStatus?.contains("Completando") != true,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    parteData.porcentajeCompletado >= 90 -> Color(0xFF4CAF50)
                                    parteData.porcentajeCompletado >= 70 -> Color(0xFFFF9800)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (parteData.porcentajeCompletado >= 70)
                                    Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Crear parte",
                                fontSize = 13.sp,
                                color = if (parteData.porcentajeCompletado >= 70)
                                    Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mensaje de ayuda
                    if (parteData.porcentajeCompletado < 70) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "ℹ️ Completá al menos el 70% para poder enviar el parte",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // Sugerencias inteligentes
            AnimatedVisibility(
                visible = camposCompletos.size < 4,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "💡 Sugerencia rápida",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Botón de carga rápida
                    OutlinedButton(
                        onClick = { onCampoSelected(getSugerenciaProximoCampo(camposCompletos)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Completar ${getSugerenciaProximoCampo(camposCompletos).displayName}",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParteActionButton(
    campo: CampoParte,
    isCompleted: Boolean,
    isInProgress: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when {
        isInProgress -> MaterialTheme.colorScheme.primary
        isCompleted -> MaterialTheme.colorScheme.tertiaryContainer
        campo.obligatorio -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        isInProgress -> MaterialTheme.colorScheme.onPrimary
        isCompleted -> MaterialTheme.colorScheme.onTertiaryContainer
        campo.obligatorio -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    campo.displayName,
                    fontSize = 13.sp,
                    fontWeight = if (isInProgress) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )

                if (campo.obligatorio && !isCompleted) {
                    Text(
                        " *",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isCompleted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Completado",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF4CAF50)
                )
            } else if (isInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// Función auxiliar para sugerir el próximo campo
private fun getSugerenciaProximoCampo(camposCompletos: Set<CampoParte>): CampoParte {
    // Priorizar campos obligatorios primero
    val obligatoriosPendientes = CampoParte.values()
        .filter { it.obligatorio && !camposCompletos.contains(it) }

    if (obligatoriosPendientes.isNotEmpty()) {
        return obligatoriosPendientes.first()
    }

    // Luego los opcionales
    return CampoParte.values()
        .first { !camposCompletos.contains(it) }
}// Botón más compacto para el grid 4x2
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactParteButton(
    campo: CampoParte,
    isCompleted: Boolean,
    isInProgress: Boolean,
    onClick: () -> Unit,
    additionalInfo: String? = null
) {
    val containerColor = when {
        isInProgress -> MaterialTheme.colorScheme.primary
        isCompleted -> MaterialTheme.colorScheme.tertiaryContainer
        campo.obligatorio -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp), // Un poco más alto para que sea cómodo
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isInProgress) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Solo emoji
            Text(
                campo.displayName.take(2), // Solo el emoji
                fontSize = 16.sp
            )

            // Texto muy pequeño
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    campo.displayName.drop(3), // Sin el emoji
                    fontSize = 9.sp,
                    fontWeight = if (isInProgress) FontWeight.Bold else FontWeight.Normal,
                    color = if (isInProgress)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                // Mostrar check o info adicional
                when {
                    isCompleted -> {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("✓", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                    additionalInfo != null -> {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "($additionalInfo)",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}