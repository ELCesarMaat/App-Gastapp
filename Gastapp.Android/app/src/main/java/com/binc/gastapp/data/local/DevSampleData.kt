package com.binc.gastapp.data.local

import androidx.room.withTransaction
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.domain.model.PaymentMethods
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Datos de muestra para ver las pantallas con Room mientras no hay login (Fase 2). Solo
 * se ofrece en debug. Se marcan como sincronizados para que la Fase 3 nunca los suba;
 * iniciar sesion reemplaza toda la base de todos modos.
 */
@Singleton
class DevSampleData @Inject constructor(
    private val db: GastappDatabase,
    private val clock: Clock,
) {
    suspend fun load() {
        clear()
        db.withTransaction { insertSample() }
    }

    /** clearAllTables es bloqueante y abre su propia transaccion: va aparte y fuera del hilo principal. */
    suspend fun clear() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private suspend fun insertSample() {
        val today = LocalDate.now(clock)
        val userId = "dev-user"

        db.incomeTypeDao().upsertAll(
            listOf(
                IncomeTypeEntity(IncomeTypes.WEEKLY, "Semanal"),
                IncomeTypeEntity(IncomeTypes.BIWEEKLY, "Quincenal"),
                IncomeTypeEntity(IncomeTypes.MONTHLY, "Mensual"),
            ),
        )
        db.userDao().upsert(
            UserEntity(
                userId = userId,
                name = "Usuario de prueba",
                email = "prueba@gastapp.dev",
                salaryCents = 1_250_000,
                percentSave = BigDecimal("15"),
                birthDate = LocalDate.of(1995, 6, 15),
                incomeTypeId = IncomeTypes.BIWEEKLY,
                firstPayDay = 1,
                secondPayDay = 16,
                weekPayDay = null,
                isSynced = true,
            ),
        )

        val categories = listOf("Sin categoria", "Comida", "Transporte", "Casa", "Ocio")
            .mapIndexed { i, name ->
                CategoryEntity("dev-cat-$i", userId, name, isDefaultCategory = i == 0, isSynced = true)
            }
        db.categoryDao().upsertAll(categories)

        val cards = listOf(
            CreditCardEntity("dev-card-1", userId, "Oro", "Banamex", "4321", cutOffDay = 5, paymentDay = 25, creditLimitCents = 3_000_000, colorHex = "#126E63", isSynced = true),
            CreditCardEntity("dev-card-2", userId, "Azul", "BBVA", "8765", cutOffDay = 20, paymentDay = 10, creditLimitCents = 1_500_000, colorHex = "#1D4ED8", isSynced = true),
        )
        db.creditCardDao().upsertAll(cards)

        var n = 0
        fun spending(
            daysAgo: Long,
            hour: Int,
            title: String,
            cents: Long,
            category: Int,
            card: String? = null,
            isCardPurchase: Boolean = card != null,
            method: String = if (card != null && isCardPurchase) PaymentMethods.CREDIT_CARD else PaymentMethods.CASH,
        ) = SpendingEntity(
            spendingId = "dev-spending-${n++}",
            userId = userId,
            categoryId = categories[category].categoryId,
            title = title,
            description = null,
            amountCents = cents,
            date = LocalDateTime.of(today.minusDays(daysAgo), LocalTime.of(hour, 15)),
            isCreditCard = isCardPurchase,
            creditCardId = card,
            paymentMethod = method,
            isSynced = true,
        )

        db.spendingDao().upsertAll(
            listOf(
                spending(0, 9, "Cafe", 6_550, 1),
                spending(0, 14, "Comida corrida", 14_000, 1),
                spending(0, 19, "Cine", 23_000, 4, card = "dev-card-1"),
                spending(1, 8, "Metro", 1_000, 2),
                spending(1, 20, "Super", 84_390, 3, card = "dev-card-2"),
                spending(2, 13, "Tacos", 12_000, 1),
                spending(3, 18, "Gasolina", 70_000, 2, card = "dev-card-1"),
                spending(4, 10, "Pago tarjeta Oro", 50_000, 0, card = "dev-card-1", isCardPurchase = false, method = PaymentMethods.TRANSFER),
                spending(6, 21, "Streaming", 21_900, 4),
            ),
        )

        db.subscriptionDao().upsertAll(
            listOf(
                SubscriptionEntity("dev-sub-1", userId, "Netflix", "Estandar", 21_900, BillingCycles.MONTHLY, today.minusMonths(3).withDayOfMonth(12), PaymentMethods.CREDIT_CARD, "dev-card-1", "dev-cat-4", colorHex = "#C62828", isSynced = true),
                SubscriptionEntity("dev-sub-2", userId, "Gimnasio", null, 59_900, BillingCycles.MONTHLY, today.minusMonths(6).withDayOfMonth(1), PaymentMethods.DEBIT, categoryId = "dev-cat-3", colorHex = "#126E63", isSynced = true),
                SubscriptionEntity("dev-sub-3", userId, "Nube", "200 GB", 49_900, BillingCycles.YEARLY, today.minusMonths(2), PaymentMethods.CREDIT_CARD, "dev-card-2", isTrial = true, trialEndDate = today.plusDays(10), isSynced = true),
            ),
        )
    }
}
