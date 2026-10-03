package com.binc.gastapp.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.binc.gastapp.ui.components.isScrollingUp
import com.binc.gastapp.ui.navigation.TopLevelTab
import kotlinx.coroutines.launch

/**
 * Pantalla principal: barra superior, cuatro pestanas, barra inferior y FAB, como en el
 * demo. Las pestanas son estado de esta pantalla (no rutas): cambian con "fade through"
 * y no se apilan, asi que atras desde otra pestana regresa a Resumen y de ahi sale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenCards: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onExplorePeriod: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(TopLevelTab.Summary) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val summaryList = rememberLazyListState()
    val savingsList = rememberLazyListState()

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    // Cada pestana empieza con la barra superior "limpia".
    LaunchedEffect(tab) { scroll.state.contentOffset = 0f }

    BackHandler(enabled = tab != TopLevelTab.Summary) { tab = TopLevelTab.Summary }

    val fabExpanded = when (tab) {
        TopLevelTab.Summary -> summaryList.isScrollingUp()
        TopLevelTab.Savings -> savingsList.isScrollingUp()
        else -> true
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
                    ) { Text(it) }
                },
                actions = {
                    AnimatedVisibility(
                        visible = tab == TopLevelTab.Summary,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        IconButton(onClick = onExplorePeriod) {
                            Icon(Icons.Rounded.DateRange, contentDescription = "Explorar periodo")
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
                        icon = {
                            Icon(
                                if (item == tab) item.selectedIcon else item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(item.title) },
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
                    onClick = {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        scope.launch { snackbarHostState.showSnackbar("El formulario de gasto llega en la Fase 4.2") }
                    },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Nuevo gasto") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // "Fade through" de Material entre pestanas.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) +
                    scaleIn(tween(220, delayMillis = 90), initialScale = 0.94f)) togetherWith
                    fadeOut(tween(90))
            },
            label = "pestanas",
        ) { current ->
            when (current) {
                TopLevelTab.Summary -> SummaryPlaceholder(
                    contentPadding = padding,
                    listState = summaryList,
                    onOpenCards = onOpenCards,
                    onOpenSubscriptions = onOpenSubscriptions,
                    onExplorePeriod = onExplorePeriod,
                )
                TopLevelTab.Savings -> TabPlaceholder(
                    contentPadding = padding,
                    listState = savingsList,
                    tab = current,
                    phase = "Fase 4.3",
                )
                TopLevelTab.Profile -> TabPlaceholder(
                    contentPadding = padding,
                    tab = current,
                    phase = "Fase 4.6",
                )
                TopLevelTab.Settings -> TabPlaceholder(
                    contentPadding = padding,
                    tab = current,
                    phase = "Fase 4.6",
                )
            }
        }
    }
}
