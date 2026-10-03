package com.binc.gastapp.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.binc.gastapp.ui.main.MainScreen
import com.binc.gastapp.ui.main.StackedPlaceholderScreen
import com.binc.gastapp.ui.navigation.CardsRoute
import com.binc.gastapp.ui.navigation.ExplorePeriodRoute
import com.binc.gastapp.ui.navigation.MainRoute
import com.binc.gastapp.ui.navigation.PredictiveBackLayer
import com.binc.gastapp.ui.navigation.ProvideBackGesture
import com.binc.gastapp.ui.navigation.SubscriptionsRoute
import com.binc.gastapp.ui.navigation.entersFromBottom
import com.binc.gastapp.ui.navigation.stackedBaseExit
import com.binc.gastapp.ui.navigation.stackedBaseReturn

/**
 * Raiz de la app: las pestanas (MainRoute) y, encima, las pantallas apiladas que se
 * cierran con el atras predictivo. El flujo de sesion (inicio, login, registro) se
 * agrega aqui en la Fase 4.1.
 */
@Composable
fun GastappApp() {
    val navController = rememberNavController()

    ProvideBackGesture {
        NavHost(
            navController = navController,
            startDestination = MainRoute,
            // Las apiladas animan su propia entrada y salida (PredictiveBackLayer).
            enterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            // La de abajo se hace a un lado, salvo bajo las que entran desde abajo.
            exitTransition = {
                if (targetState.entersFromBottom) ExitTransition.KeepUntilTransitionsFinished
                else stackedBaseExit()
            },
            popEnterTransition = {
                if (initialState.entersFromBottom) EnterTransition.None else stackedBaseReturn()
            },
        ) {
            composable<MainRoute> {
                MainScreen(
                    onOpenCards = dropUnlessResumed { navController.navigate(CardsRoute) },
                    onOpenSubscriptions = dropUnlessResumed { navController.navigate(SubscriptionsRoute) },
                    onExplorePeriod = dropUnlessResumed { navController.navigate(ExplorePeriodRoute) },
                )
            }

            // Pantallas provisionales para el spike del atras predictivo (0.7); cada una
            // se reemplaza por la real en su fase.
            composable<CardsRoute> { entry ->
                PredictiveBackLayer(entry, navController) {
                    StackedPlaceholderScreen(
                        title = "Mis tarjetas",
                        phase = "Fase 4.4",
                        onBack = navController.popOnce(),
                    )
                }
            }
            composable<SubscriptionsRoute> { entry ->
                PredictiveBackLayer(entry, navController) {
                    StackedPlaceholderScreen(
                        title = "Suscripciones",
                        phase = "Fase 4.5",
                        onBack = navController.popOnce(),
                    )
                }
            }
            composable<ExplorePeriodRoute> { entry ->
                PredictiveBackLayer(entry, navController) {
                    StackedPlaceholderScreen(
                        title = "Explorar periodo",
                        phase = "Fase 4.2",
                        onBack = navController.popOnce(),
                        closeIcon = true,
                    )
                }
            }
        }
    }
}

/** Regresar ignorando toques repetidos mientras la pantalla ya va de salida. */
@Composable
private fun NavController.popOnce(): () -> Unit = dropUnlessResumed { popBackStack() }
