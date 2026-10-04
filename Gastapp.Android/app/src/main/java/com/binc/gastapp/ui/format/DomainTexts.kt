package com.binc.gastapp.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binc.gastapp.R
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.domain.cards.StatementPaymentNote
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.isDefaultCategoryName
import com.binc.gastapp.domain.subscriptions.SubscriptionBadge
import com.binc.gastapp.domain.subscriptions.SubscriptionSummary
import com.binc.gastapp.domain.subscriptions.UpcomingCharge
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// Los textos que arma :domain (paymentStatusText, chargeStatus, priceCycleText...) son
// los de MAUI en espanol y las pruebas de paridad los comparan tal cual. La UI no los
// muestra: usa estos, con las mismas reglas, en el idioma del telefono.

// ------------------------------------------------------------ categorias y gastos

/** "Sin categoria" la crea el API con ese nombre: se muestra en el idioma del telefono. */
fun categoryLabel(strings: Strings, name: String): String =
    if (isDefaultCategoryName(name)) strings.get(R.string.default_category) else name

@Composable
fun categoryLabel(name: String): String =
    if (isDefaultCategoryName(name)) stringResource(R.string.default_category) else name

/**
 * La descripcion de un gasto como se muestra. "Pago del mes" es una marca (ver
 * StatementPaymentNote): se guarda siempre en espanol y aqui se traduce.
 */
@Composable
fun spendingDescription(description: String): String =
    if (description.startsWith(StatementPaymentNote)) {
        stringResource(R.string.statement_payment_note) + description.removePrefix(StatementPaymentNote)
    } else {
        description
    }

// ------------------------------------------------------------ formas de pago

/** Las mismas etiquetas en el formulario del gasto, su detalle y las suscripciones. */
@StringRes
fun paymentMethodName(paymentMethod: String?): Int = when (paymentMethod) {
    PaymentMethods.CREDIT_CARD -> R.string.payment_method_credit_card
    PaymentMethods.DEBIT -> R.string.payment_method_debit
    PaymentMethods.TRANSFER -> R.string.payment_method_transfer
    else -> R.string.payment_method_cash
}

// ------------------------------------------------------------ tarjetas

/**
 * "Vencido", "Vence hoy", "Vence en 2 días", "Vence en 12 días (25/oct)". Con
 * [withDate] false nunca lleva la fecha.
 */
fun cardPaymentStatus(strings: Strings, daysUntilPayment: Int, dueDate: LocalDate, withDate: Boolean = true): String = when {
    daysUntilPayment < 0 -> strings.get(R.string.card_overdue)
    daysUntilPayment == 0 -> strings.get(R.string.card_due_today)
    daysUntilPayment <= 3 || !withDate -> strings.plural(R.plurals.card_due_in_days, daysUntilPayment)
    else -> strings.plural(R.plurals.card_due_in_days_on, daysUntilPayment, daysUntilPayment, dayMonth(dueDate))
}

fun CardSummary.paymentStatus(strings: Strings, withDate: Boolean = true): String =
    cardPaymentStatus(strings, daysUntilPayment, nextPaymentDueDate, withDate)

/** "Corta hoy", "Corte en 5 días (05/oct)". */
fun CardSummary.cutOffStatus(strings: Strings): String =
    if (daysUntilCutOff == 0) strings.get(R.string.card_cuts_today)
    else strings.plural(R.plurals.card_cut_off_in_days, daysUntilCutOff, daysUntilCutOff, dayMonth(nextCutOffDate))

/** Ahorros, "Tarjetas por pagar": "Vencido" o "Vence en 3 días" (savesPendingText). */
fun savesPendingStatus(strings: Strings, daysUntilPayment: Int): String =
    if (daysUntilPayment < 0) strings.get(R.string.card_overdue)
    else strings.plural(R.plurals.card_due_in_days, daysUntilPayment)

// ------------------------------------------------------------ suscripciones

@StringRes
fun billingCycleName(billingCycle: String): Int = when (billingCycle) {
    BillingCycles.WEEKLY -> R.string.billing_weekly
    BillingCycles.QUARTERLY -> R.string.billing_quarterly
    BillingCycles.SEMIANNUAL -> R.string.billing_semiannual
    BillingCycles.YEARLY -> R.string.billing_yearly
    else -> R.string.billing_monthly
}

/** "$199.00 al mes", "$1,200.00 al año". */
fun priceCycleText(strings: Strings, amount: BigDecimal, billingCycle: String): String {
    val suffix = when (billingCycle) {
        BillingCycles.WEEKLY -> R.string.price_per_week
        BillingCycles.QUARTERLY -> R.string.price_every_3_months
        BillingCycles.SEMIANNUAL -> R.string.price_every_6_months
        BillingCycles.YEARLY -> R.string.price_per_year
        else -> R.string.price_per_month
    }
    return strings.get(suffix, formatMoney(amount))
}

fun SubscriptionSummary.priceCycle(strings: Strings): String =
    priceCycleText(strings, subscription.amount, subscription.billingCycle)

@StringRes
fun SubscriptionBadge.label(): Int = when (this) {
    SubscriptionBadge.PAUSED -> R.string.subscription_paused
    SubscriptionBadge.TRIAL -> R.string.subscription_trial
    SubscriptionBadge.ACTIVE -> R.string.subscription_active
}

/** El chargeStatus del dominio: "Se cobra hoy", "Se cobra en 5 días"... */
fun SubscriptionSummary.chargeStatusText(strings: Strings): String = when {
    !subscription.isActive -> strings.get(R.string.charge_paused)
    daysUntilCharge < 0 -> strings.get(R.string.charge_pending)
    daysUntilCharge == 0 -> strings.get(R.string.charge_today)
    daysUntilCharge == 1 -> strings.get(R.string.charge_tomorrow)
    else -> strings.plural(R.plurals.charge_in_days, daysUntilCharge)
}

/** "Hoy", "Mañana", "En 5 días", "Ya pasó". */
fun UpcomingCharge.whenText(strings: Strings): String = when {
    daysUntil < 0 -> strings.get(R.string.charge_already_passed)
    daysUntil == 0 -> strings.get(R.string.today)
    daysUntil == 1 -> strings.get(R.string.tomorrow)
    else -> strings.plural(R.plurals.in_days, daysUntil)
}

/** La tarjeta a la que se cobra o la forma de pago (paymentSourceText). */
fun UpcomingCharge.paymentSource(strings: Strings): String =
    cardName.ifBlank { strings.get(paymentMethodName(paymentMethod)) }

/** "Ya registraste el cobro de este periodo el 05/oct." o "" */
fun SubscriptionSummary.lastChargeText(strings: Strings): String {
    val date = lastChargeDate
    return if (isCurrentCycleCharged && date != null) strings.get(R.string.subscription_last_charge, dayMonth(date)) else ""
}

/** Estado de la prueba gratis, o "" si no tiene. */
fun SubscriptionSummary.trialStatusText(strings: Strings): String {
    val trialEnd = subscription.trialEndDate
    return when {
        !subscription.isTrial || trialEnd == null -> ""
        !isTrialActive -> strings.get(R.string.trial_ended, dayMonth(trialEnd))
        daysUntilTrialEnds == 0 -> strings.get(R.string.trial_ends_today, formatMoney(subscription.amount))
        else -> strings.plural(R.plurals.trial_days_left, daysUntilTrialEnds, daysUntilTrialEnds, dayMonth(trialEnd))
    }
}

/** "35% de tu gasto mensual en suscripciones", o por que no cuenta. */
fun SubscriptionSummary.shareText(strings: Strings): String = when {
    !subscription.isActive -> strings.get(R.string.share_paused)
    isTrialActive -> strings.get(R.string.share_trial)
    else -> strings.get(
        R.string.share_of_monthly,
        BigDecimal(shareOfMonthlyRatio * 100).setScale(0, RoundingMode.HALF_UP).toInt(),
    )
}
