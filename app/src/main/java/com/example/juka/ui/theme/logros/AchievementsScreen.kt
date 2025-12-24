package com.example.juka.ui.theme.logros

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juka.data.Achievement
import com.example.juka.data.AchievementsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = viewModel()) {
    val context = LocalContext.current // Obtenemos el contexto de Android
    val achievements by viewModel.uiState.collectAsState()
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mis Logros Huka") })
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), // 3 columnas como en tu foto
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5)), // Un fondo gris clarito rústico
            contentPadding = PaddingValues(16.dp)
        ) {
            items(achievements) { achievement ->
                AchievementItem(
                    achievement = achievement,
                    onItemClick = {
                        selectedAchievement = achievement
            }
                )
            }
        }
        selectedAchievement?.let { achievement ->
            AchievementDetailDialog(
                achievement = achievement,
                onDismiss = { selectedAchievement = null },
                onShare = { ach ->
                    viewModel.shareAchievement(context, ach)
                }
            )
        }
    }
}