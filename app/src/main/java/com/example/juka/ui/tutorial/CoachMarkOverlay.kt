package com.example.juka.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Capa de tutorial que oscurece la pantalla y deja visible únicamente
 * el elemento que se está explicando.
 *
 * [target] debe estar expresado en coordenadas de ventana
 * (LayoutCoordinates.boundsInWindow()). El overlay convierte esas
 * coordenadas a su sistema local antes de dibujar el recorte.
 */
@Composable
fun CoachMarkOverlay(
    target: Rect,
    title: String,
    description: String,
    stepLabel: String,
    nextLabel: String = "Siguiente",
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .onGloballyPositioned {
                overlayOriginInWindow = it.positionInWindow()
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))

            val padding = 10.dp.toPx()
            val corner = 18.dp.toPx()

            // El target llega en coordenadas absolutas de ventana. El Canvas,
            // en cambio, dibuja en coordenadas locales del overlay. Restar el
            // origen del overlay evita desplazamientos por status bar, Scaffold,
            // drawer, insets o diferencias entre dispositivos.
            val localLeft = target.left - overlayOriginInWindow.x
            val localTop = target.top - overlayOriginInWindow.y
            val localRight = target.right - overlayOriginInWindow.x
            val localBottom = target.bottom - overlayOriginInWindow.y

            val left = (localLeft - padding).coerceAtLeast(0f)
            val top = (localTop - padding).coerceAtLeast(0f)
            val right = (localRight + padding).coerceAtMost(size.width)
            val bottom = (localBottom + padding).coerceAtMost(size.height)

            val holeSize = Size(
                width = (right - left).coerceAtLeast(1f),
                height = (bottom - top).coerceAtLeast(1f)
            )

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = holeSize,
                cornerRadius = CornerRadius(corner, corner),
                blendMode = BlendMode.Clear
            )

            drawRoundRect(
                color = highlightColor,
                topLeft = Offset(left, top),
                size = holeSize,
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Captura los toques para que el usuario no active accidentalmente
        // elementos que quedan detrás del tutorial.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {}
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(18.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSkip) {
                        Text("Omitir")
                    }
                    Button(onClick = onNext) {
                        Text(nextLabel)
                    }
                }
            }
        }
    }
}
