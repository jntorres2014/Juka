// PescadexScreen.kt - Pantalla para visualizar el Pescadex del usuario
package com.example.juka.features.pescadex

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.features.chat.ChatViewModel
import com.example.juka.features.pescadex.data.*
import kotlinx.coroutines.launch

// TODO: La dependencia directa de ChatViewModel debería ser eliminada.
// Esta pantalla no debería conocer el ViewModel del chat. Se debería pasar
// la información necesaria a través de parámetros o un ViewModel compartido.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PescadexScreen(
    chatViewModel: ChatViewModel = viewModel() // Dependencia a refactorizar
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pescadexManager = remember { PescadexManager(context) }

    var especies by remember { mutableStateOf<List<EspecieConEstado>>(emptyList()) }
    var estadisticas by remember { mutableStateOf<EstadisticasPescadex?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var especieSeleccionada by remember { mutableStateOf<EspecieConEstado?>(null) }
    var mostrarCelebracion by remember { mutableStateOf<RegistroResult.Success?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                especies = pescadexManager.obtenerEspeciesConEstado()
                estadisticas = pescadexManager.obtenerEstadisticasPescadex()
            } catch (e: Exception) {
                android.util.Log.e("PESCADEX", "Error cargando: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            estadisticas?.let { PescadexHeaderCard(it) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(especies) { especie ->
                    EspecieCard(especie, onClick = { especieSeleccionada = especie })
                }
            }
        }
    }

    especieSeleccionada?.let {
        EspecieDetalleModal(it, onDismiss = { especieSeleccionada = null }) { especieId, peso ->
            scope.launch {
                val resultado = pescadexManager.registrarEspecieCapturada(especieId, peso)
                if (resultado is RegistroResult.Success && resultado.esNuevaEspecie) {
                    mostrarCelebracion = resultado
                }
                // Recargar datos
                especies = pescadexManager.obtenerEspeciesConEstado()
                estadisticas = pescadexManager.obtenerEstadisticasPescadex()
                especieSeleccionada = null
            }
        }
    }

    mostrarCelebracion?.let {
        CelebracionNuevaEspecieModal(it, onDismiss = { mostrarCelebracion = null })
    }
}

// ... (El resto de los Composables auxiliares como PescadexHeaderCard, EspecieCard, etc., permanecen igual)
