package com.binc.gastapp.domain.model

import com.binc.gastapp.domain.subscriptions.billingCycleDisplayName

/**
 * Titulos y descripciones de los gastos que arma la app (pagos y ajustes de tarjeta,
 * saldos al dar de alta una tarjeta en uso, cobros de suscripciones). Por defecto son
 * los de MAUI en espanol; la app pasa los del idioma del telefono.
 *
 * Las marcas que se buscan despues en la descripcion (BalanceAdjustmentNote,
 * StatementPaymentNote) no estan aqui: van siempre en espanol.
 */
interface MovementTexts {
    fun cardPaymentTitle(cardName: String): String = "Pago TDC - $cardName"

    fun cardPaymentDescription(bankName: String): String = "Abono a tarjeta $bankName"

    fun adjustmentChargeTitle(cardName: String): String = "Ajuste de saldo - $cardName"

    fun adjustmentCreditTitle(cardName: String): String = "Ajuste de saldo (Abono) - $cardName"

    fun cycleBalanceTitle(cardName: String): String = "Saldo corte actual - $cardName"

    fun cycleBalanceDescription(): String = "Saldo a pagar en corte actual registrado al crear la tarjeta"

    fun previousBalanceTitle(cardName: String): String = "Saldo acumulado previo - $cardName"

    fun previousBalanceDescription(): String = "Saldo acumulado anterior registrado al crear la tarjeta"

    fun cashBalanceTitle(cardName: String): String = "Saldo de contado - $cardName"

    fun cashBalanceDescription(): String = "Compras de contado pendientes al registrar la tarjeta en uso"

    fun previousMsiTitle(cardName: String): String = "Compra MSI previa - $cardName"

    fun previousMsiDescription(paidInstallments: Int, totalInstallments: Int): String =
        "Compra a MSI en curso ($paidInstallments de $totalInstallments pagadas)"

    fun subscriptionChargeTitle(serviceName: String): String = "Suscripción - $serviceName"

    fun subscriptionChargeDescription(billingCycle: String, serviceName: String, planName: String?): String {
        val cycle = billingCycleDisplayName(billingCycle).lowercase()
        return if (planName.isNullOrBlank()) "Cobro $cycle de $serviceName" else "Cobro $cycle de $serviceName ($planName)"
    }
}

/** Los textos de MAUI. */
object SpanishMovementTexts : MovementTexts
