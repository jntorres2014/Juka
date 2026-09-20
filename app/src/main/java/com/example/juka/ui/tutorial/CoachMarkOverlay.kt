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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Capa de tutorial que oscurece la pantalla y deja visible únicamente
 * el elemento que se está explicando.
 *
 * [target] debe estar expresado en coordenadas del root (por ejemplo,
 * usando LayoutCoordinates.boundsInRoot()).
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
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
            val left = (target.left - padding).coerceAtLeast(0f)
            val top = (target.top - padding).coerceAtLeast(0f)
            val right = (target.right + padding).coerceAtMost(size.width)
            val bottom = (target.bottom + padding).coerceAtMost(size.height)

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
