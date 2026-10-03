package com.binc.gastapp.demo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.binc.gastapp.demo.ui.GastappDemoApp
import com.binc.gastapp.demo.datos.LocaleMx
import com.binc.gastapp.demo.ui.pantallas.PreferenciaTema
import com.binc.gastapp.demo.ui.theme.GastappTheme
import java.util.Locale

/**
 * Extras para abrir cualquier estado directo desde adb y tomar capturas sin navegar
 * a toques:
 *
 *   adb shell am start -S -n com.binc.gastapp.demo/.MainActivity \
 *       --es pantalla tarjetas --es tema oscuro --ez hoja true --ez editar true --ez dinamico true
 *
 * pantalla: resumen | ahorros | perfil | ajustes | tarjetas | suscripciones | periodo
 */
class MainActivity : ComponentActivity() {

    // La app real seguiria el idioma del telefono; el demo se fija en es-MX para que
    // el selector de fechas y los formatos salgan en espanol aunque el emulador este en ingles.
    override fun attachBaseContext(newBase: Context) {
        Locale.setDefault(LocaleMx)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(LocaleMx) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pantalla = intent.getStringExtra("pantalla") ?: "resumen"
        val hoja = intent.getBooleanExtra("hoja", false)
        val editar = intent.getBooleanExtra("editar", false)
        val temaInicial = when (intent.getStringExtra("tema")) {
            "oscuro" -> PreferenciaTema.Oscuro
            "claro" -> PreferenciaTema.Claro
            else -> PreferenciaTema.Sistema
        }
        val dinamicoInicial = intent.getBooleanExtra("dinamico", false)

        setContent {
            var tema by remember { mutableStateOf(temaInicial) }
            var dinamico by remember { mutableStateOf(dinamicoInicial) }
            val oscuro = when (tema) {
                PreferenciaTema.Oscuro -> true
                PreferenciaTema.Claro -> false
                PreferenciaTema.Sistema -> isSystemInDarkTheme()
            }

            // Barras del sistema transparentes (edge-to-edge) con iconos del color correcto.
            DisposableEffect(oscuro) {
                val estilo = if (oscuro) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose {}
            }

            GastappTheme(oscuro = oscuro, dinamico = dinamico) {
                GastappDemoApp(
                    pantallaInicial = pantalla,
                    hojaInicial = hoja,
                    editarInicial = editar,
                    tema = tema,
                    onTema = { tema = it },
                    dinamico = dinamico,
                    onDinamico = { dinamico = it },
                )
            }
        }
    }
}
