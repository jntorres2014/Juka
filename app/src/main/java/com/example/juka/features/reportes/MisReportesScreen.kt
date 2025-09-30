package com.example.juka.features.reportes

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.juka.common.di.ViewModelFactory

@Composable
fun MisReportesScreen(
    navController: NavController,
    viewModelFactory: ViewModelFactory
) {
    val viewModel: PartesConChatViewModel = viewModel(factory = viewModelFactory)
    // Aca iria el contenido de la UI de la pantalla de mis reportes
}
