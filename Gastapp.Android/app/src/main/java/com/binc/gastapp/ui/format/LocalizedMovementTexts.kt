package com.binc.gastapp.ui.format

import com.binc.gastapp.R
import com.binc.gastapp.domain.model.MovementTexts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los titulos y descripciones de los gastos que crea la app (pagos, ajustes, saldos
 * iniciales, cobros de suscripciones) en el idioma del telefono. Se guardan asi: son
 * texto libre, como lo que escribe el usuario.
 */
@Singleton
class LocalizedMovementTexts @Inject constructor(private val strings: Strings) : MovementTexts {
    override fun cardPaymentTitle(cardName: String) = strings.get(R.string.movement_card_payment_title, cardName)

    override fun cardPaymentDescription(bankName: String) = strings.get(R.string.movement_card_payment_description, bankName)

    override fun adjustmentChargeTitle(cardName: String) = strings.get(R.string.movement_adjustment_title, cardName)

    override fun adjustmentCreditTitle(cardName: String) = strings.get(R.string.movement_adjustment_credit_title, cardName)

    override fun cycleBalanceTitle(cardName: String) = strings.get(R.string.movement_cycle_balance_title, cardName)

    override fun cycleBalanceDescription() = strings.get(R.string.movement_cycle_balance_description)

    override fun previousBalanceTitle(cardName: String) = strings.get(R.string.movement_previous_balance_title, cardName)

    override fun previousBalanceDescription() = strings.get(R.string.movement_previous_balance_description)

    override fun cashBalanceTitle(cardName: String) = strings.get(R.string.movement_cash_balance_title, cardName)

    override fun cashBalanceDescription() = strings.get(R.string.movement_cash_balance_description)

    override fun previousMsiTitle(cardName: String) = strings.get(R.string.movement_previous_msi_title, cardName)

    override fun previousMsiDescription(paidInstallments: Int, totalInstallments: Int) =
        strings.get(R.string.movement_previous_msi_description, paidInstallments, totalInstallments)

    override fun subscriptionChargeTitle(serviceName: String) = strings.get(R.string.movement_subscription_title, serviceName)

    override fun subscriptionChargeDescription(billingCycle: String, serviceName: String, planName: String?): String {
        // "Cobro mensual de Netflix" en espanol, "Cobranca mensal de Netflix" en portugues;
        // "Monthly charge for Netflix" en ingles.
        val cycle = strings.get(billingCycleName(billingCycle)).let { if (AppLocale.language in LowercaseCycleLanguages) it.lowercase(AppLocale.locale) else it }
        return if (planName.isNullOrBlank()) strings.get(R.string.movement_subscription_description, cycle, serviceName)
        else strings.get(R.string.movement_subscription_description_plan, cycle, serviceName, planName)
    }
}

/** Idiomas donde el ciclo va en minuscula dentro de la frase ("mensal", "mensual"). */
private val LowercaseCycleLanguages = setOf("es", "pt")
