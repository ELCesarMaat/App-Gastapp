package com.binc.gastapp.ui.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.serialization.Serializable

// Rutas tipadas de Navigation Compose. Las pestanas no son rutas: viven dentro de
// MainRoute como estado (ver MainScreen). Encima de MainRoute se apilan las pantallas
// de detalle, que se cierran con el atras predictivo (ver PredictiveBack.kt).

/** Pestanas, barra inferior y FAB. */
@Serializable
data object MainRoute

/** Mis tarjetas (Fase 4.4). Entra desde la derecha. */
@Serializable
data object CardsRoute

/** Suscripciones (Fase 4.5). Entra desde la derecha. */
@Serializable
data object SubscriptionsRoute

/** Explorar periodo (Fase 4.2). Entra desde abajo, como un dialogo de pantalla completa. */
@Serializable
data object ExplorePeriodRoute

/** Pantallas que entran desde abajo; las demas entran desde la derecha. */
val NavBackStackEntry.entersFromBottom: Boolean
    get() = destination.hasRoute<ExplorePeriodRoute>()
