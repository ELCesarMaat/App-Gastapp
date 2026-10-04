package com.binc.gastapp.ui.spending

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.binc.gastapp.R
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.format.categoryLabel
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.paymentMethodName
import com.binc.gastapp.ui.format.timeText

/** Un gasto listo para mostrarse en una lista. */
data class SpendingItem(
    val spending: Spending,
    val categoryName: String,
    /** Tarjeta de la compra o del pago; null si no toca una tarjeta. */
    val cardName: String?,
) {
    val id: String get() = spending.spendingId

    /** Compra con tarjeta: no cuenta en los totales hasta que se paga la tarjeta (regla 16). */
    val isCardPurchase: Boolean get() = spending.isCreditCard

    /** Abono a una tarjeta: si cuenta, es dinero que salio. */
    val isCardPayment: Boolean get() = !spending.isCreditCard && spending.creditCardId != null

}

/** El titulo, o la categoria si no tiene. */
@Composable
fun SpendingItem.titleText(): String = spending.title.ifBlank { categoryLabel(categoryName) }

/** "Comida · Efectivo", "Comida · BBVA Azul · 6 MSI", "Pago a tarjeta · Nu". */
@Composable
fun SpendingItem.detailText(): String {
    val category = categoryLabel(categoryName)
    return when {
        isCardPayment -> stringResource(R.string.spending_detail_card_payment, cardName ?: stringResource(R.string.spending_detail_card_fallback))
        isCardPurchase && spending.isMsi -> stringResource(
            R.string.spending_detail_msi,
            category,
            cardName ?: stringResource(R.string.payment_short_credit),
            spending.totalInstallments,
        )
        isCardPurchase -> stringResource(R.string.spending_detail_with, category, cardName ?: stringResource(R.string.payment_short_credit))
        else -> stringResource(R.string.spending_detail_with, category, stringResource(paymentShortLabel(spending.paymentMethod)))
    }
}

fun spendingItems(spendings: List<Spending>, categories: CategoryDirectory, cards: List<CreditCard>): List<SpendingItem> {
    val cardNames = cards.associate { it.creditCardId to it.cardName }
    return spendings.map { spending ->
        SpendingItem(
            spending = spending,
            categoryName = categories.nameOf(spending.categoryId),
            cardName = spending.creditCardId?.let { cardNames[it] },
        )
    }
}

/** Etiquetas cortas de las 4 formas de pago, las mismas del formulario de gasto de MAUI. */
@StringRes
fun paymentShortLabel(paymentMethod: String): Int = when (paymentMethod) {
    PaymentMethods.DEBIT -> R.string.payment_short_debit
    PaymentMethods.TRANSFER -> R.string.payment_short_transfer
    PaymentMethods.CREDIT_CARD -> R.string.payment_short_credit
    else -> R.string.payment_short_cash
}

/** Las de la pagina de detalle de MAUI (DetailViewModel). */
@StringRes
fun paymentLongLabel(spending: Spending): Int =
    paymentMethodName(if (spending.isCreditCard) PaymentMethods.CREDIT_CARD else spending.paymentMethod)

/**
 * Fila de un gasto (FilaGasto del demo). Una compra con tarjeta se ve atenuada y lo
 * dice: no suma al total del dia ni del periodo hasta que se paga la tarjeta.
 */
@Composable
fun SpendingRow(item: SpendingItem, onClick: (() -> Unit)?, modifier: Modifier = Modifier, showTime: Boolean = true) {
    val muted = item.isCardPurchase
    val title = item.titleText()
    val detail = item.detailText()
    val description = stringResource(
        if (muted) R.string.spending_row_card_purchase_description else R.string.spending_row_description,
        title,
        formatMoney(item.spending.amount),
        detail,
    )
    ListItem(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        colors = TransparentListItemColors,
        leadingContent = {
            TonalIcon(if (item.isCardPayment) Icons.Rounded.Payments else categoryIcon(item.categoryName))
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (muted) {
                    Text(
                        stringResource(R.string.counts_when_card_paid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    formatMoney(item.spending.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (showTime) {
                    Row { Text(timeText(item.spending.date.toLocalTime()), style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
    )
}
