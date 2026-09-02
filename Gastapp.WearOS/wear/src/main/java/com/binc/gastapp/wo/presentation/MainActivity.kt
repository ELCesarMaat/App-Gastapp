package com.binc.gastapp.wo.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.TimeText
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.presentation.theme.GastappTheme
import com.binc.gastapp.wo.sync.SyncWorker
import com.binc.gastapp.wo.ui.home.HomeScreen
import com.binc.gastapp.wo.ui.home.HomeState
import com.binc.gastapp.wo.ui.pairing.PairingScreen
import com.binc.gastapp.wo.ui.pairing.PairingState
import com.binc.gastapp.wo.ui.pairing.PairingViewModel
import com.binc.gastapp.wo.ui.quickadd.QuickAddActivity

class MainActivity : ComponentActivity() {

    private val pairingViewModel: PairingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val app = application as GastappApp
        SyncWorker.schedulePeriodic(this)

        setContent {
            GastappTheme {
                val sesionActiva by app.sessionActive.collectAsStateWithLifecycle()
                val pairingState by pairingViewModel.state.collectAsStateWithLifecycle()

                // null mientras se averigua si hay sesion guardada.
                var tieneSesion by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(sesionActiva, pairingState) {
                    tieneSesion = app.pairingRepository.hasSession() && sesionActiva
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    TimeText()

                    when (tieneSesion) {
                        null -> Unit
                        true -> HomeRoute(app)
                        false -> {
                            LaunchedEffect(Unit) { pairingViewModel.start() }
                            PairingScreen(
                                state = pairingState,
                                onRequestCode = pairingViewModel::requestCode
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pairingViewModel.resumePolling()
    }

    /**
     * Con la pantalla apagada no tiene sentido seguir sondeando: solo gasta bateria.
     */
    override fun onStop() {
        pairingViewModel.pausePolling()
        super.onStop()
    }

    @Composable
    private fun HomeRoute(app: GastappApp) {
        var estado by remember { mutableStateOf(HomeState()) }

        LaunchedEffect(Unit) {
            val resumen = app.repository.cachedSummary()
            estado = HomeState(
                total = resumen?.total ?: 0.0,
                count = resumen?.count ?: 0,
                pending = app.repository.pendingCount()
            )
        }

        HomeScreen(
            state = estado,
            onAddExpense = {
                startActivity(Intent(this@MainActivity, QuickAddActivity::class.java))
            }
        )
    }
}
