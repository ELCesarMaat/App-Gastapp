package com.binc.gastapp.domain.cards

import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.domain.money.dividedBy
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.domain.time.daysBetween
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.max

// Port de CreditCardService.GetCardSummaryAsync (MAUI). Los textos que llevan una
// fecha se arman con un formateador que pone la UI (dd/MMM en MAUI).

data class CardSummary(
    val card: CreditCard,
    val creditLimit: BigDecimal,
    val totalDebt: BigDecimal,
    val availableCredit: BigDecimal,
    /** Porcentaje usado del limite, de 0 a 100. */
    val usagePercentage: Double,
    val currentCycleAmount: BigDecimal,
    val totalMsiRemainingDebt: BigDecimal,
    val activeMsiCount: Int,
    val nextCutOffDate: LocalDate,
    val nextPaymentDueDate: LocalDate,
    val daysUntilCutOff: Int,
    val daysUntilPayment: Int,
    val paymentLevel: StatusLevel,
    val usageLevel: StatusLevel,
    val currentCycleSpendings: List<Spending>,
    val activeMsiSpendings: List<Spending>,
) {
    /** Para barras de progreso: deuda / limite entre 0 y 1. */
    val usageProgressRatio: Double
        get() = if (creditLimit.signum() > 0) {
            (totalDebt dividedBy creditLimit).toDouble().coerceIn(0.0, 1.0)
        } else {
            0.0
        }

    fun paymentStatusText(formatDate: (LocalDate) -> String): String =
        paymentStatusText(daysUntilPayment, nextPaymentDueDate, formatDate)

    fun cutOffStatusText(formatDate: (LocalDate) -> String): String =
        cutOffStatusText(daysUntilCutOff, nextCutOffDate, formatDate)
}

fun buildCardSummary(card: CreditCard, spendings: List<Spending>, today: LocalDate): CardSummary {
    val totalDebt = pendingAmount(card.creditCardId, spendings)
    val creditLimit = card.creditLimit
    val availableCredit = if (creditLimit.signum() > 0) (creditLimit - totalDebt).max(BigDecimal.ZERO) else BigDecimal.ZERO
    val usagePercentage = if (creditLimit.signum() > 0) (totalDebt dividedBy creditLimit).toDouble() * 100 else 0.0

    val (nextCutOff, nextPayment) = calculateCycleDates(card, spendings, today)
    val daysUntilCutOff = daysBetween(today, nextCutOff)
    val daysUntilPayment = daysBetween(today, nextPayment)

    val cycle = currentCycleSpendings(card, spendings, today)
    val activeMsi = activeMsiSpendings(card.creditCardId, spendings)

    return CardSummary(
        card = card,
        creditLimit = creditLimit,
        totalDebt = totalDebt,
        availableCredit = availableCredit,
        usagePercentage = minOf(usagePercentage, 100.0),
        currentCycleAmount = cycle.sumOfMoney { it.amount },
        totalMsiRemainingDebt = futureMsiDebt(activeMsi),
        activeMsiCount = activeMsi.size,
        nextCutOffDate = nextCutOff,
        nextPaymentDueDate = nextPayment,
        daysUntilCutOff = daysUntilCutOff,
        daysUntilPayment = daysUntilPayment,
        paymentLevel = paymentLevel(daysUntilPayment),
        usageLevel = usageLevel(usagePercentage),
        currentCycleSpendings = cycle,
        activeMsiSpendings = activeMsi,
    )
}

/**
 * Lo que falta por pagar de las compras a MSI: mensualidades restantes por la
 * mensualidad (o el monto entre el plazo, si la mensualidad no se guardo).
 */
fun futureMsiDebt(activeMsi: List<Spending>): BigDecimal = activeMsi.sumOfMoney { msi ->
    val remainingMonths = max(0, msi.totalInstallments - msi.currentInstallment)
    val monthly = if (msi.installmentMonthlyAmount.signum() > 0) {
        msi.installmentMonthlyAmount
    } else {
        msi.amount dividedBy max(1, msi.totalInstallments)
    }
    monthly.multiply(BigDecimal(remainingMonths))
}

/** Vencido o vence hoy en rojo, en 3 dias o menos en ambar, despues en verde. */
fun paymentLevel(daysUntilPayment: Int): StatusLevel = when {
    daysUntilPayment <= 0 -> StatusLevel.CRITICAL
    daysUntilPayment <= 3 -> StatusLevel.WARNING
    else -> StatusLevel.OK
}

/** 80 % o mas en rojo, 50 % o mas en ambar. */
fun usageLevel(usagePercentage: Double): StatusLevel = when {
    usagePercentage >= 80 -> StatusLevel.CRITICAL
    usagePercentage >= 50 -> StatusLevel.WARNING
    else -> StatusLevel.OK
}

fun paymentStatusText(daysUntilPayment: Int, dueDate: LocalDate, formatDate: (LocalDate) -> String): String = when {
    daysUntilPayment < 0 -> "Vencido"
    daysUntilPayment == 0 -> "Vence hoy"
    daysUntilPayment <= 3 -> "Vence en $daysUntilPayment día${if (daysUntilPayment == 1) "" else "s"}"
    else -> "Vence en $daysUntilPayment días (${formatDate(dueDate)})"
}

fun cutOffStatusText(daysUntilCutOff: Int, cutOffDate: LocalDate, formatDate: (LocalDate) -> String): String =
    if (daysUntilCutOff == 0) {
        "Corta hoy"
    } else {
        "Corte en $daysUntilCutOff día${if (daysUntilCutOff == 1) "" else "s"} (${formatDate(cutOffDate)})"
    }

/**
 * Estado de una tarjeta en "Tarjetas por pagar" de Ahorros (SavesViewModel). Ojo: ahi
 * el umbral del ambar es 5 dias, no 3 como en Mis tarjetas.
 */
fun savesPendingLevel(daysUntilPayment: Int): StatusLevel = when {
    daysUntilPayment < 0 -> StatusLevel.CRITICAL
    daysUntilPayment <= 5 -> StatusLevel.WARNING
    else -> StatusLevel.OK
}

fun savesPendingText(daysUntilPayment: Int): String = when {
    daysUntilPayment < 0 -> "Vencido"
    daysUntilPayment <= 5 -> "Vence en $daysUntilPayment ${if (daysUntilPayment == 1) "día" else "días"}"
    else -> "Vence en $daysUntilPayment días"
}
