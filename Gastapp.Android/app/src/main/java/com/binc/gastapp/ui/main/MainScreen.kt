package com.binc.gastapp.ui.main

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.ui.components.AppSnackbarHost
import com.binc.gastapp.ui.components.FitText
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.isScrollingUp
import com.binc.gastapp.ui.components.statusColor
import com.binc.gastapp.ui.cards.CardPaymentSheet
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.rememberStrings
import com.binc.gastapp.ui.navigation.TopLevelTab
import com.binc.gastapp.ui.profile.ProfileScreen
import com.binc.gastapp.ui.profile.ProfileShortcuts
import com.binc.gastapp.ui.savings.PendingCard
import com.binc.gastapp.ui.savings.SavingsActions
import com.binc.gastapp.ui.savings.SavingsScreen
import com.binc.gastapp.ui.savings.SavingsViewModel
import com.binc.gastapp.ui.session.SessionViewModel
import com.binc.gastapp.ui.settings.SettingsActions
import com.binc.gastapp.ui.settings.SettingsScreen
import com.binc.gastapp.ui.spending.SpendingFormRequest
import com.binc.gastapp.ui.spending.SpendingFormResult
import com.binc.gastapp.ui.spending.SpendingFormSheet
import com.binc.gastapp.ui.summary.SummaryActions
import com.binc.gastapp.ui.summary.SummaryScreen
import com.binc.gastapp.ui.summary.SummaryViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** Lo que la pantalla principal le pide al NavHost. */
class MainNavigation(
    val onOpenCards: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
    val onExplorePeriod: (LocalDate, LocalDate) -> Unit,
    val onOpenSpending: (String) -> Unit,
    val onOpenCategory: (categoryId: String, start: LocalDate, end: LocalDate) -> Unit,
    val onOpenLegal: () -> Unit,
)

/**
 * Pantalla principal (MainPage de MAUI): barra superior, cuatro pestanas, barra inferior
 * y FAB "Nuevo gasto", como en el demo. Las pestanas son estado de esta pantalla (no
 * rutas): cambian con "fade through" y no se apilan, asi que atras desde otra pestana
 * regresa a Resumen y de ahi sale.
 *
 * @param exploreResult rango que se acaba de aplicar en Explorar periodo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navigation: MainNavigation,
    exploreResult: Pair<LocalDate, LocalDate>?,
    onExploreResultConsumed: () -> Unit,
    summaryViewModel: SummaryViewModel = hiltViewModel(),
    savingsViewModel: SavingsViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(TopLevelTab.Summary) }
    var formRequest by remember { mutableStateOf<SpendingFormRequest?>(null) }
    val messages = LocalAppMessages.current
    val strings = rememberStrings()
    val summaryList = rememberLazyListState()
    val savingsList = rememberLazyListState()
    val profileList = rememberLazyListState()
    val settingsList = rememberLazyListState()
    val summary by summaryViewModel.state.collectAsStateWithLifecycle()
    val savings by savingsViewModel.state.collectAsStateWithLifecycle()
    var payingCard by remember { mutableStateOf<PendingCard?>(null) }

    // La sesion vive en el ViewModel de la actividad (el de la compuerta de GastappApp).
    val sessionViewModel: SessionViewModel = hiltViewModel(LocalActivity.current as ComponentActivity)
    val session by sessionViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(exploreResult) {
        exploreResult?.let { (start, end) ->
            summaryViewModel.applyRange(start, end)
            tab = TopLevelTab.Summary
            onExploreResultConsumed()
        }
    }

    // DayChangedMessage de MAUI: al volver a la app y a medianoche se revisa si cambio el dia.
    LifecycleResumeEffect(Unit) {
        summaryViewModel.refreshToday()
        savingsViewModel.refreshToday()
        onPauseOrDispose { }
    }
    LaunchedEffect(summary.today) {
        val untilMidnight = Duration.between(LocalDateTime.now(), summary.today.plusDays(1).atStartOfDay())
        delay(untilMidnight.toMillis().coerceAtLeast(0) + 1_000)
        summaryViewModel.refreshToday()
        savingsViewModel.refreshToday()
    }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    // Cada pestana empieza con la barra superior "limpia".
    LaunchedEffect(tab) { scroll.state.contentOffset = 0f }

    BackHandler(enabled = tab != TopLevelTab.Summary) { tab = TopLevelTab.Summary }

    val fabExpanded = when (tab) {
        TopLevelTab.Summary -> summaryList.isScrollingUp()
        TopLevelTab.Savings -> savingsList.isScrollingUp()
        else -> true
    }

    fun openExplore() {
        val period = summary.period ?: return
        navigation.onExplorePeriod(period.start, period.end)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = tab.title,
                        transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                        label = "titulo",
                    ) { Text(stringResource(it)) }
                },
                actions = {
                    AnimatedVisibility(
                        visible = tab == TopLevelTab.Summary,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Row {
                            // El puntito avisa que hay una tarjeta por vencer (ambar o rojo).
                            val cardAlert = summary.cardChip?.level?.takeIf { it.ordinal > 0 }
                            IconButton(onClick = navigation.onOpenCards) {
                                BadgedBox(
                                    badge = {
                                        if (cardAlert != null) Badge(containerColor = statusColor(cardAlert).strong)
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.CreditCard,
                                        contentDescription = stringResource(if (cardAlert != null) R.string.my_cards_alert_description else R.string.my_cards_description),
                                    )
                                }
                            }
                            IconButton(onClick = navigation.onOpenSubscriptions) {
                                Icon(Icons.Rounded.Subscriptions, contentDescription = stringResource(R.string.subscriptions_description))
                            }
                            IconButton(onClick = ::openExplore) {
                                Icon(Icons.Rounded.DateRange, contentDescription = stringResource(R.string.explore_period))
                            }
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(if (item == tab) item.selectedIcon else item.icon, contentDescription = null) },
                        // Con la fuente al 200 % "Resumen" no cabe: se encoge en vez de partirse.
                        label = { FitText(stringResource(item.title)) },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = tab == TopLevelTab.Summary || tab == TopLevelTab.Savings,
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    // En Resumen el gasto nuevo cae en el dia elegido (MenuSelectedDate de MAUI).
                    onClick = {
                        formRequest = SpendingFormRequest.New(
                            date = if (tab == TopLevelTab.Summary) summary.selectedDay else summary.today,
                        )
                    },
                    expanded = fabExpanded,
                    // El texto del FAB extendido no llega al arbol de accesibilidad (visto con
                    // uiautomator) y contraido ni se dibuja: el nombre para TalkBack va en el icono.
                    icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_spending)) },
                    text = { Text(stringResource(R.string.new_spending)) },
                )
            }
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        // "Fade through" de Material entre pestanas.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.94f)) togetherWith
                    fadeOut(tween(90))
            },
            label = "pestanas",
        ) { current ->
            when (current) {
                TopLevelTab.Summary -> SummaryScreen(
                    state = summary,
                    sessionExpired = session is SessionState.Expired,
                    contentPadding = padding,
                    listState = summaryList,
                    actions = SummaryActions(
                        onPreviousPeriod = summaryViewModel::previousPeriod,
                        onNextPeriod = summaryViewModel::nextPeriod,
                        onExplore = ::openExplore,
                        onSelectDay = summaryViewModel::selectDay,
                        onGoToToday = summaryViewModel::goToToday,
                        onOpenSpending = navigation.onOpenSpending,
                        onDeleteSpending = { item ->
                            summaryViewModel.deleteSpending(item.id) {
                                messages.showUndo(strings.get(R.string.spending_deleted)) { summaryViewModel.restoreSpending(item.id) }
                            }
                        },
                        onLogin = { sessionViewModel.requestLogin((session as? SessionState.Expired)?.email) },
                    ),
                )
                TopLevelTab.Savings -> SavingsScreen(
                    state = savings,
                    contentPadding = padding,
                    listState = savingsList,
                    actions = SavingsActions(
                        onPreviousPeriod = savingsViewModel::previousPeriod,
                        onNextPeriod = savingsViewModel::nextPeriod,
                        onOpenCards = navigation.onOpenCards,
                        onOpenCategory = { share ->
                            val period = savings.period
                            val id = share.total.categoryId
                            if (period != null && id != null) navigation.onOpenCategory(id, period.start, period.end)
                        },
                        onPayCard = { payingCard = it },
                    ),
                )
                TopLevelTab.Profile -> ProfileScreen(
                    contentPadding = padding,
                    shortcuts = ProfileShortcuts(
                        cardsSubtitle = summary.cardsSubtitle ?: stringResource(R.string.cards_subtitle_default),
                        cardChip = summary.cardChip,
                        subscriptionsSubtitle = summary.subscriptionsSubtitle ?: stringResource(R.string.subscriptions_subtitle_default),
                        onOpenCards = navigation.onOpenCards,
                        onOpenSubscriptions = navigation.onOpenSubscriptions,
                    ),
                    listState = profileList,
                )
                TopLevelTab.Settings -> SettingsScreen(
                    contentPadding = padding,
                    listState = settingsList,
                    actions = SettingsActions(
                        onOpenCards = navigation.onOpenCards,
                        onLogin = { sessionViewModel.requestLogin((session as? SessionState.Expired)?.email) },
                        onLogout = sessionViewModel::logout,
                        onOpenLegal = navigation.onOpenLegal,
                    ),
                )
            }
        }
    }

    payingCard?.let { pending ->
        CardPaymentSheet(
            summary = pending.summary,
            onConfirm = { amount ->
                payingCard = null
                savingsViewModel.registerPayment(pending.summary, amount) {
                    messages.show(strings.get(R.string.payment_registered, formatMoney(amount)))
                }
            },
            onDismiss = { payingCard = null },
        )
    }

    formRequest?.let { request ->
        SpendingFormSheet(
            request = request,
            onDismiss = { formRequest = null },
            onResult = { result ->
                formRequest = null
                when (result) {
                    is SpendingFormResult.Saved -> {
                        messages.show(strings.get(if (result.isNew) R.string.spending_saved else R.string.changes_saved))
                        tab = TopLevelTab.Summary
                        summaryViewModel.showDay(result.spending.date.toLocalDate())
                    }
                    is SpendingFormResult.Deleted -> messages.showUndo(strings.get(R.string.spending_deleted)) {
                        summaryViewModel.restoreSpending(result.spendingId)
                    }
                }
            },
        )
    }
}
