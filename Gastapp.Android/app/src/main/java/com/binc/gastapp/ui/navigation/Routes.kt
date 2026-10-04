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

/**
 * Explorar periodo. Entra desde abajo, como un dialogo de pantalla completa. Abre con
 * el periodo que muestra Resumen (fechas ISO: las rutas tipadas solo llevan tipos simples).
 */
@Serializable
data class ExplorePeriodRoute(val start: String, val end: String)

/** Alta (sin id) o edicion de una tarjeta. Pantalla completa, entra desde abajo. */
@Serializable
data class CardFormRoute(val creditCardId: String? = null)

/** Alta (sin id) o edicion de una suscripcion. Pantalla completa, entra desde abajo. */
@Serializable
data class SubscriptionFormRoute(val subscriptionId: String? = null)

/** Detalle de un gasto, con el boton Editar. */
@Serializable
data class SpendingDetailRoute(val spendingId: String)

/** Gastos de una categoria en un periodo (desde Ahorros y Explorar periodo). */
@Serializable
data class CategoryDetailRoute(val categoryId: String, val start: String, val end: String)

// ---------------------------------------------------------------- Arranque (sin sesion)

/** Pantalla de inicio: iniciar sesion, crear cuenta, olvide mi contrasena. */
@Serializable
data object StartRoute

/** Registro en 5 pasos. */
@Serializable
data object RegisterRoute

/** Recuperar contrasena. */
@Serializable
data object ForgotPasswordRoute

// ---------------------------------------------------------------- Con y sin sesion

/** Privacidad y legal: lo esencial, el aviso, los terminos y el contacto. */
@Serializable
data object LegalRoute

/** Un documento legal completo; [document] es el nombre de un LegalDocumentId. */
@Serializable
data class LegalDocumentRoute(val document: String)

/** Pantallas que entran desde abajo; las demas entran desde la derecha. */
val NavBackStackEntry.entersFromBottom: Boolean
    get() = destination.hasRoute<ExplorePeriodRoute>() || destination.hasRoute<CardFormRoute>() ||
        destination.hasRoute<SubscriptionFormRoute>()
