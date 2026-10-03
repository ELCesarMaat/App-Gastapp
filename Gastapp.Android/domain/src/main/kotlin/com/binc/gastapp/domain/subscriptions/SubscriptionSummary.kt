package com.binc.gastapp.domain.subscriptions

import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.PlannedSpending
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.money.dividedBy
import com.binc.gastapp.domain.money.formatN2
import com.binc.gastapp.domain.time.daysBetween
import java.math.BigDecimal
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

// Port de SubscriptionService.BuildSummary / GetUpcomingChargesAsync y de
// SubscriptionsViewModel.RegisterCharge (MAUI).

/**
 * Estado del proximo cobro. [text] no incluye la fecha: cuando [showsDate] es true, la
 * UI le agrega " (dd/MMM)" como hacia MAUI.
 */
data class ChargeStatus(val text: String, val level: StatusLevel, val showsDate: Boolean)

fun chargeStatus(isActive: Boolean, daysUntilCharge: Int): ChargeStatus {
    if (!isActive) return ChargeStatus("Pausada · sin cobros", StatusLevel.NEUTRAL, false)
    return when {
        daysUntilCharge < 0 -> ChargeStatus("Cobro pendiente de registrar", StatusLevel.CRITICAL, false)
        daysUntilCharge == 0 -> ChargeStatus("Se cobra hoy", StatusLevel.CRITICAL, false)
        daysUntilCharge == 1 -> ChargeStatus("Se cobra mañana", StatusLevel.WARNING, false)
        daysUntilCharge <= 3 -> ChargeStatus("Se cobra en $daysUntilCharge días", StatusLevel.WARNING, false)
        else -> ChargeStatus("Se cobra en $daysUntilCharge días", StatusLevel.OK, true)
    }
}

/** Chip de estado: Pausada (gris), Prueba gratis (ambar) o Activa (verde). */
enum class SubscriptionBadge(val text: String, val level: StatusLevel) {
    PAUSED("Pausada", StatusLevel.NEUTRAL),
    TRIAL("Prueba gratis", StatusLevel.WARNING),
    ACTIVE("Activa", StatusLevel.OK),
}

data class UpcomingCharge(
    val subscriptionId: String,
    val serviceName: String,
    val planName: String?,
    val amount: BigDecimal,
    val date: LocalDate,
    val daysUntil: Int,
    val colorHex: String,
    /** "Hoy", "Mañana", "En 5 días". */
    val whenText: String,
    val level: StatusLevel,
    val isChargedToCard: Boolean,
    val paymentSourceText: String,
)

data class SubscriptionSummary(
    val subscription: Subscription,
    val monthlyEquivalent: BigDecimal,
    val yearlyEquivalent: BigDecimal,
    val nextChargeDate: LocalDate,
    val daysUntilCharge: Int,
    val chargeStatus: ChargeStatus,
    val billingCycleText: String,
    val priceCycleText: String,
    val paymentMethodText: String,
    val linkedCardName: String,
    val categoryName: String,
    val countsTowardTotals: Boolean,
    val toggleStateActionText: String,
    /** El cobro del periodo en curso ya se registro como gasto. */
    val isCurrentCycleCharged: Boolean,
    val lastChargeDate: LocalDate?,
    val badge: SubscriptionBadge,
    val isTrialActive: Boolean,
    val daysUntilTrialEnds: Int,
    /** Que tanto pesa dentro del gasto mensual total, de 0 a 1. */
    val shareOfMonthlyRatio: Double,
    val shareOfMonthlyText: String,
    val upcomingCharges: List<UpcomingCharge>,
) {
    val hasLinkedCard: Boolean get() = linkedCardName.isNotBlank()
    val notes: String get() = subscription.notes.orEmpty()
    val hasNotes: Boolean get() = !subscription.notes.isNullOrBlank()

    fun lastChargeText(formatDate: (LocalDate) -> String): String =
        if (isCurrentCycleCharged && lastChargeDate != null) {
            "Ya registraste el cobro de este periodo el ${formatDate(lastChargeDate)}."
        } else {
            ""
        }

    fun trialStatusText(today: LocalDate, formatDate: (LocalDate) -> String): String {
        val trialEnd = subscription.trialEndDate
        return when {
            !subscription.isTrial || trialEnd == null -> ""
            !isTrialActive -> "La prueba terminó el ${formatDate(trialEnd)}, ya se está cobrando."
            daysUntilTrialEnds == 0 ->
                "La prueba termina hoy: mañana empieza el cobro de $${subscription.amount.formatN2()}."
            else ->
                "Prueba gratis: te quedan $daysUntilTrialEnds día${if (daysUntilTrialEnds == 1) "" else "s"} " +
                    "(hasta el ${formatDate(trialEnd)})."
        }
    }
}

/** Gasto mensual total: solo de las que cuentan (ni pausadas ni en prueba). */
fun totalMonthlyCost(subscriptions: List<Subscription>, today: LocalDate): BigDecimal =
    subscriptions
        .filter { !it.isDeleted && countsTowardTotals(it, today) }
        .fold(BigDecimal.ZERO) { acc, s -> acc + monthlyEquivalent(s.amount, s.billingCycle) }

fun buildSubscriptionSummary(
    subscription: Subscription,
    monthlyTotal: BigDecimal,
    cardNames: Map<String, String>,
    categoryNames: Map<String, String>,
    today: LocalDate,
): SubscriptionSummary {
    val monthly = monthlyEquivalent(subscription.amount, subscription.billingCycle)

    val trialActive = isTrialActive(subscription, today)
    val trialEnd = subscription.trialEndDate
    val daysUntilTrialEnds = if (trialActive && trialEnd != null) daysBetween(today, trialEnd) else 0

    // Durante la prueba, el primer cobro real cae el dia que termina.
    val reference = if (trialActive && trialEnd != null) trialEnd else today
    val nextCharge = nextChargeDate(subscription.firstChargeDate, subscription.billingCycle, reference)
    val daysUntilCharge = daysBetween(today, nextCharge)

    val counts = countsTowardTotals(subscription, today)
    val share = if (monthlyTotal.signum() > 0 && counts) {
        (monthly dividedBy monthlyTotal).toDouble().coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    val shareText = when {
        !subscription.isActive -> "No cuenta en tus totales mientras esté pausada"
        trialActive -> "No cuenta en tus totales mientras dure la prueba"
        else -> "${String.format(Locale.ROOT, "%.0f", share * 100)}% de tu gasto mensual en suscripciones"
    }

    // El periodo en curso arranca en el ultimo cobro que ya ocurrio: si el registro
    // cayo en o despues de esa fecha, el cobro de este periodo ya esta contabilizado.
    val currentCycle = previousChargeDate(subscription.firstChargeDate, subscription.billingCycle, today)
    val lastCharge = subscription.lastChargeRegisteredAt?.toLocalDate()
    val charged = lastCharge != null && lastCharge >= currentCycle && currentCycle <= today

    val badge = when {
        !subscription.isActive -> SubscriptionBadge.PAUSED
        trialActive -> SubscriptionBadge.TRIAL
        else -> SubscriptionBadge.ACTIVE
    }

    val linkedCardName = subscription.creditCardId
        ?.takeIf { subscription.paymentMethod == PaymentMethods.CREDIT_CARD && it.isNotBlank() }
        ?.let { cardNames[it] }
        .orEmpty()
    val categoryName = subscription.categoryId
        ?.takeIf { it.isNotBlank() }
        ?.let { categoryNames[it] }
        .orEmpty()

    return SubscriptionSummary(
        subscription = subscription,
        monthlyEquivalent = monthly,
        yearlyEquivalent = monthly.multiply(BigDecimal(12)),
        nextChargeDate = nextCharge,
        daysUntilCharge = daysUntilCharge,
        chargeStatus = chargeStatus(subscription.isActive, daysUntilCharge),
        billingCycleText = billingCycleDisplayName(subscription.billingCycle),
        priceCycleText = "$${subscription.amount.formatN2()} ${billingCycleSuffix(subscription.billingCycle)}",
        paymentMethodText = paymentMethodDisplayName(subscription.paymentMethod),
        linkedCardName = linkedCardName,
        categoryName = categoryName,
        countsTowardTotals = counts,
        toggleStateActionText = if (subscription.isActive) "Pausar" else "Reanudar",
        isCurrentCycleCharged = charged,
        lastChargeDate = lastCharge,
        badge = badge,
        isTrialActive = trialActive,
        daysUntilTrialEnds = daysUntilTrialEnds,
        shareOfMonthlyRatio = share,
        shareOfMonthlyText = shareText,
        upcomingCharges = nextCharges(subscription, nextCharge, today, cardNames, count = 3),
    )
}

/** Las siguientes [count] repeticiones de una misma suscripcion, desde [firstNext]. */
fun nextCharges(
    subscription: Subscription,
    firstNext: LocalDate,
    today: LocalDate,
    cardNames: Map<String, String>,
    count: Int,
): List<UpcomingCharge> {
    val charges = mutableListOf<UpcomingCharge>()
    var next = firstNext
    repeat(count) {
        charges += upcomingCharge(subscription, next, today, cardNames)
        next = nextChargeDate(subscription.firstChargeDate, subscription.billingCycle, next.plusDays(1))
    }
    return charges
}

private val CollatorMx: Collator = Collator.getInstance(Locale.Builder().setLanguage("es").setRegion("MX").build())

/**
 * Todo lo que se va a cobrar en los proximos [daysAhead] dias, de todas las
 * suscripciones activas, en orden de fecha (y de nombre en el mismo dia).
 */
fun upcomingCharges(
    subscriptions: List<Subscription>,
    today: LocalDate,
    cardNames: Map<String, String> = emptyMap(),
    daysAhead: Int = 45,
): List<UpcomingCharge> {
    val limit = today.plusDays(maxOf(1, daysAhead).toLong())
    val charges = mutableListOf<UpcomingCharge>()

    for (subscription in subscriptions.filter { !it.isDeleted && it.isActive }) {
        // Una prueba en curso no cobra nada: el primer cargo cae el dia que termina.
        val trialEnd = subscription.trialEndDate
        val reference = if (subscription.isTrial && trialEnd != null && trialEnd > today) trialEnd else today

        var next = nextChargeDate(subscription.firstChargeDate, subscription.billingCycle, reference)
        var guard = 0
        while (next <= limit && guard++ < 60) {
            charges += upcomingCharge(subscription, next, today, cardNames)
            next = nextChargeDate(subscription.firstChargeDate, subscription.billingCycle, next.plusDays(1))
        }
    }

    return charges.sortedWith(compareBy<UpcomingCharge> { it.date }.thenComparator { a, b ->
        CollatorMx.compare(a.serviceName, b.serviceName)
    })
}

private fun upcomingCharge(
    subscription: Subscription,
    date: LocalDate,
    today: LocalDate,
    cardNames: Map<String, String>,
): UpcomingCharge {
    val daysUntil = daysBetween(today, date)
    val whenText = when {
        daysUntil < 0 -> "Ya pasó"
        daysUntil == 0 -> "Hoy"
        daysUntil == 1 -> "Mañana"
        else -> "En $daysUntil días"
    }
    val level = when {
        daysUntil <= 0 -> StatusLevel.CRITICAL
        daysUntil <= 3 -> StatusLevel.WARNING
        else -> StatusLevel.OK
    }
    val isCard = subscription.paymentMethod == PaymentMethods.CREDIT_CARD
    val cardName = subscription.creditCardId
        ?.takeIf { isCard && it.isNotBlank() }
        ?.let { cardNames[it] }
        .orEmpty()

    return UpcomingCharge(
        subscriptionId = subscription.subscriptionId,
        serviceName = subscription.serviceName,
        planName = subscription.planName,
        amount = subscription.amount,
        date = date,
        daysUntil = daysUntil,
        colorHex = subscription.colorHex,
        whenText = whenText,
        level = level,
        isChargedToCard = isCard,
        paymentSourceText = cardName.ifBlank { paymentMethodDisplayName(subscription.paymentMethod) },
    )
}

/**
 * El gasto que crea "Registrar cobro". Si se cobra a una tarjeta, suma a la deuda de
 * esa tarjeta como cualquier compra. Se crea aunque el cobro del periodo ya estuviera
 * registrado: la UI avisa, pero no bloquea (puede ser un cargo doble real).
 */
fun subscriptionCharge(subscription: Subscription, amountCharged: BigDecimal, now: LocalDateTime): PlannedSpending {
    val cycle = billingCycleDisplayName(subscription.billingCycle).lowercase()
    val toCard = subscription.paymentMethod == PaymentMethods.CREDIT_CARD && !subscription.creditCardId.isNullOrBlank()
    val description = if (subscription.planName.isNullOrBlank()) {
        "Cobro $cycle de ${subscription.serviceName}"
    } else {
        "Cobro $cycle de ${subscription.serviceName} (${subscription.planName})"
    }
    return PlannedSpending(
        title = "Suscripción - ${subscription.serviceName}",
        description = description,
        amount = amountCharged,
        date = now,
        isCreditCard = toCard,
        creditCardId = if (toCard) subscription.creditCardId else null,
        paymentMethod = subscription.paymentMethod,
    )
}

/** Categoria del cobro: la de la suscripcion, si existe; si no, la de por defecto; si no, la primera. */
fun chargeCategoryId(subscription: Subscription, categories: List<com.binc.gastapp.domain.model.Category>): String? =
    categories.firstOrNull { it.categoryId == subscription.categoryId }?.categoryId
        ?: categories.firstOrNull { it.isDefaultCategory }?.categoryId
        ?: categories.firstOrNull()?.categoryId
