package com.binc.gastapp.domain.savings

import com.binc.gastapp.domain.money.dividedBy
import com.binc.gastapp.domain.money.percentOf
import com.binc.gastapp.domain.money.roundHalfEven
import java.math.BigDecimal

// Port de los calculos de SavesViewModel (presupuesto y salud del periodo) y de
// ProfileViewModel (meta de ahorro) de MAUI. Paridad: ahorro.json y msi_perfil.json.

private val Hundred = BigDecimal(100)

/** Salud del periodo segun que tanto del presupuesto ya se gasto. */
enum class BudgetHealth(val text: String, val colorHex: String, val message: String) {
    HEALTHY(
        "Saludable",
        "#E7F7F0",
        "Tus gastos siguen bajo control y todavia tienes margen para el resto del periodo.",
    ),
    STABLE(
        "Estable",
        "#B68A12",
        "Vas bien, pero ya consumiste buena parte del presupuesto disponible.",
    ),
    TIGHT(
        "Ajustada",
        "#D97706",
        "Estas muy cerca del limite. Cualquier gasto extra puede desbalancear tu periodo.",
    ),
    CRITICAL(
        "Critica",
        "#C62828",
        "Ya rebasaste tu limite ideal. Conviene pausar gastos no esenciales.",
    ),
}

data class BudgetStatus(
    /** Lo que se puede gastar: el sueldo menos la meta de ahorro. */
    val maxTotalSpending: BigDecimal,
    /** Porcentaje gastado del presupuesto, con 2 decimales. Puede pasar de 100. */
    val percent: BigDecimal,
    /** El mismo porcentaje topado en 100, para barras y anillos. */
    val progressPercent: BigDecimal,
    val health: BudgetHealth,
    /** Lo que queda del presupuesto; negativo si ya se excedio. */
    val remainingBudget: BigDecimal,
    val dailyAverage: BigDecimal,
    /** Lo que se ahorro del sueldo, o lo que se paso si [exceededSalary]. */
    val savedOrExceeded: BigDecimal,
    val exceededSalary: Boolean,
)

fun maxTotalSpending(salary: BigDecimal, percentSave: BigDecimal): BigDecimal =
    salary.multiply(Hundred - percentSave).divide(Hundred)

fun budgetHealth(percent: BigDecimal): BudgetHealth = when {
    percent >= Hundred -> BudgetHealth.CRITICAL
    percent >= BigDecimal(90) -> BudgetHealth.TIGHT
    percent >= BigDecimal(80) -> BudgetHealth.STABLE
    else -> BudgetHealth.HEALTHY
}

/**
 * Estado del presupuesto del periodo. [periodDayCount] son los dias del periodo
 * (si es 0 se toma 1, como MAUI).
 */
fun budgetStatus(salary: BigDecimal, percentSave: BigDecimal, totalSpending: BigDecimal, periodDayCount: Int): BudgetStatus {
    val max = maxTotalSpending(salary, percentSave)
    val percent = if (max.signum() > 0) totalSpending.percentOf(max).roundHalfEven(2) else BigDecimal.ZERO
    val dayCount = if (periodDayCount > 0) periodDayCount else 1
    val balance = salary - totalSpending

    return BudgetStatus(
        maxTotalSpending = max,
        percent = percent,
        progressPercent = percent.min(Hundred),
        health = budgetHealth(percent),
        remainingBudget = max - totalSpending,
        dailyAverage = (totalSpending dividedBy dayCount).roundHalfEven(2),
        savedOrExceeded = balance.abs(),
        exceededSalary = balance.signum() < 0,
    )
}

/** Peso de cada categoria en el total, en porcentaje con 1 decimal (al par). */
fun categoryPercentages(amounts: List<BigDecimal>): List<BigDecimal> {
    val total = amounts.fold(BigDecimal.ZERO, BigDecimal::add)
    return amounts.map { if (total.signum() > 0) it.percentOf(total).roundHalfEven(1) else BigDecimal.ZERO }
}

/** Meta de ahorro: porcentaje del sueldo a partir de un monto fijo (4 decimales). */
fun savingsPercentFromAmount(amount: BigDecimal, salary: BigDecimal): BigDecimal =
    if (salary.signum() > 0) amount.percentOf(salary).roundHalfEven(4) else BigDecimal.ZERO

/** Meta de ahorro: monto a partir de un porcentaje del sueldo. */
fun savingsAmountFromPercent(salary: BigDecimal, percent: BigDecimal): BigDecimal =
    salary.multiply(percent.divide(Hundred))
