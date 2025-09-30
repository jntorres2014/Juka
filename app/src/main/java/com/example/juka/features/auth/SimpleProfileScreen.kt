
package com.example.juka.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun SimpleProfileScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profile Screen")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("identificar_pez") }) {
            Text("Identificar Pez")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { navController.navigate("mis_reportes") }) {
            Text("Mis Reportes")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { navController.navigate("chat") }) {
            Text("Chat")
        }
    }
}
