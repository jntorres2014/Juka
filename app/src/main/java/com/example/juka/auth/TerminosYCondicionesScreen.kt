package com.example.juka.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.data.AuthManager
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────
// Pantalla 1: checkbox + link "He leído los términos y condiciones"
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AceptarTerminosScreen(
    authManager: AuthManager,
    onTerminosAceptados: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }
    var mostrarPolitica by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var guardando by remember { mutableStateOf(false) }

    if (mostrarPolitica) {
        PoliticaPrivacidadScreen(onVolver = { mostrarPolitica = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Términos y condiciones") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Antes de continuar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Por favor revisá nuestros términos y condiciones de uso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        append("He leído los ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append("términos y condiciones")
                        }
                    },
                    modifier = Modifier.clickable { mostrarPolitica = true },
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    scope.launch {
                        guardando = true
                        authManager.aceptarTerminos()
                        guardando = false
                        onTerminosAceptados()
                    }
                },
                enabled = checked && !guardando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continuar", fontSize = 16.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Pantalla 2: texto completo de la política de privacidad
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliticaPrivacidadScreen(onVolver: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Política de privacidad") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TextoPolitica()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TextoPolitica() {
    val titulo = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val cuerpo = MaterialTheme.typography.bodyMedium

    @Composable
    fun Titulo(text: String) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(text, style = titulo)
        Spacer(modifier = Modifier.height(6.dp))
    }

    @Composable
    fun Parrafo(text: String) {
        Text(text, style = cuerpo)
        Spacer(modifier = Modifier.height(4.dp))
    }

    Text(
        "Política de Privacidad y Términos de Uso — Huka",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text("Última actualización: junio de 2025", style = cuerpo,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    Titulo("1. Qué datos recopilamos")
    Parrafo("Huka recopila únicamente los datos necesarios para el funcionamiento de la aplicación:")
    Parrafo("• Nombre y correo electrónico (mediante inicio de sesión con Google).")
    Parrafo("• Información sobre las jornadas de pesca que el usuario ingresa manualmente: fecha, especie, cantidad, peso, lugar y condiciones.")
    Parrafo("• Ubicación GPS del dispositivo, exclusivamente cuando el usuario realiza un reporte activo y otorga permiso explícito.")

    Titulo("2. Para qué usamos tus datos")
    Parrafo("Los datos recopilados se utilizan para:")
    Parrafo("• Mostrar al pescador su historial de partes (reportes de jornada).")
    Parrafo("• Generar estadísticas de capturas individuales y grupales.")
    Parrafo("• Registrar ubicaciones geográficas asociadas a las jornadas de pesca.")
    Parrafo("• Mejorar la experiencia de uso de la aplicación.")
    Parrafo("No utilizamos tus datos con fines publicitarios ni los vendemos a terceros.")

    Titulo("3. Quién accede a tus datos")
    Parrafo("• Vos: tenés acceso completo a tus propios reportes.")
    Parrafo("• Tu grupo (si pertenecés a uno): los miembros del grupo pueden ver los reportes compartidos dentro del mismo.")
    Parrafo("• El equipo de Huka: accede únicamente para tareas de soporte técnico o mejora de la aplicación, bajo estricta confidencialidad.")
    Parrafo("• Firebase (Google): actúa como proveedor de infraestructura (base de datos y autenticación). Sus políticas de privacidad aplican de forma complementaria.")
    Parrafo("No compartimos tus datos personales con terceros fuera de los mencionados.")

    Titulo("4. Uso de la ubicación")
    Parrafo("El acceso a la ubicación GPS es opcional. Solo se activa cuando el usuario decide registrar la posición de una jornada de pesca. Podés denegar este permiso sin afectar el resto de las funciones de la aplicación.")

    Titulo("5. Seguridad")
    Parrafo("Los datos se almacenan en Firebase Firestore, con acceso restringido mediante reglas de seguridad. Solo el propio usuario y los miembros de su grupo pueden leer sus reportes.")

    Titulo("6. Tus derechos (Ley 25.326 — Argentina)")
    Parrafo("De acuerdo a la Ley de Protección de Datos Personales (N.º 25.326), tenés derecho a:")
    Parrafo("• Acceder a los datos que tenemos sobre vos.")
    Parrafo("• Solicitar la rectificación de datos incorrectos.")
    Parrafo("• Solicitar la eliminación de tu cuenta y todos tus datos.")
    Parrafo("Para ejercer estos derechos, contactanos desde la sección de Perfil dentro de la aplicación.")

    Titulo("7. Edad mínima")
    Parrafo("Huka está destinada a personas mayores de 13 años. No recopilamos intencionalmente datos de menores de esa edad.")

    Titulo("8. Cambios en esta política")
    Parrafo("Cualquier modificación en esta política será notificada dentro de la aplicación. El uso continuado de Huka implica la aceptación de los cambios.")
}
