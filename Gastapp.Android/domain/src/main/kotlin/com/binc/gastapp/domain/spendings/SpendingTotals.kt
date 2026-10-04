package com.binc.gastapp.domain.spendings

import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.money.dividedBy
import com.binc.gastapp.domain.money.roundHalfEven
import com.binc.gastapp.domain.money.sumOfMoney
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.max

// Port de los totales de SpendingService.cs y de la mensualidad MSI de
// NewSpendingViewModel (MAUI).
//
// OJO, dos criterios distintos que vienen de MAUI tal cual:
//  - El total del DIA suma todo lo del dia, compras con tarjeta incluidas.
//  - El total del PERIODO y el resumen por categoria del periodo NO cuentan las
//    compras con tarjeta (cuenta el pago a la tarjeta cuando se hace).
// dayTotal se queda como MAUI porque es la referencia de paridad. La app nativa ya no
// lo usa: el usuario decidio (anexo F, 2 oct 2026) que el total del dia siga el
// criterio del periodo; Resumen suma el dia sin compras con tarjeta.

/** Mensualidad de una compra a MSI: monto entre plazo, redondeado a centavos (al par). */
fun msiMonthlyInstallment(amount: BigDecimal, selectedInstallments: Int): BigDecimal =
    (amount dividedBy max(1, selectedInstallments)).roundHalfEven(2)

/** Gastos de un dia, en orden de hora (GetSpendingListByDateAsync). */
fun spendingsOfDay(spendings: List<Spending>, day: LocalDate): List<Spending> =
    spendings.filter { !it.isDeleted && it.date.toLocalDate() == day }.sortedBy { it.date }

/** Total del dia: TODO lo del dia, incluidas compras con tarjeta y pagos a tarjeta. */
fun dayTotal(spendings: List<Spending>, day: LocalDate): BigDecimal =
    spendingsOfDay(spendings, day).sumOfMoney { it.amount }

/**
 * Total de un periodo: sin compras con tarjeta, del primer dia a las 00:00 hasta el
 * ultimo dia COMPLETO.
 *
 * Diferencia intencional con MAUI: alla el total del calendario comparaba contra las
 * 00:00 del ultimo dia y dejaba fuera lo gastado ese dia. Aqui se incluye.
 */
fun periodTotal(spendings: List<Spending>, firstDay: LocalDate, lastDay: LocalDate): BigDecimal =
    spendingsInPeriod(spendings, firstDay, lastDay).sumOfMoney { it.amount }

private fun spendingsInPeriod(spendings: List<Spending>, firstDay: LocalDate, lastDay: LocalDate): List<Spending> {
    val from = firstDay.atStartOfDay()
    val until = lastDay.plusDays(1).atStartOfDay()
    return spendings.filter { !it.isDeleted && !it.isCreditCard && it.date >= from && it.date < until }
}

data class CategoryTotal(val categoryId: String?, val name: String, val amount: BigDecimal)

/**
 * Gasto por categoria de un periodo, de mayor a menor (GetCategoryResumeByPeriod). Sin
 * compras con tarjeta. [categoryNames] traduce id a nombre; sin nombre es "Sin categoria".
 */
fun categoryTotalsByPeriod(
    spendings: List<Spending>,
    categoryNames: Map<String, String>,
    firstDay: LocalDate,
    lastDay: LocalDate,
): List<CategoryTotal> =
    spendingsInPeriod(spendings, firstDay, lastDay)
        .groupBy { it.categoryId }
        .map { (id, items) -> CategoryTotal(id, categoryNames[id] ?: "Sin categoria", items.sumOfMoney { it.amount }) }
        .sortedByDescending { it.amount }

/** Dias que tienen algun gasto (los puntos del calendario). */
fun daysWithSpendings(spendings: List<Spending>): Set<LocalDate> =
    spendings.filter { !it.isDeleted }.map { it.date.toLocalDate() }.toSet()

/** Cuantos gastos vigentes tiene una categoria (se avisa antes de borrarla). */
fun activeSpendingsInCategory(spendings: List<Spending>, categoryId: String): Int =
    spendings.count { !it.isDeleted && it.categoryId == categoryId }
