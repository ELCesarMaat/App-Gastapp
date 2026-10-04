package com.binc.gastapp.domain.cards

import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.PlannedSpending
import com.binc.gastapp.domain.money.formatN2
import com.binc.gastapp.domain.money.sumOfMoney
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.math.max

// Gastos que se generan desde la pantalla de tarjetas. En MAUI vivian dentro de
// CreditCardsViewModel y de CreditCardService.AdjustCardBalanceAsync.

private val AdjustmentTolerance = BigDecimal("0.001")

/**
 * Ajustar saldo: no se edita nada, se crea una compra (si el saldo real es mayor) o un
 * abono (si es menor) por la diferencia. Null si no hay diferencia.
 */
fun balanceAdjustment(card: CreditCard, currentBalance: BigDecimal, newBalance: BigDecimal, now: LocalDateTime): PlannedSpending? {
    val diff = newBalance - currentBalance
    if (diff.abs() < AdjustmentTolerance) return null

    val description = "Ajuste de saldo de $${currentBalance.formatN2()} a $${newBalance.formatN2()}"
    return if (diff.signum() > 0) {
        PlannedSpending(
            title = "Ajuste de saldo - ${card.cardName}",
            description = description,
            amount = diff,
            date = now,
            isCreditCard = true,
            creditCardId = card.creditCardId,
            paymentMethod = PaymentMethods.CREDIT_CARD,
        )
    } else {
        PlannedSpending(
            title = "Ajuste de saldo (Abono) - ${card.cardName}",
            description = description,
            amount = diff.abs(),
            date = now,
            isCreditCard = false,
            creditCardId = card.creditCardId,
            paymentMethod = PaymentMethods.TRANSFER,
        )
    }
}

/** Monto que se propone al registrar un pago: el del corte, o toda la deuda si no hay corte. */
fun suggestedPayment(summary: CardSummary): BigDecimal =
    if (summary.currentCycleAmount.signum() > 0) summary.currentCycleAmount else summary.totalDebt

data class QuickAmount(val label: String, val amount: BigDecimal)

/** Atajos del pago: "Pagar corte" y "Pagar todo" (si son distintos). */
fun paymentQuickOptions(summary: CardSummary): List<QuickAmount> = buildList {
    if (summary.currentCycleAmount.signum() > 0) add(QuickAmount("Pagar corte", summary.currentCycleAmount))
    if (summary.totalDebt.signum() > 0 && summary.totalDebt.compareTo(summary.currentCycleAmount) != 0) {
        add(QuickAmount("Pagar todo", summary.totalDebt))
    }
}

/**
 * Un pago a la tarjeta es un gasto con isCreditCard = false: descuenta deuda. Con
 * [isStatementPayment] el usuario confirmo que es el pago del mes aunque no cubra todo
 * el corte, y la fecha limite pasa al mes siguiente (ver hasStatementPaymentAfterCutOff).
 */
fun cardPayment(card: CreditCard, amount: BigDecimal, now: LocalDateTime, isStatementPayment: Boolean = false): PlannedSpending = PlannedSpending(
    title = "Pago TDC - ${card.cardName}",
    description = if (isStatementPayment) "$StatementPaymentNote · Abono a tarjeta ${card.bankName}" else "Abono a tarjeta ${card.bankName}",
    amount = amount,
    date = now,
    isCreditCard = false,
    creditCardId = card.creditCardId,
    paymentMethod = PaymentMethods.TRANSFER,
)

/** Compra a MSI que ya venia corriendo al dar de alta una tarjeta en uso. */
data class PendingMsiPurchase(
    val title: String,
    val monthlyAmount: BigDecimal,
    /** Mensualidades que ya se pagaron. */
    val paidInstallments: Int,
    val totalInstallments: Int,
) {
    val totalAmount: BigDecimal get() = monthlyAmount.multiply(BigDecimal(totalInstallments))
    val remainingInstallments: Int get() = max(0, totalInstallments - paidInstallments)

    /** Lo que todavia se debe: es lo que suma a la deuda de la tarjeta. */
    val remainingAmount: BigDecimal get() = monthlyAmount.multiply(BigDecimal(remainingInstallments))
}

/**
 * Alta de una tarjeta que ya venia en uso: los gastos que se registran para que la
 * deuda cuadre con lo que reporto el usuario.
 *
 * El usuario captura el TOTAL usado; las compras a MSI ya son parte de ese total, asi
 * que lo de contado es la diferencia. El saldo del corte actual se fecha EL DIA del
 * ultimo corte: entra en el estado de cuenta pendiente y tambien en el ciclo actual.
 */
fun inUseCardMovements(
    card: CreditCard,
    totalUsed: BigDecimal,
    currentCycleDebt: BigDecimal,
    balanceAlreadyCut: Boolean,
    msiPurchases: List<PendingMsiPurchase>,
    now: LocalDateTime,
): List<PlannedSpending> = buildList {
    val msiOutstanding = msiPurchases.sumOfMoney { it.remainingAmount }
    val cashDebt = (totalUsed - msiOutstanding).max(BigDecimal.ZERO)
    val lastCutOff = lastCutOffDate(card.cutOffDay, now.toLocalDate()).atStartOfDay()

    fun cash(title: String, description: String, amount: BigDecimal, date: LocalDateTime) = PlannedSpending(
        title = title,
        description = description,
        amount = amount,
        date = date,
        isCreditCard = true,
        creditCardId = card.creditCardId,
        paymentMethod = PaymentMethods.CREDIT_CARD,
    )

    if (cashDebt.signum() > 0) {
        val cycleAmount = currentCycleDebt.min(cashDebt)
        if (cycleAmount.signum() > 0 && cycleAmount < cashDebt) {
            add(
                cash(
                    "Saldo corte actual - ${card.cardName}",
                    "Saldo a pagar en corte actual registrado al crear la tarjeta",
                    cycleAmount,
                    lastCutOff,
                )
            )
            add(
                cash(
                    "Saldo acumulado previo - ${card.cardName}",
                    "Saldo acumulado anterior registrado al crear la tarjeta",
                    cashDebt - cycleAmount,
                    now.minusMonths(2),
                )
            )
        } else {
            add(
                cash(
                    "Saldo de contado - ${card.cardName}",
                    "Compras de contado pendientes al registrar la tarjeta en uso",
                    cashDebt,
                    if (balanceAlreadyCut) lastCutOff else now,
                )
            )
        }
    }

    // Se registra lo que FALTA por pagar, no el precio original: es lo que sigue
    // ocupando la linea de credito.
    msiPurchases.forEach { msi ->
        add(
            PlannedSpending(
                title = msi.title.ifBlank { "Compra MSI previa - ${card.cardName}" },
                description = "Compra a MSI en curso (${msi.paidInstallments} de ${msi.totalInstallments} pagadas)",
                amount = msi.remainingAmount,
                date = now,
                isCreditCard = true,
                creditCardId = card.creditCardId,
                paymentMethod = PaymentMethods.CREDIT_CARD,
                isMsi = true,
                totalInstallments = msi.totalInstallments,
                currentInstallment = msi.paidInstallments,
                installmentMonthlyAmount = msi.monthlyAmount,
            )
        )
    }
}
