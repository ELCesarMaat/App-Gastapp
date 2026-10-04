package com.binc.gastapp.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binc.gastapp.R
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
        title = stringResource(R.string.register_payment),
        subtitle = cardSubtitle(summary),
        fieldLabel = stringResource(R.string.amount_to_pay),
        initialAmount = suggestedPayment(summary).takeIf { it.signum() > 0 },
        confirmLabel = stringResource(R.string.register_payment),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        contextRows = if (summary.totalDebt.signum() > 0) {
            listOfNotNull(
                AmountContextRow(stringResource(R.string.total_pending_balance), formatMoney(summary.totalDebt), highlighted = true),
                // Lo que se compara al registrar: si el pago no lo cubre, se pregunta si es el pago del mes.
                summary.statementPendingAmount.takeIf { it.signum() > 0 }?.let {
                    AmountContextRow(stringResource(R.string.statement_pending, dayMonth(summary.nextPaymentDueDate)), formatMoney(it))
                },
                AmountContextRow(stringResource(R.string.current_statement), formatMoney(summary.currentCycleAmount)),
            )
        } else {
            emptyList()
        },
        options = paymentQuickOptions(summary).map {
            AmountOption(stringResource(if (it.isCycle) R.string.pay_statement else R.string.pay_all), it.amount)
        },
    )
}

/** Ajustar saldo (AdjustCardBalance de MAUI): el saldo real segun el banco; acepta 0. */
@Composable
fun CardAdjustSheet(summary: CardSummary, onConfirm: (BigDecimal) -> Unit, onDismiss: () -> Unit) {
    AmountSheet(
        title = stringResource(R.string.adjust_balance),
        subtitle = cardSubtitle(summary),
        fieldLabel = stringResource(R.string.real_balance),
        initialAmount = summary.totalDebt,
        confirmLabel = stringResource(R.string.adjust_balance),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        contextRows = listOf(
            AmountContextRow(stringResource(R.string.balance_in_app), formatMoney(summary.totalDebt), highlighted = true),
            AmountContextRow(stringResource(R.string.card_limit), formatMoney(summary.creditLimit)),
        ),
        options = listOf(AmountOption(stringResource(R.string.no_debt), BigDecimal.ZERO)),
        allowZero = true,
    )
}
