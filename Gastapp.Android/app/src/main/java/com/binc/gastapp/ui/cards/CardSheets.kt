package com.binc.gastapp.ui.cards

import androidx.compose.runtime.Composable
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.domain.cards.paymentQuickOptions
import com.binc.gastapp.domain.cards.suggestedPayment
import com.binc.gastapp.ui.components.AmountContextRow
import com.binc.gastapp.ui.components.AmountOption
import com.binc.gastapp.ui.components.AmountSheet
import com.binc.gastapp.ui.format.dayMonth
import com.binc.gastapp.ui.format.formatMoney
import java.math.BigDecimal

/** "Tarjeta · Banco" */
fun cardSubtitle(summary: CardSummary): String =
    listOf(summary.card.cardName, summary.card.bankName).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Registrar pago (PayCard de MAUI): propone el corte actual (o toda la deuda si no hay
 * corte) y ofrece "Pagar corte" y "Pagar todo".
 */
@Composable
fun CardPaymentSheet(summary: CardSummary, onConfirm: (BigDecimal) -> Unit, onDismiss: () -> Unit) {
    AmountSheet(
        title = "Registrar pago",
        subtitle = cardSubtitle(summary),
        fieldLabel = "Monto a pagar",
        initialAmount = suggestedPayment(summary).takeIf { it.signum() > 0 },
        confirmLabel = "Registrar pago",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        contextRows = if (summary.totalDebt.signum() > 0) {
            listOfNotNull(
                AmountContextRow("Saldo total pendiente", formatMoney(summary.totalDebt), highlighted = true),
                // Lo que se compara al registrar: si el pago no lo cubre, se pregunta si es el pago del mes.
                summary.statementPendingAmount.takeIf { it.signum() > 0 }?.let {
                    AmountContextRow("Pendiente del corte (vence ${dayMonth(summary.nextPaymentDueDate)})", formatMoney(it))
                },
                AmountContextRow("Corte actual", formatMoney(summary.currentCycleAmount)),
            )
        } else {
            emptyList()
        },
        options = paymentQuickOptions(summary).map { AmountOption(it.label, it.amount) },
    )
}

/** Ajustar saldo (AdjustCardBalance de MAUI): el saldo real segun el banco; acepta 0. */
@Composable
fun CardAdjustSheet(summary: CardSummary, onConfirm: (BigDecimal) -> Unit, onDismiss: () -> Unit) {
    AmountSheet(
        title = "Ajustar saldo",
        subtitle = cardSubtitle(summary),
        fieldLabel = "Saldo real según tu banco",
        initialAmount = summary.totalDebt,
        confirmLabel = "Ajustar saldo",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        contextRows = listOf(
            AmountContextRow("Saldo registrado en Gastapp", formatMoney(summary.totalDebt), highlighted = true),
            AmountContextRow("Límite de la tarjeta", formatMoney(summary.creditLimit)),
        ),
        options = listOf(AmountOption("Sin deuda", BigDecimal.ZERO)),
        allowZero = true,
    )
}
