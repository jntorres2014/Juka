package com.example.juka.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.AssistChipDefaults  // Para colores, si necesitas custom
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip  // Usa este en lugar de Chip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juka.domain.chat.QuotaState

@Composable
fun QuotaIndicator(
    quotaState: QuotaState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = when {
            quotaState.remaining > 3 -> Color(0xFF4CAF50)
            quotaState.remaining > 0 -> Color(0xFFFF9800)
            else -> Color(0xFFFF5722)
        }.copy(alpha = 0.15f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.QuestionAnswer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Consultas con Huka",
                    fontSize = 13.sp
                )
            }

            SuggestionChip(  // Reemplaza Chip por SuggestionChip
                onClick = { /* Puedes dejar vacío o agregar lógica si quieres */ },
                label = {
                    Text(
                        text = "${quotaState.remaining}/${quotaState.total}",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = when {
                        quotaState.remaining > 3 -> Color(0xFF4CAF50)
                        quotaState.remaining > 0 -> Color(0xFFFF9800)
                        else -> Color(0xFFFF5722)
                    }
                )
            )
        }
    }
}