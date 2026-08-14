package com.example.juka.ui.notificaciones

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.viewmodel.AppViewModelProvider
import com.example.juka.viewmodel.NotificacionesViewModel

/**
 * Ícono de campana 🔔 con badge de no-leídas. Se usa en el TopAppBar global
 * y en el header del chat. Tap → llama `onClick` (típicamente navega a la
 * pantalla de notificaciones).
 *
 * El conteo se obtiene del `NotificacionesViewModel` que es shared scope
 * de la app, así varios usos de la campana ven el mismo estado.
 *
 * @param tint Color del ícono — se pasa porque el chat tiene fondo verde
 *             (necesita ícono blanco) y el TopAppBar genérico usa el
 *             color por defecto.
 */
@Composable
fun CampanaIcon(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    viewModel: NotificacionesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val unread by viewModel.unreadCount.collectAsState()

    // Refrescamos cada vez que la campana se compone — barato y mantiene
    // el badge al día sin necesidad de un listener permanente.
    LaunchedEffect(Unit) {
        viewModel.recargar()
    }

    Box {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Notificaciones",
                tint = tint
            )
        }
        if (unread > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFE53935),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(if (unread > 9) 18.dp else 16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (unread > 9) "9+" else "$unread",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
