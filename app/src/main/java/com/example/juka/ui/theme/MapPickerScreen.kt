package com.example.juka.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    onDismiss: () -> Unit,
    onLocationSelected: (lat: Double, lon: Double, name: String?) -> Unit
) {
    val context = LocalContext.current
    var selectedLocation by remember { mutableStateOf(GeoPoint(-40.784, -65.035)) }

    // Configurar osmdroid
    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Selecciona una ubicación") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        titleContentColor = MaterialTheme.colorScheme.onTertiary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onTertiary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        onLocationSelected(
                            selectedLocation.latitude,
                            selectedLocation.longitude,
                            "Ubicación seleccionada"
                        )
                        onDismiss()
                    },
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Confirmar")
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(4.0)
                            controller.setCenter(GeoPoint(-40.784, -65.035))

                            // Listener para capturar el centro del mapa
                            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                selectedLocation = mapCenter as GeoPoint
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { mapView ->
                    // Actualizar ubicación cuando el mapa se mueve
                    selectedLocation = mapView.mapCenter as GeoPoint
                }

                // Marcador fijo en el centro
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Punto de selección",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(48.dp)
                            .offset(y = (-24).dp)
                    )
                }
            }
        }
    }
}