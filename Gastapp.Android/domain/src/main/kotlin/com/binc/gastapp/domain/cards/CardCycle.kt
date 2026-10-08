package com.binc.gastapp.domain.cards

import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.money.Cent
import com.binc.gastapp.domain.money.sumOfMoney
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

// Port de CreditCardService.cs (MAUI). Paridad verificada contra
// ciclos_tarjeta.json y escenarios_tarjeta.json.

data class CycleDates(val cutOffDate: LocalDate, val paymentDueDate: LocalDate)

/**
 * La proxima vez que ocurre el dia [day] (1-31) contando hoy. Si el mes no tiene ese
 * dia, cae en el ultimo (un 31 en septiembre es el 30).
 */
fun nextOccurrenceOfDay(today: LocalDate, day: Int): LocalDate {
    var candidate = today.withDayOfMonth(min(day, today.lengthOfMonth()))
    if (candidate < today) {
        val nextMonth = today.plusMonths(1)
        candidate = nextMonth.withDayOfMonth(min(day, nextMonth.lengthOfMonth()))
    }
    return candidate
}

/** La ultima vez que ocurrio el dia [day] contando el propio [reference]. */
fun previousOccurrenceOfDay(reference: LocalDate, day: Int): LocalDate {
    var candidate = reference.withDayOfMonth(min(day, reference.lengthOfMonth()))
    if (candidate > reference) {
        val previousMonth = reference.minusMonths(1)
        candidate = previousMonth.withDayOfMonth(min(day, previousMonth.lengthOfMonth()))
    }
    return candidate
}

/**
 * Corte y pago se calculan cada uno por separado, como "la proxima vez que ocurre ese
 * dia". Calcular el pago a partir del proximo corte salta un ciclo entero cuando ya
 * paso el corte pero todavia no llega el dia de pago.
 */
fun calculateCycleDates(cutOffDay: Int, paymentDay: Int, referenceDate: LocalDate): CycleDates =
    CycleDates(
        cutOffDate = nextOccurrenceOfDay(referenceDate, cutOffDay),
        paymentDueDate = nextOccurrenceOfDay(referenceDate, paymentDay),
    )

/**
 * Igual que la anterior, pero la fecha limite salta al ciclo siguiente cuando el
 * estado de cuenta que liquida ya quedo cubierto con los abonos registrados, o cuando
 * el usuario confirmo que un pago despues de ese corte era el pago del mes.
 * Es la que debe usar la UI (CalculateCycleDatesAsync en MAUI).
 */
fun calculateCycleDates(card: CreditCard, spendings: List<Spending>, referenceDate: LocalDate): CycleDates {
    val dates = calculateCycleDates(card.cutOffDay, card.paymentDay, referenceDate)

    // La fecha limite liquida el ultimo corte que ocurrio ANTES de ella.
    val statementCutOff = statementCutOffFor(dates.paymentDueDate, card.cutOffDay)

    // Si ese corte todavia no llega, el estado de cuenta ni se ha generado.
    if (statementCutOff > referenceDate) return dates

    if (isStatementSettled(card.creditCardId, spendings, statementCutOff) ||
        hasStatementPaymentAfterCutOff(card.creditCardId, spendings, statementCutOff, referenceDate)
    ) {
        return dates.copy(paymentDueDate = nextOccurrenceOfDay(dates.paymentDueDate.plusDays(1), card.paymentDay))
    }
    return dates
}

/** El corte cuyo estado de cuenta liquida la fecha limite [paymentDueDate]. */
fun statementCutOffFor(paymentDueDate: LocalDate, cutOffDay: Int): LocalDate =
    previousOccurrenceOfDay(paymentDueDate.minusDays(1), cutOffDay)

/**
 * Inicio de la descripcion de un abono que el usuario confirmo como el pago del mes
 * aunque no cubriera todo el corte. Va en la descripcion para que viaje al API y se
 * vaya con el abono si este se borra.
 */
const val StatementPaymentNote = "Pago del mes"

fun Spending.isStatementPayment(): Boolean =
    !isCreditCard && description?.startsWith(StatementPaymentNote) == true

/**
 * Hubo un abono confirmado como el pago del mes despues del corte del estado de cuenta
 * (desde el dia siguiente y hasta hoy): ese corte se da por pagado aunque el monto no
 * cuadre con lo registrado (las MSI cuentan completas en lo facturado, faltan compras,
 * pago minimo). No esta en MAUI: alla solo contaba el acumulado.
 */
fun hasStatementPaymentAfterCutOff(
    creditCardId: String,
    spendings: List<Spending>,
    statementCutOff: LocalDate,
    referenceDate: LocalDate,
): Boolean {
    val from = statementCutOff.plusDays(1).atStartOfDay()
    val until = referenceDate.plusDays(1).atStartOfDay()
    return spendings.any {
        it.creditCardId == creditCardId && it.isStatementPayment() && !it.isDeleted &&
            it.date >= from && it.date < until
    }
}

/**
 * Deuda de la tarjeta: compras ([Spending.isCreditCard] = true) menos abonos (false),
 * nunca negativa.
 */
fun pendingAmount(creditCardId: String, spendings: List<Spending>): BigDecimal {
    val purchases = spendings
        .filter { it.creditCardId == creditCardId && it.isCreditCard && !it.isDeleted }
        .sumOfMoney { it.amount }
    val payments = spendings
        .filter { it.creditCardId == creditCardId && !it.isCreditCard && !it.isDeleted }
        .sumOfMoney { it.amount }
    return (purchases - payments).max(BigDecimal.ZERO)
}

/**
 * Un corte esta cubierto cuando lo que falta por pagar de el (ver [statementPendingAmount])
 * es de un centavo o menos.
 */
fun isStatementSettled(creditCardId: String, spendings: List<Spending>, statementCutOff: LocalDate): Boolean =
    statementPendingAmount(creditCardId, spendings, statementCutOff) <= Cent

/**
 * Lo que falta por pagar del estado de cuenta de [statementCutOff]: lo facturado hasta
 * ese dia (incluido) menos todo lo abonado. Es un acumulado: los abonos previos ya
 * descontaron las compras previas.
 *
 * "Nada facturado antes del corte" NO significa "ya esta pagado": en ese caso falta
 * toda la deuda.
 */
fun statementPendingAmount(creditCardId: String, spendings: List<Spending>, statementCutOff: LocalDate): BigDecimal {
    val cutOffLimit = statementCutOff.plusDays(1).atStartOfDay()

    val billed = spendings
        .filter { it.creditCardId == creditCardId && it.isCreditCard && !it.isDeleted && it.date < cutOffLimit }
        .sumOfMoney { it.amount }
    val paid = spendings
        .filter { it.creditCardId == creditCardId && !it.isCreditCard && !it.isDeleted }
        .sumOfMoney { it.amount }

    if (billed.signum() <= 0) return pendingAmount(creditCardId, spendings)
    return (billed - paid).max(BigDecimal.ZERO)
}

/**
 * El ultimo corte que ya ocurrio. Fecha el saldo con el que se da de alta una tarjeta
 * en uso: fechado hoy quedaria despues del corte y no contaria como facturado.
 */
fun lastCutOffDate(cutOffDay: Int, referenceDate: LocalDate): LocalDate =
    previousOccurrenceOfDay(referenceDate, cutOffDay)

/**
 * Cortes que pasaron despues del primer estado de cuenta de una compra (el primer corte
 * en o despues de [purchaseDate]; una compra del dia del corte entra en ese) y hasta
 * [today], incluido. Es lo que avanza la mensualidad de una compra a MSI.
 */
fun cutOffsSinceFirstStatement(purchaseDate: LocalDate, cutOffDay: Int, today: LocalDate): Int {
    val firstCutOff = nextOccurrenceOfDay(purchaseDate, cutOffDay)
    val lastCutOff = previousOccurrenceOfDay(today, cutOffDay)
    if (lastCutOff <= firstCutOff) return 0
    // Los dos caen en el dia de corte (recortado en meses cortos): basta contar meses.
    return ChronoUnit.MONTHS.between(YearMonth.from(firstCutOff), YearMonth.from(lastCutOff)).toInt()
}

/**
 * La mensualidad en la que va una compra a MSI en [today]. [Spending.currentInstallment]
 * guarda la del primer estado de cuenta que la incluye (1 en una compra nueva) y sube
 * una con cada corte posterior. No es cosa de MAUI: alla se quedaba fija.
 *
 * Puede pasar del plazo: entonces ya se termino de pagar.
 */
fun Spending.installmentOn(cutOffDay: Int, today: LocalDate): Int =
    currentInstallment + cutOffsSinceFirstStatement(date.toLocalDate(), cutOffDay, today)

/**
 * Lo que se guarda en [Spending.currentInstallment] para que una compra del dia
 * [purchaseDate] vaya en la mensualidad [installment] en [today]. Puede quedar en 0 o
 * menos (una compra vieja que el usuario dice que apenas empieza a pagarse).
 */
fun installmentAnchorFor(installment: Int, purchaseDate: LocalDate, cutOffDay: Int, today: LocalDate): Int =
    installment - cutOffsSinceFirstStatement(purchaseDate, cutOffDay, today)

/**
 * Compras a MSI que se siguen pagando, de la mas reciente a la mas vieja, con
 * [Spending.currentInstallment] ya avanzada a [today] (ver [installmentOn]). Las que ya
 * pasaron de su ultima mensualidad salen de la lista.
 */
fun activeMsiSpendings(card: CreditCard, spendings: List<Spending>, today: LocalDate): List<Spending> =
    spendings
        .filter { it.creditCardId == card.creditCardId && it.isCreditCard && it.isMsi && !it.isDeleted }
        .map { it.copy(currentInstallment = it.installmentOn(card.cutOffDay, today)) }
        .filter { it.currentInstallment <= it.totalInstallments }
        .sortedByDescending { it.date }

/**
 * Compras del ciclo actual: el mes que termina en el proximo corte. Los limites son
 * los de MAUI tal cual: desde las 00:00 del corte anterior hasta las 00:00 del dia
 * siguiente al proximo corte, ambos incluidos.
 */
fun currentCycleSpendings(card: CreditCard, spendings: List<Spending>, today: LocalDate): List<Spending> {
    val nextCutOff = calculateCycleDates(card.cutOffDay, card.paymentDay, today).cutOffDate
    val cycleStart = nextCutOff.minusMonths(1).atStartOfDay()
    val cycleEnd = nextCutOff.plusDays(1).atStartOfDay()
    return spendings
        .filter {
            it.creditCardId == card.creditCardId && it.isCreditCard && !it.isDeleted &&
                it.date >= cycleStart && it.date <= cycleEnd
        }
        .sortedByDescending { it.date }
}
