package com.binc.gastapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.binc.gastapp.ui.GastappApp
import com.binc.gastapp.ui.theme.GastappTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Antes de super.onCreate: cambia del tema del splash al de la app al terminar.
        installSplashScreen()
        // Barras del sistema transparentes; los iconos siguen al modo claro u oscuro.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            GastappTheme {
                GastappApp()
            }
        }
    }
}
