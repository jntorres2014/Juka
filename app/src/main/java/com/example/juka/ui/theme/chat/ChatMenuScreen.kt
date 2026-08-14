package com.example.juka.ui.theme.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.ui.notificaciones.CampanaIcon
import com.google.firebase.auth.FirebaseUser

/**
 * Pantalla de entrada al chat.
 *
 * Antes, el "menú" (Consultar a Huka / Info de mareas / Info adicional) era
 * el primer mensaje del bot DENTRO del chat — mostrado como burbuja con
 * botones — mientras el campo de texto para escribir libremente ya estaba
 * visible al mismo tiempo. Eso generaba confusión: no quedaba claro si había
 * que tocar un botón o directamente escribir.
 *
 * Ahora el menú es una pantalla propia con tarjetas tocables. Solo al elegir
 * "Hacer una consulta" se entra al chat libre (EnhancedChatScreen), que
 * arranca directamente con el input visible y sin menú superpuesto.
 */
@Composable
fun ChatMenuScreen(
    user: FirebaseUser,
    onConsultar: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onOpenNotificaciones: () -> Unit = {}
) {
    val context = LocalContext.current
    var mostrarInfoAdicional by rememberSaveable { mutableStateOf(false) }

    fun abrirLink(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // Sin navegador disponible u otro error: no hay mucho más que
            // hacer acá, la app sigue funcionando igual.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.primary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Abrir menú",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Chat Huka",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "Hola ${user.displayName?.split(" ")?.firstOrNull() ?: "Pescador"}, elegí qué necesitás",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
                CampanaIcon(onClick = onOpenNotificaciones, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MenuOptionCard(
                icon = Icons.Default.Chat,
                titulo = "Hacer una consulta",
                subtitulo = "Preguntale lo que quieras a Huka",
                destacado = true,
                onClick = onConsultar
            )
            MenuOptionCard(
                icon = Icons.Default.Waves,
                titulo = "Información de mareas",
                subtitulo = "Abrí la tabla oficial",
                trailingIcon = Icons.Default.OpenInNew,
                onClick = { abrirLink("https://www.hidro.gov.ar/oceanografia/tmareas/form_tmareas.asp") }
            )
            MenuOptionCard(
                icon = Icons.Default.Info,
                titulo = "Info adicional",
                subtitulo = "Especies, vedas, permisos y más",
                trailingIcon = if (mostrarInfoAdicional) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                onClick = { mostrarInfoAdicional = !mostrarInfoAdicional }
            )

            AnimatedVisibility(
                visible = mostrarInfoAdicional,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InfoLinkRow("Guía de especies") { abrirLink("https://www.pescaargentina.com.ar/contenidos/especies-argentinas") }
                    InfoLinkRow("Viento") { abrirLink("https://www.windguru.cz/53") }
                    InfoLinkRow("Vedas vigentes") { abrirLink("https://www.argentina.gob.ar/inidep/areas-de-veda") }
                    InfoLinkRow("Permisos y reglamentos") { abrirLink("https://www.pescaargentina.com.ar/reglamento-licencia-pesca-deportiva") }
                    InfoLinkRow("Calendario lunar") { abrirLink("https://www.pescaargentina.com.ar/fases-lunares/") }
                }
            }
        }
    }
}

@Composable
private fun MenuOptionCard(
    icon: ImageVector,
    titulo: String,
    subtitulo: String,
    destacado: Boolean = false,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (destacado) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destacado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titulo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destacado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitulo,
                    fontSize = 11.sp,
                    color = if (destacado) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = if (destacado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoLinkRow(texto: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(texto, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}
