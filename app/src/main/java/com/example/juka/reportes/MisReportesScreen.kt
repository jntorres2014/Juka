/*
package com.example.juka.reportes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.viewmodel.ChatViewModel
import com.example.juka.data.firebase.PartePesca
import kotlinx.coroutines.launch



// Función para generar imagen compartible del reporte (fuera de cualquier composable)
fun generarImagenReporte(reporte: PartePesca, context: Context): Bitmap {
    val width = 800
    val height = 1000

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Fondo degradado
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Fondo azul marino
    paint.color = Color(0xFF1E3A8A).toArgb()
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // Fondo decorativo (ondas)
    paint.color = Color(0xFF3B82F6).copy(alpha = 0.3f).toArgb()
    for (i in 0..5) {
        canvas.drawCircle(
            width * 0.8f,
            height * (0.1f + i * 0.15f),
            (100 + i * 20).toFloat(),
            paint
        )
    }

    // Texto del reporte (simplificado)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 48f
    paint.textAlign = Paint.Align.CENTER

    canvas.drawText("JORNADA DE PESCA", width / 2f, 120f, paint)

    paint.textSize = 36f
    reporte.fecha?.let { canvas.drawText(it, width / 2f, 200f, paint) }

    paint.textSize = 32f
    canvas.drawText("${reporte.horaInicio ?: "?"} - ${reporte.horaFin ?: "?"}", width / 2f, 260f, paint)

    // Estadísticas principales
    paint.textSize = 72f
    canvas.drawText("${reporte.cantidadTotal}", width / 2f, 400f, paint)

    paint.textSize = 28f
    canvas.drawText("PECES CAPTURADOS", width / 2f, 450f, paint)

    paint.textSize = 32f
    canvas.drawText("Tipo: ${reporte.tipo?.replaceFirstChar { it.uppercase() } ?: "?"}", width / 2f, 520f, paint)
    canvas.drawText("Cañas: ${reporte.numeroCanas ?: 0}", width / 2f, 570f, paint)

    // Especies
    if (reporte.peces.isNotEmpty()) {
        paint.textSize = 24f
        canvas.drawText("ESPECIES:", width / 2f, 650f, paint)

        var yPos = 690f
        reporte.peces.forEach { pez ->
            canvas.drawText("${pez.especie}: ${pez.cantidad}", width / 2f, yPos, paint)
            yPos += 35f
        }
    }

    // Footer
    paint.textSize = 20f
    paint.color = Color(0xFF94A3B8).toArgb()
    canvas.drawText("Registrado con Huka - App de Pesca", width / 2f, height - 80f, paint)

    return bitmap
}

*/
