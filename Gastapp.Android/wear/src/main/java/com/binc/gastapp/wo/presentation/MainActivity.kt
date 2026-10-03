package com.binc.gastapp.wo.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.HierarchicalFocusCoordinator
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.presentation.theme.GastappTheme
import com.binc.gastapp.wo.sync.SyncWorker
import com.binc.gastapp.wo.ui.home.HomeScreen
import com.binc.gastapp.wo.ui.home.HomeViewModel
import com.binc.gastapp.wo.ui.home.OptionsScreen
import com.binc.gastapp.wo.ui.pairing.PairingScreen
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
                    // Sin sesion activa no hace falta preguntarle al disco: es que no.
                    // Ademas hasSession() lee DataStore y descifra con AndroidKeyStore,
                    // y esto corre en el hilo principal: al desvincular, la pantalla
                    // tiene que cambiar aunque el disco este ocupado.
                    tieneSesion = if (!sesionActiva) false else app.pairingRepository.hasSession()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    when (tieneSesion) {
                        null -> Unit
                        true -> HomeRoute()
                        false -> {
                            LaunchedEffect(Unit) {
                                // Tras desvincular a mano se espera al usuario en vez de
                                // pedir codigo solo: el API tarda en despertar y la
                                // pantalla se quedaba en "Preparando...".
                                if (app.justUnlinked.value) {
                                    pairingViewModel.showUnlinked()
                                } else {
                                    pairingViewModel.start()
                                }
                            }
                            val canal by pairingViewModel.channelState.collectAsStateWithLifecycle()
                            val autoPair by pairingViewModel.autoPairStatus.collectAsStateWithLifecycle()

                            Scaffold(timeText = { TimeText() }) {
                                PairingScreen(
                                    state = pairingState,
                                    channel = canal,
                                    autoPairStatus = autoPair,
                                    onRequestCode = {
                                        app.justUnlinked.value = false
                                        pairingViewModel.requestCode()
                                    },
                                    onTestChannel = pairingViewModel::testChannel
                                )
                            }
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

    /**
     * Dos paginas: la lista del dia y las opciones. El deslizamiento horizontal es el
     * gesto que ya espera cualquiera en Wear OS, asi que no hace falta ningun menu.
     */
    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    private fun HomeRoute() {
        val vm: HomeViewModel = viewModel()
        val estado by vm.state.collectAsStateWithLifecycle()

        val pagerState = rememberPagerState(pageCount = { 2 })
        val listaGastos = rememberScalingLazyListState()
        val listaOpciones = rememberScalingLazyListState()

        // En cada vuelta al frente, no solo al crear la pantalla: al regresar de
        // QuickAddActivity el gasto recien capturado ya tiene que verse.
        LifecycleResumeEffect(Unit) {
            vm.refresh()
            onPauseOrDispose { }
        }

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = {
                PositionIndicator(
                    scalingLazyListState = if (pagerState.currentPage == 0) {
                        listaGastos
                    } else {
                        listaOpciones
                    }
                )
            },
            pageIndicator = {
                HorizontalPageIndicator(pageIndicatorState = recordarIndicador(pagerState))
            }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pagina ->
                // Solo la pagina visible toma el foco, o las dos listas se pelearian
                // por los eventos de la corona.
                HierarchicalFocusCoordinator(requiresFocus = { pagerState.currentPage == pagina }) {
                    when (pagina) {
                        0 -> HomeScreen(
                            state = estado,
                            listState = listaGastos,
                            onAddExpense = {
                                startActivity(Intent(this@MainActivity, QuickAddActivity::class.java))
                            }
                        )

                        else -> OptionsScreen(
                            state = estado,
                            listState = listaOpciones,
                            onSyncNow = vm::syncNow,
                            onTestChannel = vm::testChannel,
                            onUnlink = vm::unlink
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adaptador entre el PagerState de Compose y el indicador de Wear, que espera su
 * propia interfaz. Las propiedades se leen en cada recomposicion a proposito: asi el
 * punto se mueve junto con el dedo.
 */
@Composable
private fun recordarIndicador(pagerState: PagerState): PageIndicatorState =
    remember(pagerState) {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = pagerState.pageCount
        }
    }
