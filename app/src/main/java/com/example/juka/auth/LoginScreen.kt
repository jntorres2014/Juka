package com.example.juka.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.juka.R
import com.example.juka.data.AuthManager
import com.example.juka.data.AuthState
import com.example.juka.viewmodel.AppViewModelProvider
import com.example.juka.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio de sesión de Huka.
 *
 * Diseño:
 *  - Fondo con gradient vertical desde el `primary` del theme hacia un tono más
 *    profundo, para tener identidad propia pero seguir respetando el theme de
 *    la app (no usamos colores hardcoded como antes).
 *  - Card central con el logo de Huka, tagline pesca-friendly y el botón de
 *    Google styled (la "G" suelta y feota de antes la reemplazamos por una
 *    insignia circular).
 *  - El logo de Huka se mantiene tal cual (`R.drawable.logohuka1`), como pidió
 *    el usuario.
 */
@Composable
fun LoginScreen(
    authManager: AuthManager,
    navController: NavController,
    loginViewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    // Scope local solo para abrir el diálogo de Google. El resultado y la
    // navegación los maneja AppWithAuth observando authState.
    val scope = rememberCoroutineScope()

    val authState by authManager.authState.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = true
        loginViewModel.handleSignInResult(result.data)
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Loading -> {
                isLoading = true
                errorMessage = null
            }
            is AuthState.Error -> {
                isLoading = false
                errorMessage = (authState as AuthState.Error).message
            }
            else -> {
                // Authenticated o Unauthenticated: la navegación la dispara
                // AppWithAuth desde su LaunchedEffect(authState).
                isLoading = false
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            colors.primary,
            colors.primaryContainer.copy(alpha = 0.85f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ───── Logo ─────
                // Mantenemos el logo original (logohuka1). El tamaño y el
                // tratamiento sí cambian: lo metemos en un círculo con un
                // halo sutil del primaryContainer para anclarlo visualmente.
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logohuka1),
                        contentDescription = "Logo Huka",
                        modifier = Modifier.size(120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ───── Marca y tagline ─────
                Text(
                    text = "Huka",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tu compañera de pesca",
                    fontSize = 14.sp,
                    color = colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ───── Card de error (si aplica) ─────
                errorMessage?.let { error ->
                    Surface(
                        color = colors.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = colors.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No pudimos iniciar sesión",
                                    color = colors.onErrorContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = error,
                                    color = colors.onErrorContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ───── Botón Google ─────
                // Reemplazamos la "G" suelta por una insignia circular blanca
                // con la G en los colores de Google. Sigue siendo simple
                // (sin asset) pero se reconoce inmediatamente como Google
                // Sign-In.
                Button(
                    onClick = {
                        Log.d("LOGIN_DEBUG", "Botón Google presionado")
                        scope.launch {
                            try {
                                isLoading = true
                                errorMessage = null

                                val intent = authManager.getSignInIntent()
                                if (intent != null) {
                                    signInLauncher.launch(intent)
                                } else {
                                    isLoading = false
                                    errorMessage = "Error configurando login"
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GoogleBadge()
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Continuar con Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Iniciá sesión para guardar tus partes,\nlogros y récords personales 🎣",
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Insignia circular blanca con la "G" en los colores de Google. Reemplaza
 * a la "G" suelta que tenía el botón antes. Sin asset externo — todo Compose.
 *
 * Los colores son los oficiales de Google Sign-In (azul, rojo, amarillo,
 * verde) usados como acento del trazado de la G. Para mantenerlo simple,
 * acá usamos el azul Google (#4285F4) como color principal de la letra.
 */
@Composable
private fun GoogleBadge() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4285F4) // Google Blue
        )
    }
}
