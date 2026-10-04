package com.binc.gastapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.binc.gastapp.data.prefs.ThemeMode
import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.sync.SyncScheduler
import com.binc.gastapp.ui.GastappApp
import com.binc.gastapp.ui.format.AppLocale
import com.binc.gastapp.ui.session.SessionViewModel
import com.binc.gastapp.ui.settings.AppearanceViewModel
import com.binc.gastapp.ui.theme.GastappTheme
import com.binc.gastapp.ui.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    private val sessionViewModel: SessionViewModel by viewModels()
    private val appearanceViewModel: AppearanceViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Antes de super.onCreate: cambia del tema del splash al de la app al terminar.
        // El splash se queda hasta saber si hay sesion (lectura de Room y DataStore) y
        // que tema eligio el usuario.
        installSplashScreen().setKeepOnScreenCondition {
            sessionViewModel.state.value == null || appearanceViewModel.appearance.value == null
        }
        // Barras del sistema transparentes; los iconos se ajustan abajo al tema elegido.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Elegir otro idioma para la app (Android 13+) recrea la actividad sin pasar por
        // la Application: aqui se toma el idioma con que se van a formatear las fechas.
        AppLocale.update(this)

        if (savedInstanceState == null) {
            startupSync()
            // CheckForAppUpdate de MAUI: si hay version nueva, la raiz muestra el dialogo.
            updateViewModel.checkOnStart()
        }

        setContent {
            val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
            val darkTheme = when (appearance?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }

            // Iconos claros u oscuros en las barras segun el tema de la app, no el del sistema.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose { }
            }

            GastappTheme(darkTheme = darkTheme, dynamicColor = appearance?.dynamicColor == true) {
                GastappApp(sessionViewModel, updateViewModel)
            }
        }
    }

    /**
     * CheckUser de App.xaml.cs: al abrir la app con sesion vigente se refresca el token,
     * se sube lo pendiente y se bajan los gastos de otros dispositivos, sin bloquear la
     * UI. Sin red, WorkManager espera a que la haya. Con el token vencido no se llama al
     * API: se entra con los datos locales (el aviso lo muestra Resumen).
     */
    private fun startupSync() {
        lifecycleScope.launch {
            if (sessionViewModel.state.filterNotNull().first() is SessionState.Active) syncScheduler.requestFullSync()
        }
    }

    private companion object {
        // Los velos de la barra de 3 botones que usa enableEdgeToEdge() por defecto.
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
