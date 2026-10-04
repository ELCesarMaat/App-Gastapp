package com.binc.gastapp.ui.spending

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.format.formatMoney
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

    val title: String get() = spending.title.ifBlank { categoryName }

    /** "Comida · Efectivo", "Comida · BBVA Azul · 6 MSI", "Pago a tarjeta · Nu". */
    val detail: String
        get() = buildString {
            when {
                isCardPayment -> append("Pago a tarjeta · ").append(cardName ?: "Tarjeta")
                isCardPurchase -> {
                    append(categoryName).append(" · ").append(cardName ?: "Crédito")
                    if (spending.isMsi) append(" · ").append(spending.totalInstallments).append(" MSI")
                }
                else -> append(categoryName).append(" · ").append(paymentShortLabel(spending.paymentMethod))
            }
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
fun paymentShortLabel(paymentMethod: String): String = when (paymentMethod) {
    PaymentMethods.DEBIT -> "Débito"
    PaymentMethods.TRANSFER -> "Transf."
    PaymentMethods.CREDIT_CARD -> "Crédito"
    else -> "Efectivo"
}

/** Las de la pagina de detalle de MAUI (DetailViewModel). */
fun paymentLongLabel(spending: Spending): String = when {
    spending.paymentMethod == PaymentMethods.DEBIT -> "Tarjeta de débito"
    spending.paymentMethod == PaymentMethods.TRANSFER -> "Transferencia bancaria"
    spending.isCreditCard || spending.paymentMethod == PaymentMethods.CREDIT_CARD -> "Tarjeta de crédito"
    else -> "Efectivo"
}

/**
 * Fila de un gasto (FilaGasto del demo). Una compra con tarjeta se ve atenuada y lo
 * dice: no suma al total del dia ni del periodo hasta que se paga la tarjeta.
 */
@Composable
fun SpendingRow(item: SpendingItem, onClick: (() -> Unit)?, modifier: Modifier = Modifier, showTime: Boolean = true) {
    val muted = item.isCardPurchase
    ListItem(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(item.title).append(", ").append(formatMoney(item.spending.amount)).append(", ").append(item.detail)
                    if (muted) append(", compra con tarjeta, cuenta al pagarla")
                }
            },
        colors = TransparentListItemColors,
        leadingContent = {
            TonalIcon(if (item.isCardPayment) Icons.Rounded.Payments else categoryIcon(item.categoryName))
        },
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(item.detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (muted) {
                    Text(
                        "Cuenta al pagar la tarjeta",
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
