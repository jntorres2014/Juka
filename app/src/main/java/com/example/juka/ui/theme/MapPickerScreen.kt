package com.example.juka.ui.theme

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.juka.HukaApplication
import com.example.juka.LocationDetector
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Pantalla modal para seleccionar una ubicación en el mapa. Tiene dos
 * acciones flotantes:
 *
 *  - Mini-FAB "Mi ubicación actual" (ícono target): pide permisos runtime
 *    si hace falta, obtiene las coordenadas vía LocationDetector, y anima
 *    el mapa hasta ese punto con un zoom local (15). Si falla (sin GPS,
 *    sin permisos, sin último fix), muestra un snackbar explicativo.
 *
 *  - FAB principal "Confirmar" (check): devuelve la ubicación que está en
 *    el centro de la pantalla al caller.
 *
 * El marcador es fijo en el centro de la pantalla — el mapa se mueve por
 * debajo. Es el mismo patrón que usan Uber/iFood y se siente más natural
 * que arrastrar un pin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    onDismiss: () -> Unit,
    onLocationSelected: (lat: Double, lon: Double, name: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLocation by remember { mutableStateOf(GeoPoint(-40.784, -65.035)) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Guardamos referencia al MapView para poder mover el controller desde
    // afuera del factory (cuando el usuario toca "Mi ubicación").
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var isConfirming by remember { mutableStateOf(false) }

    // Nombre detectado para mostrar en el chip arriba del mapa.
    // Se setea cuando: (a) usuario apretó "Mi ubicación" y el geocoder
    // devolvió algo, o (b) cuando va a confirmar y geocodificamos el punto.
    var nombreDetectado by remember { mutableStateOf<String?>(null) }
    // Punto desde donde se generó `nombreDetectado`. Si el usuario mueve
    // el mapa más de ~1km de acá, limpiamos el chip — el nombre ya no es
    // confiable para el centro actual.
    var puntoDetectado by remember { mutableStateOf<GeoPoint?>(null) }

    // Detector reusable — instancia única para esta sesión del Dialog.
    val locationDetector = remember {
        LocationDetector(context.applicationContext as HukaApplication)
    }

    // Función que coordina la obtención de ubicación + animación del mapa.
    // Se invoca después de tener permisos garantizados.
    val centrarEnMiUbicacion: () -> Unit = {
        scope.launch {
            isLoadingLocation = true
            val latLon = locationDetector.obtenerLatLonActual()
            if (latLon != null) {
                val (lat, lon) = latLon
                val destino = GeoPoint(lat, lon)
                selectedLocation = destino
                mapView?.controller?.animateTo(destino, 15.0, 1200L)
                // Reverse geocoding en paralelo — mostramos el chip apenas
                // tengamos el nombre. Si falla, simplemente no aparece chip.
                val nombre = locationDetector.obtenerNombreLugar(lat, lon)
                if (nombre != null) {
                    nombreDetectado = nombre
                    puntoDetectado = destino
                }
            } else {
                snackbarHostState.showSnackbar(
                    "No pudimos obtener tu ubicación. Activá el GPS y los permisos."
                )
            }
            isLoadingLocation = false
        }
    }

    // Cuando el usuario mueve el mapa, chequeamos si se alejó del punto
    // donde detectamos el nombre. Si sí, limpiamos el chip — ya no aplica.
    LaunchedEffect(selectedLocation) {
        val detected = puntoDetectado ?: return@LaunchedEffect
        // distanceToAsDouble devuelve metros. >1500m → cleanup
        if (selectedLocation.distanceToAsDouble(detected) > 1500.0) {
            nombreDetectado = null
            puntoDetectado = null
        }
    }

    // Launcher para pedir permiso de ubicación runtime. La política Android 6+
    // requiere pedir aunque esté declarado en el manifest.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            centrarEnMiUbicacion()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Sin permiso de ubicación no podemos centrarte en el mapa."
                )
            }
        }
    }

    // Click del mini-FAB: chequear permisos primero, después centrar.
    val onMyLocationClick: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) centrarEnMiUbicacion()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Configurar osmdroid una sola vez
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
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                // Column con dos FABs apilados — el mini arriba (acción
                // secundaria), el grande abajo (acción principal). Layout
                // recomendado por Material 3 cuando hay dos FABs juntos.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { if (!isLoadingLocation) onMyLocationClick() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (isLoadingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "Centrar en mi ubicación actual"
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isConfirming) return@FloatingActionButton
                            scope.launch {
                                isConfirming = true
                                // Si el chip todavía está visible significa
                                // que el usuario está cerca del punto donde
                                // detectamos el nombre — reusamos ese. Si
                                // movió el mapa, geocodificamos el centro
                                // actual antes de devolver.
                                val nombre = nombreDetectado
                                    ?: locationDetector.obtenerNombreLugar(
                                        selectedLocation.latitude,
                                        selectedLocation.longitude
                                    )
                                    ?: "Ubicación seleccionada"
                                onLocationSelected(
                                    selectedLocation.latitude,
                                    selectedLocation.longitude,
                                    nombre
                                )
                                isConfirming = false
                                onDismiss()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        if (isConfirming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Confirmar")
                        }
                    }
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
                        }.also { mv -> mapView = mv }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { mapViewInstance ->
                    // Actualizar ubicación cuando el mapa se mueve. También
                    // refrescamos la referencia por si la primera vez no se
                    // tomó (shouldn't happen, pero por las dudas).
                    selectedLocation = mapViewInstance.mapCenter as GeoPoint
                    mapView = mapViewInstance
                }

                // Marcador fijo en el centro de la pantalla
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

                // Chip flotante con el nombre detectado (provincia/ciudad o
                // zona conocida). Aparece cuando se centra en "Mi ubicación"
                // y el geocoder devolvió algo, y se oculta si el usuario
                // se aleja significativamente del punto detectado.
                val nombre = nombreDetectado
                if (nombre != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = nombre,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
