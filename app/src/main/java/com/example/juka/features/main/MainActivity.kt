package com.example.juka.features.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.juka.features.main.navigation.AppNavigation
import com.example.juka.ui.theme.JukaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JukaTheme {
                AppNavigation()
            }
        }
    }
}
