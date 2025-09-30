package com.example.juka.features.main.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.juka.common.di.ViewModelFactory
import com.example.juka.core.nlp.MLKitManager
import com.example.juka.data.firebase.FirebaseManager
import com.example.juka.database.FishDatabase
import com.example.juka.features.auth.LoginScreen
import com.example.juka.features.auth.SimpleProfileScreen
import com.example.juka.features.chat.ChatScreen
import com.example.juka.features.chat.ChatViewModel
import com.example.juka.features.identificar.IdentificarPezScreen
import com.example.juka.features.reportes.MisReportesScreen
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val firebaseManager = FirebaseManager(context)
    val fishDatabase = FishDatabase.getDatabase(context).fishDao()
    val mlKitManager = MLKitManager(context, fishDatabase)
    val viewModelFactory = ViewModelFactory(context, firebaseManager, mlKitManager)
    val user = Firebase.auth.currentUser

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Profile.route) {
            SimpleProfileScreen(navController = navController)
        }
        composable(Screen.IdentificarPez.route) {
            IdentificarPezScreen(navController = navController)
        }
        composable(Screen.MisReportes.route) {
            MisReportesScreen(navController = navController, viewModelFactory = viewModelFactory)
        }
        composable(Screen.Chat.route) {
            if (user != null) {
                val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
                ChatScreen(user = user, viewModel = chatViewModel)
            }
        }
    }
}
