
package com.example.juka.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.juka.data.AuthManager

@Composable
fun LoginScreen(navController: NavController) {
    val authManager = AuthManager()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            // TODO: Implement Google Sign In
            val user = authManager.getCurrentUser()
            if (user != null) {
                navController.navigate("profile")
            } else {
                // Simulate a successful login for now
                navController.navigate("profile")
            }
        }) {
            Text("Sign in with Google")
        }
    }
}
