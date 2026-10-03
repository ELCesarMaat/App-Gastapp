package com.binc.gastapp.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// Modelos del dominio. Los nombres y valores son los mismos que usan el API y la app
// MAUI (Gastapp.Models), para que el mapeo con los DTO y con Room sea 1:1.

/** Formas de pago. Se guardan como texto, igual que en el API. */
object PaymentMethods {
    const val CASH = "Cash"
    const val DEBIT = "Debit"
    const val TRANSFER = "Transfer"
    const val CREDIT_CARD = "CreditCard"
}

/**
 * Periodicidades de una suscripcion. Texto y no enum a proposito (como en MAUI): un
 * valor que no se conozca se trata como mensual en vez de romper.
 */
object BillingCycles {
    const val WEEKLY = "Weekly"
    const val MONTHLY = "Monthly"
    const val QUARTERLY = "Quarterly"
    const val SEMIANNUAL = "Semiannual"
    const val YEARLY = "Yearly"
}

/** Tipo de ingreso del usuario (IncomeTypeId en el API). */
object IncomeTypes {
    const val WEEKLY = 1
    const val BIWEEKLY = 2
    const val MONTHLY = 3
}

/**
 * Un gasto. Tambien representa los pagos a tarjeta: un abono es un Spending con
 * [isCreditCard] = false y [creditCardId] lleno.
 *
 * [date] es hora local del telefono (al API viaja en UTC, ver time/ApiTime.kt).
 */
data class Spending(
    val spendingId: String,
    val amount: BigDecimal,
    val date: LocalDateTime,
    val userId: String = "",
    val categoryId: String = "",
    val title: String = "",
    val description: String? = null,
    val isCreditCard: Boolean = false,
    val creditCardId: String? = null,
    val paymentMethod: String = PaymentMethods.CASH,
    val isMsi: Boolean = false,
    val totalInstallments: Int = 1,
    val currentInstallment: Int = 1,
    val parentSpendingId: String? = null,
    val installmentMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    val isDeleted: Boolean = false,
)

/** Solo se guardan los dias del mes de corte y de pago (1-31), no fechas. */
data class CreditCard(
    val creditCardId: String,
    val cutOffDay: Int,
    val paymentDay: Int,
    val userId: String = "",
    val cardName: String = "",
    val bankName: String = "",
    val lastFourDigits: String? = null,
    val creditLimit: BigDecimal = BigDecimal.ZERO,
    val colorHex: String = "#126E63",
)

/**
 * Suscripcion. [firstChargeDate] y [trialEndDate] son fechas de calendario: nunca
 * pasan por conversiones de zona horaria. [lastChargeRegisteredAt] si es un instante
 * (hora local del telefono).
 */
data class Subscription(
    val subscriptionId: String,
    val serviceName: String,
    val amount: BigDecimal,
    val firstChargeDate: LocalDate,
    val billingCycle: String = BillingCycles.MONTHLY,
    val userId: String = "",
    val planName: String? = null,
    val paymentMethod: String = PaymentMethods.CASH,
    val creditCardId: String? = null,
    val categoryId: String? = null,
    val isActive: Boolean = true,
    val isTrial: Boolean = false,
    val trialEndDate: LocalDate? = null,
    val colorHex: String = "#7C3AED",
    val notes: String? = null,
    val lastChargeRegisteredAt: LocalDateTime? = null,
    val isDeleted: Boolean = false,
)

data class Category(
    val categoryId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean = false,
)

/**
 * Gasto que el dominio propone crear (pago a tarjeta, ajuste de saldo, cobro de una
 * suscripcion...). La capa de datos le pone id, usuario y categoria y lo guarda.
 */
data class PlannedSpending(
    val title: String,
    val description: String,
    val amount: BigDecimal,
    val date: LocalDateTime,
    val isCreditCard: Boolean,
    val creditCardId: String?,
    val paymentMethod: String,
    val isMsi: Boolean = false,
    val totalInstallments: Int = 1,
    val currentInstallment: Int = 1,
    val installmentMonthlyAmount: BigDecimal = BigDecimal.ZERO,
)

/** Nivel de un estado y su color. Son los colores de estado de toda la app. */
enum class StatusLevel(val colorHex: String) {
    OK("#126E63"),
    WARNING("#D97706"),
    CRITICAL("#C62828"),
    NEUTRAL("#6E6E6E"),
}
