// PescadexScreen.kt - Pantalla integrada con tu diseño
package com.example.juka

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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PescadexScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pescadexManager = remember { PescadexManager(context) }
    
    var especies by remember { mutableStateOf<List<EspecieConEstado>>(emptyList()) }
    var estadisticas by remember { mutableStateOf<EstadisticasPescadex?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var especieSeleccionada by remember { mutableStateOf<EspecieConEstado?>(null) }
    var mostrarCelebracion by remember { mutableStateOf<RegistroResult.Success?>(null) }
    
    // Cargar datos al iniciar
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando tu Pescadex...")
                }
            }
        } else {
            // Header con estadísticas
            estadisticas?.let { stats ->
                PescadexHeaderCard(estadisticas = stats)
            }
            
            // Grid de especies
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(especies) { especieConEstado ->
                    EspecieCard(
                        especieConEstado = especieConEstado,
                        onClick = { especieSeleccionada = especieConEstado }
                    )
                }
            }
        }
    }
    
    // Modal de detalles de especie
    especieSeleccionada?.let { especie ->
        EspecieDetalleModal(
            especieConEstado = especie,
            onDismiss = { especieSeleccionada = null },
            onRegistrarCaptura = { especieId, peso ->
                scope.launch {
                    val resultado = pescadexManager.registrarEspecieCapturada(
                        especieId = especieId,
                        peso = peso
                    )
                    
                    when (resultado) {
                        is RegistroResult.Success -> {
                            if (resultado.esNuevaEspecie) {
                                mostrarCelebracion = resultado
                            }
                            // Recargar datos
                            especies = pescadexManager.obtenerEspeciesConEstado()
                            estadisticas = pescadexManager.obtenerEstadisticasPescadex()
                        }
                        is RegistroResult.Error -> {
                            //