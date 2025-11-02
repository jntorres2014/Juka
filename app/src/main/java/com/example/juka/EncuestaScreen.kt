package com.example.juka.auth

import android.app.DatePickerDialog
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.juka.data.AuthManager
import com.example.juka.data.encuesta.PreguntasEncuesta
import com.example.juka.data.encuesta.RespuestaPregunta
import com.example.juka.data.encuesta.TipoPregunta
import com.example.juka.data.encuesta.ValidadorEncuesta
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.viewmodel.EncuestaViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EncuestaScreen(
    authManager: AuthManager,
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    var preguntaActual by remember { mutableStateOf(0) }
    val respuestas = remember { mutableStateMapOf<Int, RespuestaPregunta>() }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val totalPreguntas = PreguntasEncuesta.obtenerTotalPreguntas()
    val pregunta = PreguntasEncuesta.PREGUNTAS.getOrNull(preguntaActual)
    val progreso = ((respuestas.size.toFloat() / totalPreguntas) * 100).toInt()
    val fechaInicio = remember { com.google.firebase.Timestamp.now() }
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
                        text = "Pregunta ${preguntaActual + 1} de $totalPreguntas ($progreso% completado)",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progreso / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1E3A8A)
                    )
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
                        PreguntaContentSimple(
                            pregunta = pregunta,
                            respuestaActual = respuestas[pregunta.id],
                            onRespuestaChanged = { nuevaRespuesta ->
                                respuestas[pregunta.id] = nuevaRespuesta
                                mensajeError = null
                            }
                        )
                    }

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
                    OutlinedButton(
                        onClick = {
                            preguntaActual -= 1
                            mensajeError = null
                        },
                        enabled = preguntaActual > 0 && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anterior")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    if (preguntaActual == totalPreguntas - 1) {
                        Button(
                            onClick = {
                                if (pregunta != null && pregunta.esObligatoria) {
                                    val respuesta = respuestas[pregunta.id]
                                    if (respuesta == null) {
                                        mensajeError = "Esta pregunta es obligatoria"
                                        return@Button
                                    }
                                    val validacion = ValidadorEncuesta.validarRespuesta(pregunta, respuesta)
                                    if (!validacion.esValida) {
                                        mensajeError = validacion.mensajeError
                                        return@Button
                                    }
                                }

                                val preguntasObligatorias = PreguntasEncuesta.PREGUNTAS.filter { it.esObligatoria }
                                val faltantes = preguntasObligatorias.filter { respuestas[it.id] == null }

                                if (faltantes.isNotEmpty()) {
                                    mensajeError = "Faltan ${faltantes.size} preguntas obligatorias"
                                    return@Button
                                }

                                // <-- 2. LANZAR LA COROUTINE AQUÍ
                                scope.launch {
                                    try {
                                        isLoading = true
                                        val guardado =
                                            authManager.guardarEncuestaCompleta(respuestas)
                                        if (guardado) {
                                            navController.navigate("home") {
                                                popUpTo("survey") { inclusive = true }
                                            }
                                        } else {
                                            mensajeError = "Error al guardar la encuesta"
                                            isLoading = false
                                        }

                                        // Ahora la llamada a la función suspend es segura
                                        authManager.markSurveyCompleted()
                                        //EncuestaViewModel.completarEncuesta()
                                        // La navegación también debe ir aquí para asegurar que se
                                        // ejecute después de que `markSurveyCompleted` termine.
                                        navController.navigate("home") {
                                            popUpTo("survey") { inclusive = true }
                                        }
                                        // Podrías poner isLoading = false aquí si la pantalla no desaparece
                                        // inmediatamente, pero con la navegación, no es estrictamente necesario.
                                    } catch (e: Exception) {
                                        mensajeError = "Error al guardar la encuesta"
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Completar")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (pregunta != null && pregunta.esObligatoria) {
                                    val respuesta = respuestas[pregunta.id]
                                    if (respuesta == null) {
                                        mensajeError = "Esta pregunta es obligatoria"
                                        return@Button
                                    }
                                    val validacion = ValidadorEncuesta.validarRespuesta(pregunta, respuesta)
                                    if (!validacion.esValida) {
                                        mensajeError = validacion.mensajeError
                                        return@Button
                                    }
                                }

                                preguntaActual += 1
                                mensajeError = null
                            },
                            enabled = preguntaActual < totalPreguntas - 1 && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Siguiente")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreguntaContentSimple(
    pregunta: com.example.juka.data.encuesta.Pregunta,
    respuestaActual: RespuestaPregunta?,
    onRespuestaChanged: (RespuestaPregunta) -> Unit
) {
    val context = LocalContext.current

    Column {
        Text(
            text = pregunta.texto,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (pregunta.tipo) {
            TipoPregunta.FECHA -> {
                var fechaSeleccionada by remember(pregunta.id) {
                    mutableStateOf(respuestaActual?.respuestaFecha ?: "")
                }

                OutlinedTextField(
                    value = fechaSeleccionada,
                    onValueChange = {},
                    label = { Text("Fecha de nacimiento") },
                    placeholder = { Text("DD/MM/AAAA") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val calendar = Calendar.getInstance()
                            val year = calendar.get(Calendar.YEAR)
                            val month = calendar.get(Calendar.MONTH)
                            val day = calendar.get(Calendar.DAY_OF_MONTH)

                            DatePickerDialog(
                                context,
                                { _, selectedYear, selectedMonth, selectedDay ->
                                    val fecha = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                                    fechaSeleccionada = fecha
                                    onRespuestaChanged(
                                        RespuestaPregunta(
                                            preguntaId = pregunta.id,
                                            respuestaFecha = fecha
                                        )
                                    )
                                },
                                year,
                                month,
                                day
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TipoPregunta.NUMERO -> {
                var numero by remember(pregunta.id) {
                    mutableStateOf(respuestaActual?.respuestaNumero?.toString() ?: "")
                }

                OutlinedTextField(
                    value = numero,
                    onValueChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            numero = it
                            val numeroInt = it.toIntOrNull()
                            if (numeroInt != null) {
                                onRespuestaChanged(
                                    RespuestaPregunta(
                                        preguntaId = pregunta.id,
                                        respuestaNumero = numeroInt
                                    )
                                )
                            }
                        }
                    },
                    label = { Text("Ingrese año") },
                    placeholder = { Text(pregunta.placeholder) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TipoPregunta.TEXTO_LIBRE -> {
                var texto by remember(pregunta.id) {
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
                var seleccionada by remember(pregunta.id) {
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
                var seleccionadas by remember(pregunta.id) {
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
                var valor by remember(pregunta.id) {
                    mutableStateOf(
                        respuestaActual?.valorEscala?.toFloat()
                            ?: ((pregunta.rangoEscala.first + pregunta.rangoEscala.last) / 2f)
                    )
                }

                Column {
                    Text(
                        text = "Valor: ${valor.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
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
                        valueRange = pregunta.rangoEscala.first.toFloat()..pregunta.rangoEscala.last.toFloat(),
                        steps = pregunta.rangoEscala.last - pregunta.rangoEscala.first - 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in pregunta.rangoEscala) {
                            Text(
                                text = i.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            TipoPregunta.SI_NO -> {
                var respuesta by remember(pregunta.id) {
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (respuesta == true) Color(0xFF4CAF50) else Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sí", color = Color.White)
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (respuesta == false) Color(0xFFFFA500) else Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("No", color = Color.White)
                    }
                }
            }
        }
    }
}