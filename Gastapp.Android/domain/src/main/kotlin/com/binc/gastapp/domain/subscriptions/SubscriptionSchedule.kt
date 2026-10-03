package com.binc.gastapp.domain.subscriptions

import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.money.dividedBy
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Port de SubscriptionService.cs (MAUI): calendario de cobros y totales.
// Paridad verificada contra cobros_suscripcion.json y escenarios_suscripcion.json.

private fun monthsPerCycle(billingCycle: String): Int = when (billingCycle) {
    BillingCycles.QUARTERLY -> 3
    BillingCycles.SEMIANNUAL -> 6
    BillingCycles.YEARLY -> 12
    else -> 1
}

private fun elapsedMonths(anchor: LocalDate, today: LocalDate): Int =
    (today.year - anchor.year) * 12 + today.monthValue - anchor.monthValue

/**
 * El proximo cobro en o despues de [referenceDate].
 *
 * El candidato siempre se calcula desde el ancla (ancla + n * meses) y nunca sumando
 * meses al cobro anterior: si el ancla cae 31 y un mes lo recorta a 30, encadenar
 * perderia el dia 31 para siempre. plusMonths recorta igual que AddMonths de .NET.
 */
fun nextChargeDate(firstChargeDate: LocalDate, billingCycle: String, referenceDate: LocalDate): LocalDate {
    val anchor = firstChargeDate
    val today = referenceDate

    // El primer cobro todavia no llega: ese es el proximo.
    if (anchor >= today) return anchor

    if (billingCycle == BillingCycles.WEEKLY) {
        var elapsedWeeks = ChronoUnit.DAYS.between(anchor, today) / 7
        var weekly = anchor.plusDays(elapsedWeeks * 7)
        while (weekly < today) {
            elapsedWeeks++
            weekly = anchor.plusDays(elapsedWeeks * 7)
        }
        return weekly
    }

    val months = monthsPerCycle(billingCycle)
    var cycles = maxOf(0, elapsedMonths(anchor, today) / months)
    var candidate = anchor.plusMonths((cycles * months).toLong())
    while (candidate < today) {
        cycles++
        candidate = anchor.plusMonths((cycles * months).toLong())
    }
    return candidate
}

/** El espejo hacia atras: el ultimo cobro en o antes de [referenceDate]. */
fun previousChargeDate(firstChargeDate: LocalDate, billingCycle: String, referenceDate: LocalDate): LocalDate {
    val anchor = firstChargeDate
    val today = referenceDate

    // Todavia no ocurre ni el primer cobro: no hay periodo anterior.
    if (anchor >= today) return anchor

    if (billingCycle == BillingCycles.WEEKLY) {
        val elapsedWeeks = ChronoUnit.DAYS.between(anchor, today) / 7
        return anchor.plusDays(elapsedWeeks * 7)
    }

    val months = monthsPerCycle(billingCycle)
    var cycles = maxOf(0, elapsedMonths(anchor, today) / months)
    var candidate = anchor.plusMonths((cycles * months).toLong())
    while (candidate > today && cycles > 0) {
        cycles--
        candidate = anchor.plusMonths((cycles * months).toLong())
    }
    return candidate
}

/** Una prueba gratis vigente: todavia no sale dinero. */
fun isTrialActive(subscription: Subscription, referenceDate: LocalDate): Boolean {
    val trialEnd = subscription.trialEndDate
    return subscription.isTrial && trialEnd != null && trialEnd >= referenceDate
}

/**
 * Si suma al gasto recurrente. Ni una pausada ni una en prueba gratis cuentan: el mismo
 * criterio alimenta el porcentaje de cada una, asi los dos numeros no se contradicen.
 */
fun countsTowardTotals(subscription: Subscription, referenceDate: LocalDate): Boolean =
    subscription.isActive && !isTrialActive(subscription, referenceDate)

/** El costo llevado a mes. Semanal = 52 semanas entre 12 meses, no 4 por mes. */
fun monthlyEquivalent(amount: BigDecimal, billingCycle: String): BigDecimal {
    if (amount.signum() <= 0) return BigDecimal.ZERO
    return when (billingCycle) {
        BillingCycles.WEEKLY -> amount.multiply(BigDecimal(52)) dividedBy 12
        BillingCycles.QUARTERLY -> amount dividedBy 3
        BillingCycles.SEMIANNUAL -> amount dividedBy 6
        BillingCycles.YEARLY -> amount dividedBy 12
        else -> amount
    }
}

fun billingCycleDisplayName(billingCycle: String): String = when (billingCycle) {
    BillingCycles.WEEKLY -> "Semanal"
    BillingCycles.QUARTERLY -> "Trimestral"
    BillingCycles.SEMIANNUAL -> "Semestral"
    BillingCycles.YEARLY -> "Anual"
    else -> "Mensual"
}

fun billingCycleSuffix(billingCycle: String): String = when (billingCycle) {
    BillingCycles.WEEKLY -> "a la semana"
    BillingCycles.QUARTERLY -> "cada 3 meses"
    BillingCycles.SEMIANNUAL -> "cada 6 meses"
    BillingCycles.YEARLY -> "al año"
    else -> "al mes"
}

/** Mismas etiquetas que el detalle del gasto, para que digan lo mismo. */
fun paymentMethodDisplayName(paymentMethod: String): String = when (paymentMethod) {
    PaymentMethods.CREDIT_CARD -> "Tarjeta de crédito"
    PaymentMethods.DEBIT -> "Tarjeta de débito"
    PaymentMethods.TRANSFER -> "Transferencia bancaria"
    else -> "Efectivo"
}
