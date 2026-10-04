package com.binc.gastapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.ui.cards.CardFormScreen
import com.binc.gastapp.ui.cards.CardsScreen
import com.binc.gastapp.ui.category.CategoryDetailScreen
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.rememberAppMessages
import com.binc.gastapp.ui.explore.ExplorePeriodScreen
import com.binc.gastapp.ui.main.MainNavigation
import com.binc.gastapp.ui.main.MainScreen
import com.binc.gastapp.ui.navigation.CardFormRoute
import com.binc.gastapp.ui.navigation.CardsRoute
import com.binc.gastapp.ui.navigation.CategoryDetailRoute
import com.binc.gastapp.ui.navigation.ExplorePeriodRoute
import com.binc.gastapp.ui.navigation.ForgotPasswordRoute
import com.binc.gastapp.ui.navigation.MainRoute
import com.binc.gastapp.ui.navigation.PredictiveBackLayer
import com.binc.gastapp.ui.navigation.ProvideBackGesture
import com.binc.gastapp.ui.navigation.RegisterRoute
import com.binc.gastapp.ui.navigation.SpendingDetailRoute
import com.binc.gastapp.ui.navigation.StartRoute
import com.binc.gastapp.ui.navigation.SubscriptionFormRoute
import com.binc.gastapp.ui.navigation.SubscriptionsRoute
import com.binc.gastapp.ui.navigation.entersFromBottom
import com.binc.gastapp.ui.navigation.stackedBaseExit
import com.binc.gastapp.ui.navigation.stackedBaseReturn
import com.binc.gastapp.ui.session.SessionViewModel
import com.binc.gastapp.ui.spending.SpendingDetailScreen
import com.binc.gastapp.ui.start.ForgotPasswordScreen
import com.binc.gastapp.ui.subscriptions.SubscriptionFormScreen
import com.binc.gastapp.ui.subscriptions.SubscriptionsScreen
import com.binc.gastapp.ui.start.RegisterScreen
import com.binc.gastapp.ui.start.StartScreen
import java.time.LocalDate

/** Que se muestra segun la sesion. */
private enum class Gate { Splash, Start, App }

/**
 * Raiz de la app. Primero la compuerta de sesion (CheckUser de App.xaml.cs):
 *  - Sin datos en el telefono, o si el servidor respondio 401: pantalla de inicio.
 *  - Con datos (sesion vigente o vencida por tiempo): la app. Con la sesion vencida se
 *    usa lo local y el inicio de sesion se abre a peticion, con opcion de volver.
 * Despues, las pestanas (MainRoute) y encima las pantallas apiladas que se cierran con
 * el atras predictivo.
 */
@Composable
fun GastappApp(sessionViewModel: SessionViewModel) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val loginRequest by sessionViewModel.loginRequest.collectAsStateWithLifecycle()

    LaunchedEffect(session) { if (session is SessionState.Active) sessionViewModel.onLoggedIn() }

    val current = session
    val gate = when {
        // Se queda el splash (ver MainActivity).
        current == null -> Gate.Splash
        current is SessionState.LoggedOut || current is SessionState.Revoked -> Gate.Start
        current is SessionState.Expired && loginRequest != null -> Gate.Start
        else -> Gate.App
    }

    AnimatedContent(
        targetState = gate,
        transitionSpec = { fadeIn(tween(300, delayMillis = 60)) togetherWith fadeOut(tween(200)) },
        label = "compuerta",
    ) { target ->
        when (target) {
            Gate.Splash -> Unit
            Gate.Start -> when (current) {
                is SessionState.Revoked -> StartNavHost(
                    message = "Tu sesión expiró. Inicia sesión de nuevo para sincronizar.",
                    openLogin = true,
                    loginEmail = current.email.orEmpty(),
                    onClose = null,
                    onUseSample = null,
                )
                is SessionState.Expired -> StartNavHost(
                    message = "Estás usando la app con los datos de este teléfono. Inicia sesión para volver a sincronizar.",
                    openLogin = true,
                    loginEmail = loginRequest?.email ?: current.email.orEmpty(),
                    onClose = sessionViewModel::dismissLogin,
                    onUseSample = null,
                )
                else -> StartNavHost(
                    message = null,
                    openLogin = false,
                    loginEmail = "",
                    onClose = null,
                    onUseSample = sessionViewModel::useSample,
                )
            }
            Gate.App -> AppNavHost()
        }
    }
}

private const val LoginEmailKey = "login_email"

/** Inicio, registro y recuperar contrasena: lo unico que se ve sin sesion. */
@Composable
private fun StartNavHost(
    message: String?,
    openLogin: Boolean,
    loginEmail: String,
    onClose: (() -> Unit)?,
    onUseSample: (() -> Unit)?,
) {
    val navController = rememberNavController()

    ProvideBackGesture {
        StackedNavHost(navController, startDestination = StartRoute) {
            composable<StartRoute> { entry ->
                val returnedEmail by entry.savedStateHandle.getStateFlow<String?>(LoginEmailKey, null)
                    .collectAsStateWithLifecycle()
                StartScreen(
                    message = message,
                    openLogin = openLogin,
                    loginEmail = loginEmail,
                    pendingLoginEmail = returnedEmail,
                    onPendingLoginConsumed = { entry.savedStateHandle[LoginEmailKey] = null },
                    onRegister = dropUnlessResumed { navController.navigate(RegisterRoute) },
                    onForgotPassword = dropUnlessResumed { navController.navigate(ForgotPasswordRoute) },
                    onClose = onClose,
                    onUseSample = onUseSample,
                )
            }
            composable<RegisterRoute> { entry ->
                PredictiveBackLayer(entry, navController) {
                    RegisterScreen(onClose = navController.popOnce())
                }
            }
            composable<ForgotPasswordRoute> { entry ->
                PredictiveBackLayer(entry, navController) {
                    ForgotPasswordScreen(
                        onClose = navController.popOnce(),
                        onGoToLogin = { email ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(LoginEmailKey, email)
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}

private const val ExploreRangeKey = "explore_range"

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val messages = rememberAppMessages()

    CompositionLocalProvider(LocalAppMessages provides messages) {
        ProvideBackGesture {
            StackedNavHost(navController, startDestination = MainRoute) {
                composable<MainRoute> { entry ->
                    val explored by entry.savedStateHandle.getStateFlow<String?>(ExploreRangeKey, null)
                        .collectAsStateWithLifecycle()
                    MainScreen(
                        navigation = MainNavigation(
                            onOpenCards = dropUnlessResumed { navController.navigate(CardsRoute) },
                            onOpenSubscriptions = dropUnlessResumed { navController.navigate(SubscriptionsRoute) },
                            onExplorePeriod = { start, end ->
                                navController.navigateFrom(entry, ExplorePeriodRoute(start.toString(), end.toString()))
                            },
                            onOpenSpending = { id -> navController.navigateFrom(entry, SpendingDetailRoute(id)) },
                            onOpenCategory = { id, start, end ->
                                navController.navigateFrom(entry, CategoryDetailRoute(id, start.toString(), end.toString()))
                            },
                        ),
                        exploreResult = explored?.toDateRange(),
                        onExploreResultConsumed = { entry.savedStateHandle[ExploreRangeKey] = null },
                    )
                }

                composable<SpendingDetailRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        SpendingDetailScreen(
                            onBack = navController.popOnce(),
                            onSaved = { messages.show("Cambios guardados") },
                        )
                    }
                }
                composable<CategoryDetailRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        CategoryDetailScreen(
                            onBack = navController.popOnce(),
                            onOpenSpending = { id -> navController.navigateFrom(entry, SpendingDetailRoute(id)) },
                        )
                    }
                }
                composable<ExplorePeriodRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        ExplorePeriodScreen(
                            onClose = navController.popOnce(),
                            onApply = { start, end ->
                                if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                    navController.previousBackStackEntry?.savedStateHandle?.set(ExploreRangeKey, "$start|$end")
                                    navController.popBackStack()
                                }
                            },
                            onOpenCategory = { categoryId, start, end ->
                                navController.navigateFrom(entry, CategoryDetailRoute(categoryId, start.toString(), end.toString()))
                            },
                        )
                    }
                }

                composable<CardsRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        CardsScreen(
                            onBack = navController.popOnce(),
                            onAddCard = { navController.navigateFrom(entry, CardFormRoute()) },
                            onEditCard = { id -> navController.navigateFrom(entry, CardFormRoute(id)) },
                            onOpenSpending = { id -> navController.navigateFrom(entry, SpendingDetailRoute(id)) },
                        )
                    }
                }
                composable<CardFormRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        CardFormScreen(
                            onClose = navController.popOnce(),
                            onSaved = { message ->
                                messages.show(message)
                                navController.popBackStack()
                            },
                        )
                    }
                }
                composable<SubscriptionsRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        SubscriptionsScreen(
                            onBack = navController.popOnce(),
                            onAdd = { navController.navigateFrom(entry, SubscriptionFormRoute()) },
                            onEdit = { id -> navController.navigateFrom(entry, SubscriptionFormRoute(id)) },
                        )
                    }
                }
                composable<SubscriptionFormRoute> { entry ->
                    PredictiveBackLayer(entry, navController) {
                        SubscriptionFormScreen(
                            onClose = navController.popOnce(),
                            onSaved = { message ->
                                messages.show(message)
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "2026-10-01|2026-10-15" -> fechas. */
private fun String.toDateRange(): Pair<LocalDate, LocalDate>? {
    val parts = split('|')
    if (parts.size != 2) return null
    return runCatching { LocalDate.parse(parts[0]) to LocalDate.parse(parts[1]) }.getOrNull()
}

/**
 * Navega solo si [from] sigue al frente: ignora el segundo toque de un doble toque
 * (dropUnlessResumed, pero para acciones con parametros).
 */
private fun NavController.navigateFrom(from: NavBackStackEntry, route: Any) {
    if (from.lifecycle.currentState == Lifecycle.State.RESUMED) navigate(route)
}

/**
 * NavHost con las transiciones de las pantallas apiladas: estas animan su propia entrada
 * y salida (PredictiveBackLayer) y la de abajo se hace a un lado, salvo bajo las que
 * entran desde abajo.
 */
@Composable
private fun StackedNavHost(
    navController: androidx.navigation.NavHostController,
    startDestination: Any,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        exitTransition = {
            if (targetState.entersFromBottom) ExitTransition.KeepUntilTransitionsFinished
            else stackedBaseExit()
        },
        popEnterTransition = {
            if (initialState.entersFromBottom) EnterTransition.None else stackedBaseReturn()
        },
        builder = builder,
    )
}

/** Regresar ignorando toques repetidos mientras la pantalla ya va de salida. */
@Composable
private fun NavController.popOnce(): () -> Unit = dropUnlessResumed { popBackStack() }
