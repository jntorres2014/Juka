package com.example.juka.ui.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.data.network.NetworkMonitor
import kotlinx.coroutines.delay

/**
 * Banner reactivo que aparece en la parte superior cuando no hay
 * conectividad, y muestra brevemente "Conexión restablecida" cuando
 * vuelve. Usado a nivel raíz para que cubra todas las pantallas.
 *
 * Estados visuales:
 *  - Sin conexión: barra roja con ícono `CloudOff` que se queda visible
 *    mientras dure el offline.
 *  - Conexión restablecida: barra verde por 2.5s y se va sola.
 */
@Composable
fun NetworkBanner(monitor: NetworkMonitor) {
    val isOnline by monitor.isOnline.collectAsState()

    // Estado interno: cuando volvemos a tener red, mostramos el verde
    // por unos segundos antes de ocultarlo.
    var mostrarRestablecido by remember { mutableStateOf(false) }
    var primerEstado by remember { mutableStateOf(true) }

    LaunchedEffect(isOnline) {
        if (primerEstado) {
            // No mostramos "restablecido" al inicio de la app aunque
            // isOnline == true: sólo cuando hay una transición real
            // offline -> online.
            primerEstado = false
            return@LaunchedEffect
        }
        if (isOnline) {
            mostrarRestablecido = true
            delay(2_500)
            mostrarRestablecido = false
        }
    }

    // Mostrar offline (prioridad) o "restablecido" temporal
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        BannerContent(
            bg = Color(0xFFD93636),
            icon = Icons.Default.CloudOff,
            text = "Sin conexión a internet"
        )
    }

    AnimatedVisibility(
        visible = isOnline && mostrarRestablecido,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        BannerContent(
            bg = Color(0xFF1D9E75),
            icon = Icons.Default.CheckCircle,
            text = "Conexión restablecida"
        )
    }
}

@Composable
private fun BannerContent(bg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
