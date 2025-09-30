package com.example.juka.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.juka.data.AuthManager
import com.example.juka.data.encuesta.PreguntasEncuesta
import com.example.juka.data.encuesta.RespuestaPregunta
import com.example.juka.data.encuesta.TipoPregunta
import com.example.juka.viewmodel.EncuestaViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncuestaScreen(
    authManager: AuthManager,
    navController: NavController,
    viewModel: EncuestaViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    // Estados del ViewModel
    val estadoEncuesta by viewModel.estadoEncuesta.collectAsState()
    val preguntaActual by viewModel.preguntaActual.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val mensajeError by viewModel.mensajeError.collectAsState()
    val encuestaCompletada by viewModel.encuestaCompletada.collectAsState()
    val guardandoProgreso by viewModel.guardandoProgreso.collectAsState()

    // Pregunta actual y respuesta
    val pregunta = viewModel.obtenerPreguntaActual()
    val respuestaActual = viewModel.obtenerRespuestaActual()

    // Si la encuesta se completó, navegar a home
    LaunchedEffect(encuestaCompletada) {
        if (encuestaCompletada) {
            authManager.markSurveyCompleted()
            navController.navigate("home") {
                popUpTo("survey") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E3A8A))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header con progreso
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Encuesta de Usuario",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = viewModel.obtenerResumenProgreso(),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Barra de progreso
                    LinearProgressIndicator(
                        progress = estadoEncuesta.progresoPorcentaje / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1E3A8A)
                    )

                    if (guardandoProgreso) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Guardando progreso...",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Contenido principal de la pregunta
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (pregunta != null) {
                        PreguntaContent(
                            pregunta = pregunta,
                            respuestaActual = respuestaActual,
                            onRespuestaChanged = { nuevaRespuesta ->
                                viewModel.guardarRespuesta(nuevaRespuesta)
                            }
                        )
                    }

                    // Mostrar error si existe
                    mensajeError?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Text(
                                text = error,
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Botones de navegación
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Botón Anterior
                    OutlinedButton(
                        onClick = { viewModel.preguntaAnterior() },
                        enabled = estadoEncuesta.puedeRetroceder && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anterior")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Botón Siguiente/Completar
                    if (preguntaActual == PreguntasEncuesta.obtenerTotalPreguntas() - 1) {
                        // Última pregunta - botón completar
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.completarEncuesta()
                                }
                            },
                            enabled = viewModel.puedeCompletarEncuesta() && !isLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Completar")
                            }
                        }
                    } else {
                        // Botón siguiente normal
                        Button(
                            onClick = {
                                viewModel.limpiarError()
                                viewModel.siguientePregunta()
                            },
                            enabled = estadoEncuesta.puedeAvanzar && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Siguiente")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }

                // Botón guardar progreso
                if (estadoEncuesta.progresoPorcentaje > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.guardarProgreso() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar progreso para continuar después")
                    }
                }
            }
        }
    }
}

@Composable
fun PreguntaContent(
    pregunta: com.example.juka.data.encuesta.Pregunta,
    respuestaActual: RespuestaPregunta?,
    onRespuestaChanged: (RespuestaPregunta) -> Unit
) {
    Column {
        Text(
            text = pregunta.texto,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (pregunta.tipo) {
            TipoPregunta.TEXTO_LIBRE -> {
                var texto by remember(respuestaActual) {
                    mutableStateOf(respuestaActual?.respuestaTexto ?: "")
                }

                OutlinedTextField(
                    value = texto,
                    onValueChange = {
                        texto = it
                        onRespuestaChanged(
                            RespuestaPregunta(
                                preguntaId = pregunta.id,
                                respuestaTexto = it
                            )
                        )
                    },
                    label = { Text(pregunta.placeholder) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            TipoPregunta.OPCION_MULTIPLE -> {
                var seleccionada by remember(respuestaActual) {
                    mutableStateOf(respuestaActual?.opcionSeleccionada ?: "")
                }

                Column(Modifier.selectableGroup()) {
                    pregunta.opciones.forEach { opcion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (seleccionada == opcion),
                                    onClick = {
                                        seleccionada = opcion
                                        onRespuestaChanged(
                                            RespuestaPregunta(
                                                preguntaId = pregunta.id,
                                                opcionSeleccionada = opcion
                                            )
                                        )
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (seleccionada == opcion),
                                onClick = null
                            )
                            Text(
                                text = opcion,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            TipoPregunta.SELECCION_MULTIPLE -> {
                var seleccionadas by remember(respuestaActual) {
                    mutableStateOf(respuestaActual?.opcionesSeleccionadas?.toSet() ?: emptySet())
                }

                Column {
                    pregunta.opciones.forEach { opcion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = seleccionadas.contains(opcion),
                                    onClick = {
                                        val nuevasSeleccionadas = if (seleccionadas.contains(opcion)) {
                                            seleccionadas - opcion
                                        } else {
                                            seleccionadas + opcion
                                        }
                                        seleccionadas = nuevasSeleccionadas
                                        onRespuestaChanged(
                                            RespuestaPregunta(
                                                preguntaId = pregunta.id,
                                                opcionesSeleccionadas = nuevasSeleccionadas.toList()
                                            )
                                        )
                                    },
                                    role = Role.Checkbox
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = seleccionadas.contains(opcion),
                                onCheckedChange = null
                            )
                            Text(
                                text = opcion,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            TipoPregunta.ESCALA -> {
                var valor by remember(respuestaActual) {
                    mutableStateOf(respuestaActual?.valorEscala?.toFloat() ?: 1f)
                }

                Column {
                    Text(
                        text = "Valor: ${valor.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Slider(
                        value = valor,
                        onValueChange = {
                            valor = it
                            onRespuestaChanged(
                                RespuestaPregunta(
                                    preguntaId = pregunta.id,
                                    valorEscala = it.toInt()
                                )
                            )
                        },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        pregunta.opciones.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(60.dp)
                            )
                        }
                    }
                }
            }

            TipoPregunta.SI_NO -> {
                var respuesta by remember(respuestaActual) {
                    mutableStateOf(respuestaActual?.respuestaSiNo)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            respuesta = true
                            onRespuestaChanged(
                                RespuestaPregunta(
                                    preguntaId = pregunta.id,
                                    respuestaSiNo = true
                                )
                            )
                        },
                        colors = if (respuesta == true) {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sí")
                    }

                    Button(
                        onClick = {
                            respuesta = false
                            onRespuestaChanged(
                                RespuestaPregunta(
                                    preguntaId = pregunta.id,
                                    respuestaSiNo = false
                                )
                            )
                        },
                        colors = if (respuesta == false) {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFFf44336))
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("No")
                    }
                }
            }
        }
    }
}