package com.example.juka
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.domain.model.ParteEnProgreso

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
    onGuardarBorrador: () -> Unit,
    onCompletarParte: () -> Unit,
    firebaseStatus: String?,
    modifier: Modifier = Modifier
) {
    // Estado para colapsar/expandir el panel manualmente
    var isExpanded by remember { mutableStateOf(true) }

    // Calculamos campos completos
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
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) // Solo redondeado abajo
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // --- ENCABEZADO COMPACTO ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }, // Tocar el título colapsa
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Resumen del Parte",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Chip pequeño de progreso
                    Surface(
                        color = if (parteData.porcentajeCompletado >= 70) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${parteData.porcentajeCompletado}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (parteData.porcentajeCompletado >= 70) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Icono de colapsar
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- CONTENIDO EXPANDIBLE ---
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // GRILLA COMPACTA (4 Columnas x 2 Filas)
                    val campos = CampoParte.values().toList()
                    val filas = campos.chunked(4) // 4 elementos por fila

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filas.forEach { filaCampos ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween // Distribuye uniformemente
                            ) {
                                filaCampos.forEach { campo ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        CompactItem(
                                            campo = campo,
                                            isCompleted = camposCompletos.contains(campo),
                                            isInProgress = currentFieldInProgress == campo,
                                            onClick = { onCampoSelected(campo) }
                                        )
                                    }
                                }
                                // Rellenar espacio si la fila no está completa (aunque aquí son 8 exactos)
                                repeat(4 - filaCampos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // --- ACCIONES FINALES ---
                    if (parteData.porcentajeCompletado >= 10) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Status texto
                            Text(
                                text = firebaseStatus ?: if(parteData.porcentajeCompletado >= 70) "Listo para enviar" else "Falta información",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            // Botones pequeños
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onGuardarBorrador,
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Borrador", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = onCompletarParte,
                                    enabled = parteData.porcentajeCompletado >= 70,
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text("Enviar", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item ultra-compacto: Icono arriba, texto abajo.
 * Ocupa muy poco espacio.
 */
@Composable
fun CompactItem(
    campo: CampoParte,
    isCompleted: Boolean,
    isInProgress: Boolean,
    onClick: () -> Unit
) {
    val iconColor = when {
        isInProgress -> MaterialTheme.colorScheme.primary
        isCompleted -> Color(0xFF2E7D32) // Verde
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bgColor = when {
        isInProgress -> MaterialTheme.colorScheme.primaryContainer
        isCompleted -> Color(0xFFE8F5E9) // Verde muy claro
        else -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp) // Padding interno mínimo
    ) {
        // Contenedor del Icono
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = campo.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor
            )

            // Puntito indicador si es obligatorio y falta
            if (campo.obligatorio && !isCompleted && !isInProgress) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFFFF5252), CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Texto
        Text(
            text = campo.displayName.replace(Regex("[^a-zA-Záéíóúñ ]"), "").trim(), // Quitamos emojis del nombre
            fontSize = 10.sp,
            fontWeight = if(isInProgress) FontWeight.Bold else FontWeight.Normal,
            color = if(isInProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
/**
 * Tarjeta mejorada: Más ancha, usa iconos vectoriales y tiene mejor jerarquía visual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParteFieldCard(
    campo: CampoParte,
    isCompleted: Boolean,
    isInProgress: Boolean,
    extraInfo: String? = null,
    onClick: () -> Unit
) {
    // Definición de colores según estado
    val (bgColor, iconColor, textColor) = when {
        isInProgress -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        isCompleted -> Triple(
            Color(0xFFE8F5E9), // Verde muy suave
            Color(0xFF2E7D32), // Verde oscuro
            Color(0xFF1B5E20)
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.onSurface
        )
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(72.dp) // Altura fija cómoda para el dedo
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Icono (con fondo circular)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)), // Fondo suave para el icono
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = campo.icon, // ✅ Usamos el icono vectorial real
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Textos
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Limpiamos el nombre para quitar emojis si el enum los tiene en el texto
                // (Asumiendo que campo.displayName es "📅 Fecha", tomamos solo "Fecha")
                val cleanName = campo.displayName.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ ]"), "").trim()

                Text(
                    text = cleanName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtítulo: Muestra valor ("3 peces") o estado ("Pendiente")
                Text(
                    text = when {
                        isInProgress -> "Editando..."
                        extraInfo != null -> extraInfo
                        isCompleted -> "Listo"
                        campo.obligatorio -> "Requerido"
                        else -> "Opcional"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Indicador final (Check o Chevron)
            if (isCompleted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF2E7D32)
                )
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